package stirling.software.SPDF.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SrbIpsQrOverlayService {

    private final SrbService srbService;

    /**
     * Nacrta IPS-QR na izabranoj strani PDF-a. Pomoću "corner" možeš birati: br, bl, tr, tl
     * (bottom/top, left/right). Ako corner == null, koristi eksplicitne margine kao bottom-right (x
     * = pageW - marginX - size, y = marginY). pageIndex1 je 1-based; ako je null, koristi poslednju
     * stranu.
     */
    public byte[] overlayIpsQr(
            byte[] pdf,
            String iban,
            String recipient,
            String amount,
            String currency,
            String purpose,
            String model,
            String reference,
            Integer pageIndex1,
            String corner,
            float sizePt,
            float marginX,
            float marginY)
            throws IOException {
        String payload =
                srbService.buildIpsQrPayload(
                        iban, recipient, amount, currency, purpose, model, reference);
        String b64;
        try {
            b64 = srbService.qrPngBase64(payload, (int) Math.max(256, sizePt));
        } catch (Exception e) {
            throw new IOException("Greška pri generisanju IPS-QR", e);
        }
        byte[] png = Base64.getDecoder().decode(b64);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() == 0) throw new IOException("Prazan PDF");
            int idx = (pageIndex1 == null ? doc.getNumberOfPages() : pageIndex1) - 1;
            if (idx < 0 || idx >= doc.getNumberOfPages())
                throw new IOException("pageIndex van opsega");
            PDPage page = doc.getPage(idx);
            PDRectangle box = page.getMediaBox();

            PDImageXObject img = PDImageXObject.createFromByteArray(doc, png, "ipsqr");
            float w = sizePt, h = sizePt; // kvadrat
            float x, y;
            String c = corner == null ? "br" : corner.toLowerCase();
            switch (c) {
                case "bl":
                    x = marginX;
                    y = marginY;
                    break;
                case "tl":
                    x = marginX;
                    y = box.getHeight() - marginY - h;
                    break;
                case "tr":
                    x = box.getWidth() - marginX - w;
                    y = box.getHeight() - marginY - h;
                    break;
                case "br":
                default:
                    x = box.getWidth() - marginX - w;
                    y = marginY;
                    break;
            }

            try (PDPageContentStream cs =
                    new PDPageContentStream(doc, page, AppendMode.APPEND, true)) {
                cs.drawImage(img, x, y, w, h);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Renderuje PNG preview izabrane strane sa ucrtanim IPS-QR (iste pozicije i veličine kao
     * overlay).
     */
    public byte[] renderIpsQrPreview(
            byte[] pdf,
            String iban,
            String recipient,
            String amount,
            String currency,
            String purpose,
            String model,
            String reference,
            Integer pageIndex1,
            String corner,
            float sizePt,
            float marginX,
            float marginY,
            float dpi)
            throws IOException {

        String payload =
                srbService.buildIpsQrPayload(
                        iban, recipient, amount, currency, purpose, model, reference);
        String b64;
        try {
            b64 = srbService.qrPngBase64(payload, (int) Math.max(256, sizePt));
        } catch (Exception e) {
            throw new IOException("Greška pri generisanju IPS-QR", e);
        }
        byte[] png = Base64.getDecoder().decode(b64);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() == 0) throw new IOException("Prazan PDF");
            int idx = (pageIndex1 == null ? doc.getNumberOfPages() : pageIndex1) - 1;
            if (idx < 0 || idx >= doc.getNumberOfPages())
                throw new IOException("pageIndex van opsega");
            PDPage page = doc.getPage(idx);
            PDRectangle box = page.getMediaBox();

            float wPt = sizePt, hPt = sizePt;
            float xPt, yPt;
            String c = corner == null ? "br" : corner.toLowerCase();
            switch (c) {
                case "bl":
                    xPt = marginX;
                    yPt = marginY;
                    break;
                case "tl":
                    xPt = marginX;
                    yPt = box.getHeight() - marginY - hPt;
                    break;
                case "tr":
                    xPt = box.getWidth() - marginX - wPt;
                    yPt = box.getHeight() - marginY - hPt;
                    break;
                case "br":
                default:
                    xPt = box.getWidth() - marginX - wPt;
                    yPt = marginY;
                    break;
            }

            float scale = dpi / 72f;
            int imgH = Math.max(1, Math.round(box.getHeight() * scale));

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImg = renderer.renderImageWithDPI(idx, dpi);

            BufferedImage qr = ImageIO.read(new java.io.ByteArrayInputStream(png));
            int drawW = Math.max(1, Math.round(wPt * scale));
            int drawH = Math.max(1, Math.round(hPt * scale));
            int drawX = Math.max(0, Math.round(xPt * scale));
            // Convert from PDF coords (bottom-left) to image coords (top-left origin)
            int drawY = Math.max(0, imgH - Math.round((yPt + hPt) * scale));

            BufferedImage out =
                    new BufferedImage(
                            pageImg.getWidth(), pageImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(pageImg, 0, 0, null);
                g.drawImage(qr, drawX, drawY, drawW, drawH, null);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return baos.toByteArray();
        }
    }

    /** Renderuje PNG izabrane strane bez QR-a, za interaktivni klijentski preview. */
    public byte[] renderPagePng(byte[] pdf, Integer pageIndex1, float dpi) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() == 0) throw new IOException("Prazan PDF");
            int idx = (pageIndex1 == null ? doc.getNumberOfPages() : pageIndex1) - 1;
            if (idx < 0 || idx >= doc.getNumberOfPages())
                throw new IOException("pageIndex van opsega");

            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage pageImg = renderer.renderImageWithDPI(idx, dpi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(pageImg, "png", baos);
            return baos.toByteArray();
        }
    }

    /**
     * Overlay na više strana prema specifikaciji: "all", "first", "last", ili lista npr. "1,3,5".
     */
    public byte[] overlayIpsQrBatch(
            byte[] pdf,
            String iban,
            String recipient,
            String amount,
            String currency,
            String purpose,
            String model,
            String reference,
            String pagesSpec,
            String corner,
            float sizePt,
            float marginX,
            float marginY)
            throws IOException {

        String payload =
                srbService.buildIpsQrPayload(
                        iban, recipient, amount, currency, purpose, model, reference);
        String b64;
        try {
            b64 = srbService.qrPngBase64(payload, (int) Math.max(256, sizePt));
        } catch (Exception e) {
            throw new IOException("Greška pri generisanju IPS-QR", e);
        }
        byte[] png = Base64.getDecoder().decode(b64);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int total = doc.getNumberOfPages();
            if (total == 0) throw new IOException("Prazan PDF");

            java.util.List<Integer> targets = new java.util.ArrayList<>();
            String spec =
                    (pagesSpec == null || pagesSpec.isBlank())
                            ? "last"
                            : pagesSpec.trim().toLowerCase();
            if ("all".equals(spec)) {
                for (int i = 0; i < total; i++) targets.add(i);
            } else if ("first".equals(spec)) {
                targets.add(0);
            } else if ("last".equals(spec)) {
                targets.add(total - 1);
            } else {
                String[] parts = spec.split(",");
                for (String p : parts) {
                    if (p.isBlank()) continue;
                    try {
                        int oneBased = Integer.parseInt(p.trim());
                        int idx = oneBased - 1;
                        if (idx >= 0 && idx < total) targets.add(idx);
                    } catch (NumberFormatException ignore) {
                        /* skip */
                    }
                }
                if (targets.isEmpty()) targets.add(total - 1);
            }

            PDImageXObject img = PDImageXObject.createFromByteArray(doc, png, "ipsqr");
            float w = sizePt, h = sizePt;
            String c = corner == null ? "br" : corner.toLowerCase();

            for (int idx : targets) {
                PDPage page = doc.getPage(idx);
                PDRectangle box = page.getMediaBox();
                float x, y;
                switch (c) {
                    case "bl":
                        x = marginX;
                        y = marginY;
                        break;
                    case "tl":
                        x = marginX;
                        y = box.getHeight() - marginY - h;
                        break;
                    case "tr":
                        x = box.getWidth() - marginX - w;
                        y = box.getHeight() - marginY - h;
                        break;
                    case "br":
                    default:
                        x = box.getWidth() - marginX - w;
                        y = marginY;
                        break;
                }
                try (PDPageContentStream cs =
                        new PDPageContentStream(doc, page, AppendMode.APPEND, true)) {
                    cs.drawImage(img, x, y, w, h);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

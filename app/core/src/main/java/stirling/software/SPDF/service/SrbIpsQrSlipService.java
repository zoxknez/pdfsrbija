package stirling.software.SPDF.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SrbIpsQrSlipService {

    private final SrbService srbService;

    /** Generiše jednostavan A4 slip sa IPS-QR kodom i osnovnim podacima o plaćanju. */
    public byte[] generateSlip(
            String iban,
            String recipient,
            String amount,
            String currency,
            String purpose,
            String model,
            String reference)
            throws IOException {
        String payload =
                srbService.buildIpsQrPayload(
                        iban, recipient, amount, currency, purpose, model, reference);
        String b64Png;
        try {
            b64Png = srbService.qrPngBase64(payload, 512);
        } catch (Exception e) {
            throw new IOException("Greška pri generisanju IPS-QR", e);
        }
        byte[] png = Base64.getDecoder().decode(b64Png);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDImageXObject qr = PDImageXObject.createFromByteArray(doc, png, "ipsqr");

            float margin = 48f;
            float qrSize = 256f;
            float leftColX = margin;
            float textTopY = page.getMediaBox().getHeight() - margin;
            float qrX = page.getMediaBox().getWidth() - margin - qrSize;
            float qrY = margin;

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs =
                    new PDPageContentStream(doc, page, AppendMode.OVERWRITE, false)) {
                // Naslov
                cs.beginText();
                cs.setFont(font, 18);
                cs.newLineAtOffset(leftColX, textTopY);
                cs.showText("IPS-QR Uplatnica");
                cs.endText();

                float y = textTopY - 28f;
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(leftColX, y);
                cs.showText("Primalac: " + safe(recipient));
                cs.endText();
                y -= 18f;

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(leftColX, y);
                cs.showText("IBAN: " + safe(iban));
                cs.endText();
                y -= 18f;

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(leftColX, y);
                cs.showText("Iznos: " + safe(amount) + " " + safe(currency));
                cs.endText();
                y -= 18f;

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(leftColX, y);
                cs.showText("Model: " + safe(model) + " / Poziv: " + safe(reference));
                cs.endText();
                y -= 18f;

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(leftColX, y);
                cs.showText("Svrha: " + safe(purpose));
                cs.endText();

                // QR desno dole
                cs.drawImage(qr, qrX, qrY, qrSize, qrSize);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

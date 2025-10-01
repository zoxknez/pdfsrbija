package stirling.software.SPDF.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

@Service
public class PdfEditService {

    /** Zameni stranicu stranicom iz drugog PDF-a. 1-based indeksi. */
    public byte[] replacePageWithPdf(
            byte[] basePdf, byte[] replacementPdf, int targetPage1, Integer sourcePage1)
            throws IOException {
        try (PDDocument base = Loader.loadPDF(basePdf);
                PDDocument repl = Loader.loadPDF(replacementPdf)) {

            if (base.getNumberOfPages() == 0) throw new IOException("Prazan osnovni PDF");
            int targetIdx = targetPage1 - 1;
            if (targetIdx < 0 || targetIdx >= base.getNumberOfPages())
                throw new IOException("targetPage van opsega: " + targetPage1);

            int srcIdx = (sourcePage1 == null ? 0 : sourcePage1 - 1);
            if (srcIdx < 0 || srcIdx >= repl.getNumberOfPages())
                throw new IOException(
                        "sourcePage van opsega: " + (sourcePage1 == null ? 1 : sourcePage1));

            PDPage src = repl.getPage(srcIdx);
            PDPage imported = base.importPage(src);

            base.getPages().insertBefore(imported, base.getPage(targetIdx));
            base.getPages().remove(targetIdx + 1);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            base.save(baos);
            return baos.toByteArray();
        }
    }

    /** Zameni stranicu slikom (PNG/JPG). fit: contain/cover. 1-based index. */
    public byte[] replacePageWithImage(
            byte[] basePdf, byte[] imageBytes, int targetPage1, String fit) throws IOException {
        try (PDDocument base = Loader.loadPDF(basePdf)) {
            if (base.getNumberOfPages() == 0) throw new IOException("Prazan osnovni PDF");
            int idx = targetPage1 - 1;
            if (idx < 0 || idx >= base.getNumberOfPages())
                throw new IOException("targetPage van opsega: " + targetPage1);

            PDPage oldPage = base.getPage(idx);
            PDRectangle box = oldPage.getMediaBox();

            PDPage newPage = new PDPage(box);
            base.getPages().insertBefore(newPage, oldPage);
            base.getPages().remove(oldPage);

            PDImageXObject img = PDImageXObject.createFromByteArray(base, imageBytes, "upload");
            float iw = img.getWidth(), ih = img.getHeight();
            float pw = box.getWidth(), ph = box.getHeight();

            boolean cover = "cover".equalsIgnoreCase(fit);
            float scale = cover ? Math.max(pw / iw, ph / ih) : Math.min(pw / iw, ph / ih);
            float w = iw * scale, h = ih * scale;
            float x = (pw - w) / 2f, y = (ph - h) / 2f;

            try (PDPageContentStream cs =
                    new PDPageContentStream(base, newPage, AppendMode.OVERWRITE, false)) {
                cs.drawImage(img, x, y, w, h);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            base.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Replace a page with a page from another PDF but keep the target media box size. This imports
     * the source page as a form XObject and scales/translates it to fit the target page media box,
     * preserving vector content. targetPage1/sourcePage1 are 1-based indices.
     */
    public byte[] replacePageWithPdfKeepSize(
        byte[] basePdf,
        byte[] replacementPdf,
        int targetPage1,
        Integer sourcePage1,
        String fit)
            throws IOException {
        try (PDDocument base = Loader.loadPDF(basePdf);
                PDDocument repl = Loader.loadPDF(replacementPdf)) {

            if (base.getNumberOfPages() == 0) throw new IOException("Prazan osnovni PDF");
            int targetIdx = targetPage1 - 1;
            if (targetIdx < 0 || targetIdx >= base.getNumberOfPages())
                throw new IOException("targetPage van opsega: " + targetPage1);

            int srcIdx = (sourcePage1 == null ? 0 : sourcePage1 - 1);
            if (srcIdx < 0 || srcIdx >= repl.getNumberOfPages())
                throw new IOException(
                        "sourcePage van opsega: " + (sourcePage1 == null ? 1 : sourcePage1));

            PDPage src = repl.getPage(srcIdx);
            PDPage target = base.getPage(targetIdx);

            PDRectangle targetBox = target.getMediaBox();
            PDRectangle srcBox = src.getMediaBox();

            // Import source page as a form XObject
            LayerUtility lu = new LayerUtility(base);
            PDFormXObject form = lu.importPageAsForm(repl, srcIdx);

            // Create a new page with the same media box as target, and place form onto it
            PDPage newPage = new PDPage(targetBox);
            base.getPages().insertBefore(newPage, target);
            base.getPages().remove(target);

            float pw = targetBox.getWidth();
            float ph = targetBox.getHeight();
            float sw = srcBox.getWidth();
            float sh = srcBox.getHeight();

            // determine scale based on fit mode
            String mode = (fit == null ? "contain" : fit).toLowerCase();
            float scale;
            switch (mode) {
                case "cover":
                    scale = Math.max(pw / sw, ph / sh);
                    break;
                case "fitwidth":
                    scale = pw / sw;
                    break;
                case "fitheight":
                    scale = ph / sh;
                    break;
                default: // contain
                    scale = Math.min(pw / sw, ph / sh);
            }
            float w = sw * scale;
            float h = sh * scale;
            float tx = (pw - w) / 2f;
            float ty = (ph - h) / 2f;

            try (PDPageContentStream cs =
                    new PDPageContentStream(base, newPage, AppendMode.OVERWRITE, false)) {
                Matrix mat = Matrix.getTranslateInstance(tx, ty);
                mat.scale(scale, scale);
                cs.transform(mat);
                cs.drawForm(form);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            base.save(baos);
            return baos.toByteArray();
        }
    }
}

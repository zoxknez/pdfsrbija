package stirling.software.SPDF.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

public class PdfEditServiceTests {

    private byte[] onePager(PDRectangle size) throws Exception {
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage(size));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            d.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void replacePdf_keepSize_vector_contain() throws Exception {
        PdfEditService svc = new PdfEditService();
        byte[] base = onePager(PDRectangle.A4);
        byte[] src  = onePager(new PDRectangle(400, 400));
        byte[] out  = svc.replacePageWithPdfKeepSize(base, src, 1, 1, "contain");
        try (PDDocument d = PDDocument.load(out)) {
            assertEquals(1, d.getNumberOfPages());
            PDRectangle box = d.getPage(0).getMediaBox();
            assertEquals(Math.round(PDRectangle.A4.getWidth()), Math.round(box.getWidth()));
            assertEquals(Math.round(PDRectangle.A4.getHeight()), Math.round(box.getHeight()));
        }
    }

    @Test
    void replacePdf_simple_changesSize() throws Exception {
        PdfEditService svc = new PdfEditService();
        byte[] base = onePager(PDRectangle.A4);
        PDRectangle srcSize = new PDRectangle(300, 500);
        byte[] src  = onePager(srcSize);
        byte[] out  = svc.replacePageWithPdf(base, src, 1, 1);
        try (PDDocument d = PDDocument.load(out)) {
            PDRectangle box = d.getPage(0).getMediaBox();
            assertEquals(Math.round(srcSize.getWidth()),  Math.round(box.getWidth()));
            assertEquals(Math.round(srcSize.getHeight()), Math.round(box.getHeight()));
        }
    }
}

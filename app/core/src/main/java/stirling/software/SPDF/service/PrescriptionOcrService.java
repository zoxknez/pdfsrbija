package stirling.software.SPDF.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import lombok.Data;

@Service
public class PrescriptionOcrService {

    private final Tesseract tess;
    private final List<Template> templates;

    public PrescriptionOcrService(
            @Value("${tesseract.tessdata.path:${TESSDATA_PATH:/app/tessdata}}") String tessdataPath,
            @Value("${srb.ocr.prescription.zones:srb/ocr-prescription-zones.json}")
                    String zonesPath) {
        tess = new Tesseract();
        // Pokušaj da pronađe validan tessdata path; ako nije pronađen, nemoj forsirati pogrešan put
        String envPrefix = System.getenv("TESSDATA_PREFIX");
        try {
            if (envPrefix != null && new File(envPrefix).exists()) {
                tess.setDatapath(envPrefix);
            } else if (tessdataPath != null && new File(tessdataPath).exists()) {
                tess.setDatapath(tessdataPath);
            } // else: prepusti Tesseract-u da koristi sistemski podrazumevani put
        } catch (Throwable ignore) {}
        tess.setLanguage("srp_latn+srp+eng");
        tess.setOcrEngineMode(1); // LSTM_ONLY
        tess.setPageSegMode(6); // Assume a single uniform block of text
        this.templates = loadTemplates(zonesPath);
    }

    /** Ulaz: PDF/PNG/JPG bajtovi. Izlaz: strukturisani rezultat. */
    public Result extract(byte[] fileBytes, String filename) throws Exception {
        BufferedImage img = toImage(fileBytes, filename);
        String barcode = tryDecodeBarcode(img);

        Template tpl = templates.isEmpty() ? null : templates.get(0);
        Map<String, String> fields = new LinkedHashMap<>();

        if (tpl != null) {
            Rect rPatient = tpl.zones.get("patient");
            Rect rLbo = tpl.zones.get("lbo");
            Rect rDate = tpl.zones.get("date");
            Rect rDoctor = tpl.zones.get("doctor");
            Rect rMkb = tpl.zones.get("mkb");
            Rect rMeds = tpl.zones.get("medsBlock");

            fields.put("patient", ocrByRect(img, rPatient, false));
            fields.put("lbo", ocrByRect(img, rLbo, true));
            fields.put("date", ocrByRect(img, rDate, false));
            fields.put("doctor", ocrByRect(img, rDoctor, false));
            fields.put("mkb", ocrByRect(img, rMkb, false));
            String medsBlock = ocrByRect(img, rMeds, false);

            fields.replaceAll((k, v) -> normalize(v));
            String date = normalizeDate(fields.get("date"));
            String lbo = normalizeDigits(fields.get("lbo"));

            Result r = new Result();
            r.setBarcode(barcode);
            r.setFields(fields);
            r.setDate(date);
            r.setLbo(lbo);
            r.setMeds(parseMeds(medsBlock));
            r.setValidations(buildValidations(fields, lbo));
            return r;
        } else {
            // Fallback hardcoded zones
            fields.put("patient", ocrCrop(img, 0.10, 0.78, 0.80, 0.06));
            fields.put("lbo", ocrCropDigits(img, 0.10, 0.84, 0.35, 0.05));
            fields.put("date", ocrCrop(img, 0.65, 0.09, 0.25, 0.05));
            fields.put("doctor", ocrCrop(img, 0.10, 0.12, 0.55, 0.06));
            fields.put("mkb", ocrCrop(img, 0.72, 0.18, 0.18, 0.05));

            String medsBlock = ocrCrop(img, 0.08, 0.30, 0.84, 0.40);

            fields.replaceAll((k, v) -> normalize(v));
            String date = normalizeDate(fields.get("date"));
            String lbo = normalizeDigits(fields.get("lbo"));

            Result r = new Result();
            r.setBarcode(barcode);
            r.setFields(fields);
            r.setDate(date);
            r.setLbo(lbo);
            r.setMeds(parseMeds(medsBlock));
            r.setValidations(buildValidations(fields, lbo));
            return r;
        }
    }

    /** Render preview PNG with configured OCR zones highlighted (page 1). */
    public byte[] renderZonePreview(byte[] fileBytes, String filename, float dpi) throws Exception {
        BufferedImage pageImg = toPageImage(fileBytes, filename, dpi);
        if (templates.isEmpty()) {
            return toPngBytes(pageImg);
        }
        Template tpl = templates.get(0);
        Graphics2D g = pageImg.createGraphics();
        try {
            g.setStroke(new BasicStroke(2f));
            for (Map.Entry<String, Rect> e : tpl.zones.entrySet()) {
                Rect r = e.getValue();
                if (r == null) continue;
                // Convert relative rect to image pixels
                int x = (int) Math.round(pageImg.getWidth() * r.x);
                int yTop = (int) Math.round(pageImg.getHeight() * r.y);
                int w = (int) Math.round(pageImg.getWidth() * r.w);
                int h = (int) Math.round(pageImg.getHeight() * r.h);
                // Our relative coords are already in image space (top-left), so use directly
                g.setColor(new Color(255, 200, 0, 80));
                g.fillRect(x, yTop, w, h);
                g.setColor(new Color(255, 140, 0, 220));
                g.drawRect(x, yTop, w, h);
                g.setColor(Color.WHITE);
                g.drawString(e.getKey(), x + 4, yTop + 14);
            }
        } finally {
            g.dispose();
        }
        return toPngBytes(pageImg);
    }

    private Map<String, Object> buildValidations(Map<String, String> fields, String lbo) {
        Map<String, Object> v = new LinkedHashMap<>();
        String patient = fields.getOrDefault("patient", "");
        String date = fields.getOrDefault("date", "");
        v.put("lboValid", validateLbo(lbo));
        v.put("datePresent", date != null && date.length() >= 5);
        v.put("patientPresent", patient != null && patient.length() >= 3);
        return v;
    }

    private BufferedImage toImage(byte[] bytes, String name) throws Exception {
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (var doc = Loader.loadPDF(bytes)) {
                var renderer = new PDFRenderer(doc);
                return renderer.renderImageWithDPI(0, 300, ImageType.GRAY);
            }
        }
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private BufferedImage toPageImage(byte[] bytes, String name, float dpi) throws Exception {
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (var doc = Loader.loadPDF(bytes)) {
                var renderer = new PDFRenderer(doc);
                return renderer.renderImageWithDPI(0, dpi <= 0 ? 150 : dpi, ImageType.RGB);
            }
        }
        // For images, just return the original (we won't change DPI)
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private String tryDecodeBarcode(BufferedImage img) {
        try {
            var src = new BufferedImageLuminanceSource(img);
            var bin = new HybridBinarizer(src);
            var bitmap = new BinaryBitmap(bin);
            var hints = new Hashtable<DecodeHintType, Object>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            var result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (Exception e) {
            return null;
        }
    }

    private String ocrByRect(BufferedImage img, Rect r, boolean digits) throws TesseractException {
        if (r == null) return "";
        if (digits) {
            return ocrCropDigits(img, r.x, r.y, r.w, r.h);
        }
        return ocrCrop(img, r.x, r.y, r.w, r.h);
    }

    private String ocrCrop(BufferedImage img, double x, double y, double w, double h)
            throws TesseractException {
        int X = (int) (img.getWidth() * x), Y = (int) (img.getHeight() * y);
        int W = (int) (img.getWidth() * w), H = (int) (img.getHeight() * h);
        var sub =
                img.getSubimage(
                        Math.max(0, X),
                        Math.max(0, Y),
                        Math.min(W, img.getWidth() - X),
                        Math.min(H, img.getHeight() - Y));
        try {
            return tess.doOCR(sub);
        } catch (Throwable t) {
            // Ne propagiraj native crash; vrati prazno polje ili baci TesseractException sa porukom
            if (t instanceof TesseractException te) throw te;
            throw new TesseractException("Tesseract native greška: " + t.getMessage());
        }
    }

    private String ocrCropDigits(BufferedImage img, double x, double y, double w, double h)
            throws TesseractException {
        tess.setTessVariable("tessedit_char_whitelist", "0123456789/");
        try {
            return ocrCrop(img, x, y, w, h);
        } finally {
            tess.setTessVariable("tessedit_char_whitelist", null);
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("[\\t\\r]+", " ").trim();
    }

    private static String normalizeDigits(String s) {
        if (s == null) return null;
        return s.replaceAll("[^0-9]", "{}").replace("{}", "").trim();
    }

    private static String normalizeDate(String s) {
        if (s == null) return null;
        var m = Pattern.compile("(\\d{1,2})[\\.\\/-](\\d{1,2})[\\.\\/-](\\d{2,4})").matcher(s);
        if (m.find()) {
            String d = String.format("%02d", Integer.parseInt(m.group(1)));
            String mo = String.format("%02d", Integer.parseInt(m.group(2)));
            String y = m.group(3);
            if (y.length() == 2) y = "20" + y;
            return d + "." + mo + "." + y + ".";
        }
        return s.trim();
    }

    private static List<String> parseMeds(String block) {
        if (block == null) return List.of();
        return Arrays.stream(block.split("\\R"))
                .map(String::trim)
                .filter(s -> s.length() > 1)
                .limit(10)
                .toList();
    }

    private static boolean validateLbo(String lbo) {
        if (lbo == null || !lbo.matches("\\d{11}")) return false;
        // LBO often doesn't include checksum publicly documented; basic length-only validation
        return true;
    }

    private List<Template> loadTemplates(String zonesPath) {
        try {
            InputStream in = null;
            // First try as classpath resource under configs/
            String cp = zonesPath;
            if (!cp.startsWith("configs/")) {
                // default resource location configured in build.gradle adds ../configs to CP
                cp = "configs/" + zonesPath;
            }
            try {
                ClassPathResource cpr = new ClassPathResource(cp);
                if (cpr.exists()) in = cpr.getInputStream();
            } catch (Exception ignore) {
            }

            if (in == null) {
                // try filesystem
                File f = new File(zonesPath);
                if (f.exists()) in = new FileInputStream(f);
            }
            if (in == null) return List.of();

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            List<Template> out = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode n : root) out.add(parseTemplate(n));
            } else {
                out.add(parseTemplate(root));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Template parseTemplate(JsonNode node) {
        Template t = new Template();
        t.name = of(node, "name", "generic");
        t.page = ofInt(node, "page", 0);
        t.zones = new LinkedHashMap<>();
        JsonNode z = node.get("zones");
        if (z != null && z.isObject()) {
            z.fields().forEachRemaining(e -> t.zones.put(e.getKey(), parseRect(e.getValue())));
        }
        return t;
    }

    private static Rect parseRect(JsonNode n) {
        Rect r = new Rect();
        r.x = ofDouble(n, "x", 0);
        r.y = ofDouble(n, "y", 0);
        r.w = ofDouble(n, "w", 1);
        r.h = ofDouble(n, "h", 1);
        r.digitsOnly = ofBool(n, "digitsOnly", false);
        return r;
    }

    private static String of(JsonNode n, String field, String def) {
        JsonNode v = n.get(field);
        return v != null && v.isTextual() ? v.asText() : def;
    }

    private static int ofInt(JsonNode n, String field, int def) {
        JsonNode v = n.get(field);
        return v != null && v.isInt() ? v.asInt() : def;
    }

    private static double ofDouble(JsonNode n, String field, double def) {
        JsonNode v = n.get(field);
        return v != null && v.isNumber() ? v.asDouble() : def;
    }

    private static boolean ofBool(JsonNode n, String field, boolean def) {
        JsonNode v = n.get(field);
        return v != null && v.isBoolean() ? v.asBoolean() : def;
    }

    @Data
    public static class Result {
        private String barcode; // QR/Code128 vrednost (ako postoji)
        private Map<String, String> fields;
        private String date;
        private String lbo;
        private List<String> meds;
        private Map<String, Object> validations;
    }

    public static class Template {
        public String name;
        public int page;
        public Map<String, Rect> zones;
    }

    public static class Rect {
        public double x, y, w, h; // relative to image (0..1), top-left origin
        public boolean digitsOnly;
    }
}

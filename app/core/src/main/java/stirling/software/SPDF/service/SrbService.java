package stirling.software.SPDF.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class SrbService {

    /* ===================== IBAN (RS) ===================== */

    public boolean isValidIban(String ibanRaw) {
        if (ibanRaw == null) return false;
        String iban = ibanRaw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!iban.matches("[A-Z0-9]+")) return false;
        if (iban.length() < 15 || iban.length() > 34) return false;

        // Rearrange: move first 4 chars to end, A=10..Z=35
        String rearr = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearr.length() * 2);
        for (char c : rearr.toCharArray()) {
            if (Character.isDigit(c)) numeric.append(c);
            else numeric.append((int) (c - 'A' + 10));
        }
        BigInteger num = new BigInteger(numeric.toString());
        return num.mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    public boolean isValidIbanRS(String ibanRaw) {
        if (ibanRaw == null) return false;
        String iban = ibanRaw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!iban.startsWith("RS")) return false;
        if (iban.length() != 22) return false; // RS + 20 cifara
        return isValidIban(iban);
    }

    public String formatIbanPretty(String ibanRaw) {
        if (ibanRaw == null) return "";
        String s = ibanRaw.replaceAll("\\s+", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append(' ');
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    /* ===================== PIB (poreski identifikacioni broj) ===================== */

    /**
     * Provera srpskog PIB-a (9 cifara). Kontrolna cifra se računa kao:
     * c = 11 - ((10*a1 + 9*a2 + 8*a3 + 7*a4 + 6*a5 + 5*a6 + 4*a7 + 3*a8) mod 11),
     * a zatim ako je c > 9 postavlja se na 0. PIB je ispravan ako je c == a9.
     */
    public boolean validatePib(String pibRaw) {
        if (pibRaw == null) return false;
        String s = pibRaw.replaceAll("\\D", "");
        if (!s.matches("\\d{9}")) return false;
        int[] a = new int[9];
        for (int i = 0; i < 9; i++) a[i] = s.charAt(i) - '0';
        int sum = 10 * a[0] + 9 * a[1] + 8 * a[2] + 7 * a[3] + 6 * a[4] + 5 * a[5] + 4 * a[6] + 3 * a[7];
        int c = 11 - (sum % 11);
        if (c > 9) c = 0;
        return c == a[8];
    }

    /* ===================== JMBG (jedinstveni matični broj građana) ===================== */

    /**
     * Validacija JMBG-a (13 cifara) + parsiranje osnovnih polja.
     * Struktura: DD MM YYY RR BBB K
     * Kontrolna cifra K: k = 11 - ((7*(a1+a7) + 6*(a2+a8) + 5*(a3+a9) + 4*(a4+a10) + 3*(a5+a11) + 2*(a6+a12)) mod 11);
     * ako je k > 9 onda je k = 0. Mora se poklapati sa a13.
     */
    public Map<String, Object> validateJmbg(String jmbgRaw) {
        Map<String, Object> res = new HashMap<>();
        res.put("jmbg", jmbgRaw == null ? "" : jmbgRaw);
        res.put("valid", false);
        if (jmbgRaw == null) return res;
        String s = jmbgRaw.replaceAll("\\D", "");
        res.put("normalized", s);
        if (!s.matches("\\d{13}")) return res;

        int[] a = new int[13];
        for (int i = 0; i < 13; i++) a[i] = s.charAt(i) - '0';

        int day = a[0] * 10 + a[1];
        int month = a[2] * 10 + a[3];
        int yyy = a[4] * 100 + a[5] * 10 + a[6];
        // Heuristika za vek: 900-999 -> 1900-1999; 000-099 -> 2000-2099; ostalo -> 1000+yyy (1900-1999 pokriveno)
        int year = (yyy >= 900) ? (1000 + yyy) : (2000 + yyy);
        res.put("day", day);
        res.put("month", month);
        res.put("year", year);

        int region = a[7] * 10 + a[8];
        res.put("region", region);

        int bbb = a[9] * 100 + a[10] * 10 + a[11];
        String gender = (bbb >= 500) ? "F" : "M";
        res.put("sequence", bbb);
        res.put("gender", gender);

        // Provera datuma
        try {
            java.time.LocalDate.of(year, month, day);
        } catch (Exception e) {
            res.put("error", "Neispravan datum u JMBG");
            return res;
        }

        int sum = 7 * (a[0] + a[6])
                + 6 * (a[1] + a[7])
                + 5 * (a[2] + a[8])
                + 4 * (a[3] + a[9])
                + 3 * (a[4] + a[10])
                + 2 * (a[5] + a[11]);
        int k = 11 - (sum % 11);
        if (k > 9) k = 0;
        boolean ok = k == a[12];
        res.put("valid", ok);
        return res;
    }

    /* ===================== Model 97 ===================== */

    public Map<String, String> generateModel97(String baseDigits) {
        String digits = baseDigits == null ? "" : baseDigits.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Map.of("base", "", "control", "00", "reference", "00");
        }
        BigInteger n = new BigInteger(digits);
        int mod = n.mod(BigInteger.valueOf(97)).intValue();
        int control = 98 - mod;
        if (control < 0) control = (control % 97 + 97) % 97; // safety
        String cc = String.format("%02d", control);
        return Map.of(
                "base", digits,
                "control", cc,
                "reference", digits + cc);
    }

    public boolean validateModel97(String reference) {
        if (reference == null || !reference.matches("\\d{3,}")) return false;
        String base = reference.substring(0, reference.length() - 2);
        String cc = reference.substring(reference.length() - 2);
        Map<String, String> gen = generateModel97(base);
        return cc.equals(gen.get("control"));
    }

    /* ===================== Transliteracija ===================== */
    /* ===================== Transliteracija (robust) ===================== */

    private static String preserveCase(String src, String lower, String upper) {
        if (src == null || src.length() == 0) return lower;
        char c0 = src.charAt(0);
        return Character.isUpperCase(c0) ? upper : lower;
    }

    /** Latinica → Ćirilica, sa digrafima i čuvanjem početnog velikog slova. */
    public String latinToCyrillic(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC);

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            // check digraphs (3 two-letter ones: Dž, Lj, Nj)
            if (i + 1 < s.length()) {
                String two = s.substring(i, i + 2);
                if ("Dž".equals(two) || "DŽ".equals(two)) {
                    out.append("Џ");
                    i++;
                    continue;
                }
                if ("dž".equals(two)) {
                    out.append("џ");
                    i++;
                    continue;
                }
                if ("Lj".equals(two) || "LJ".equals(two)) {
                    out.append("Љ");
                    i++;
                    continue;
                }
                if ("lj".equals(two)) {
                    out.append("љ");
                    i++;
                    continue;
                }
                if ("Nj".equals(two) || "NJ".equals(two)) {
                    out.append("Њ");
                    i++;
                    continue;
                }
                if ("nj".equals(two)) {
                    out.append("њ");
                    i++;
                    continue;
                }
            }

            String one = s.substring(i, i + 1);
            switch (one) {
                case "A":
                    out.append("А");
                    break;
                case "a":
                    out.append("а");
                    break;
                case "B":
                    out.append("Б");
                    break;
                case "b":
                    out.append("б");
                    break;
                case "C":
                    out.append("Ц");
                    break;
                case "c":
                    out.append("ц");
                    break;
                case "Č":
                    out.append("Ч");
                    break;
                case "č":
                    out.append("ч");
                    break;
                case "Ć":
                    out.append("Ћ");
                    break;
                case "ć":
                    out.append("ћ");
                    break;
                case "D":
                    out.append("Д");
                    break;
                case "d":
                    out.append("д");
                    break;
                case "Đ":
                    out.append("Ђ");
                    break;
                case "đ":
                    out.append("ђ");
                    break;
                case "E":
                    out.append("Е");
                    break;
                case "e":
                    out.append("е");
                    break;
                case "F":
                    out.append("Ф");
                    break;
                case "f":
                    out.append("ф");
                    break;
                case "G":
                    out.append("Г");
                    break;
                case "g":
                    out.append("г");
                    break;
                case "H":
                    out.append("Х");
                    break;
                case "h":
                    out.append("х");
                    break;
                case "I":
                    out.append("И");
                    break;
                case "i":
                    out.append("и");
                    break;
                case "J":
                    out.append("Ј");
                    break;
                case "j":
                    out.append("ј");
                    break;
                case "K":
                    out.append("К");
                    break;
                case "k":
                    out.append("к");
                    break;
                case "L":
                    out.append("Л");
                    break;
                case "l":
                    out.append("л");
                    break;
                case "M":
                    out.append("М");
                    break;
                case "m":
                    out.append("м");
                    break;
                case "N":
                    out.append("Н");
                    break;
                case "n":
                    out.append("н");
                    break;
                case "O":
                    out.append("О");
                    break;
                case "o":
                    out.append("о");
                    break;
                case "P":
                    out.append("П");
                    break;
                case "p":
                    out.append("п");
                    break;
                case "R":
                    out.append("Р");
                    break;
                case "r":
                    out.append("р");
                    break;
                case "S":
                    out.append("С");
                    break;
                case "s":
                    out.append("с");
                    break;
                case "Š":
                    out.append("Ш");
                    break;
                case "š":
                    out.append("ш");
                    break;
                case "T":
                    out.append("Т");
                    break;
                case "t":
                    out.append("т");
                    break;
                case "U":
                    out.append("У");
                    break;
                case "u":
                    out.append("у");
                    break;
                case "V":
                    out.append("В");
                    break;
                case "v":
                    out.append("в");
                    break;
                case "Z":
                    out.append("З");
                    break;
                case "z":
                    out.append("з");
                    break;
                case "Ž":
                    out.append("Ж");
                    break;
                case "ž":
                    out.append("ж");
                    break;
                default:
                    out.append(one);
                    break;
            }
        }
        return out.toString();
    }

    /** Ćirilica → Latinica, sa digrafima i čuvanjem početnog velikog slova. */
    public String cyrillicToLatin(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC);

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            // handle digraphs Љ, Њ, Џ with case-aware output
            if ("Љ".equals(ch)
                    || "љ".equals(ch)
                    || "Њ".equals(ch)
                    || "њ".equals(ch)
                    || "Џ".equals(ch)
                    || "џ".equals(ch)) {
                boolean upper = Character.isUpperCase(ch.charAt(0));
                boolean nextUpper = (i + 1 < s.length() && Character.isUpperCase(s.charAt(i + 1)));
                if ("Љ".equals(ch) || "љ".equals(ch)) {
                    if (upper) out.append(nextUpper ? "LJ" : "Lj");
                    else out.append("lj");
                } else if ("Њ".equals(ch) || "њ".equals(ch)) {
                    if (upper) out.append(nextUpper ? "NJ" : "Nj");
                    else out.append("nj");
                } else { // Џ / џ
                    if (upper) out.append(nextUpper ? "DŽ" : "Dž");
                    else out.append("dž");
                }
                continue;
            }
            switch (ch) {
                case "А":
                    out.append("A");
                    break;
                case "а":
                    out.append("a");
                    break;
                case "Б":
                    out.append("B");
                    break;
                case "б":
                    out.append("b");
                    break;
                case "Ц":
                    out.append("C");
                    break;
                case "ц":
                    out.append("c");
                    break;
                case "Ч":
                    out.append("Č");
                    break;
                case "ч":
                    out.append("č");
                    break;
                case "Ћ":
                    out.append("Ć");
                    break;
                case "ћ":
                    out.append("ć");
                    break;
                case "Д":
                    out.append("D");
                    break;
                case "д":
                    out.append("d");
                    break;
                case "Ђ":
                    out.append("Đ");
                    break;
                case "ђ":
                    out.append("đ");
                    break;
                case "Е":
                    out.append("E");
                    break;
                case "е":
                    out.append("e");
                    break;
                case "Ф":
                    out.append("F");
                    break;
                case "ф":
                    out.append("f");
                    break;
                case "Г":
                    out.append("G");
                    break;
                case "г":
                    out.append("g");
                    break;
                case "Х":
                    out.append("H");
                    break;
                case "х":
                    out.append("h");
                    break;
                case "И":
                    out.append("I");
                    break;
                case "и":
                    out.append("i");
                    break;
                case "Ј":
                    out.append("J");
                    break;
                case "ј":
                    out.append("j");
                    break;
                case "К":
                    out.append("K");
                    break;
                case "к":
                    out.append("k");
                    break;
                case "Л":
                    out.append("L");
                    break;
                case "л":
                    out.append("l");
                    break;
                case "Љ":
                    out.append("Lj");
                    break;
                case "љ":
                    out.append("lj");
                    break;
                case "М":
                    out.append("M");
                    break;
                case "м":
                    out.append("m");
                    break;
                case "Н":
                    out.append("N");
                    break;
                case "н":
                    out.append("n");
                    break;
                case "Њ":
                    out.append("Nj");
                    break;
                case "њ":
                    out.append("nj");
                    break;
                case "О":
                    out.append("O");
                    break;
                case "о":
                    out.append("o");
                    break;
                case "П":
                    out.append("P");
                    break;
                case "п":
                    out.append("p");
                    break;
                case "Р":
                    out.append("R");
                    break;
                case "р":
                    out.append("r");
                    break;
                case "С":
                    out.append("S");
                    break;
                case "с":
                    out.append("s");
                    break;
                case "Ш":
                    out.append("Š");
                    break;
                case "ш":
                    out.append("š");
                    break;
                case "Т":
                    out.append("T");
                    break;
                case "т":
                    out.append("t");
                    break;
                case "У":
                    out.append("U");
                    break;
                case "у":
                    out.append("u");
                    break;
                case "В":
                    out.append("V");
                    break;
                case "в":
                    out.append("v");
                    break;
                case "З":
                    out.append("Z");
                    break;
                case "з":
                    out.append("z");
                    break;
                case "Ж":
                    out.append("Ž");
                    break;
                case "ж":
                    out.append("ž");
                    break;
                case "Џ":
                    out.append("Dž");
                    break;
                case "џ":
                    out.append("dž");
                    break;
                default:
                    out.append(ch);
                    break;
            }
        }
        return out.toString();
    }

    /* ===================== IPS-QR (payload + QR PNG) ===================== */

    public String buildIpsQrPayload(
            String ibanRs,
            String recipientName,
            String amountDecimal,
            String currency,
            String purpose,
            String model,
            String reference) {
        String iban = ibanRs == null ? "" : ibanRs.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String cur =
                (currency == null || currency.isBlank())
                        ? "RSD"
                        : currency.toUpperCase(Locale.ROOT);
        String m = (model == null || model.isBlank()) ? "97" : model;

        StringBuilder sb = new StringBuilder();
        sb.append("K:PR|V:01|C:SRB");
        if (!iban.isBlank()) sb.append("|R:").append(iban);
        if (recipientName != null && !recipientName.isBlank())
            sb.append("|N:").append(escape(recipientName));
        if (amountDecimal != null && !amountDecimal.isBlank())
            sb.append("|I:").append(amountDecimal);
        sb.append("|CUR:").append(cur);
        if (purpose != null && !purpose.isBlank()) sb.append("|P:").append(escape(purpose));
        if (m != null && !m.isBlank()) sb.append("|M:").append(m);
        if (reference != null && !reference.isBlank())
            sb.append("|S:").append(reference.replaceAll("\\s+", ""));
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("|", "/").replace(":", "-");
    }

    public String qrPngBase64(String payload, int sizePx) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix =
                new MultiFormatWriter()
                        .encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /** Parsira IPS-QR payload oblika K:PR|V:01|C:SRB|R:RS..|N:...|I:...|CUR:...|P:...|M:..|S:... */
    public Map<String, String> parseIpsQrPayload(String payload) {
        Map<String, String> out = new HashMap<>();
        if (payload == null) return out;
        String[] parts = payload.split("\\|");
        for (String p : parts) {
            int i = p.indexOf(':');
            if (i <= 0) continue;
            String k = p.substring(0, i).trim();
            String v = p.substring(i + 1).trim();
            switch (k) {
                case "R":
                    out.put("iban", v);
                    break;
                case "N":
                    out.put("recipient", v);
                    break;
                case "I":
                    out.put("amount", v);
                    break;
                case "CUR":
                    out.put("currency", v);
                    break;
                case "P":
                    out.put("purpose", v);
                    break;
                case "M":
                    out.put("model", v);
                    break;
                case "S":
                    out.put("reference", v);
                    break;
                case "K":
                    out.put("kind", v);
                    break;
                case "V":
                    out.put("version", v);
                    break;
                case "C":
                    out.put("country", v);
                    break;
                default:
                    out.put(k, v);
                    break;
            }
        }
        return out;
    }

    /** Dekodira QR sa slike (PNG/JPG) i vraća payload + parsirana polja. */
    public Map<String, Object> decodeIpsQrFromImage(byte[] imageBytes) throws Exception {
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (img == null) throw new IllegalArgumentException("Nepoznat format slike");
        LuminanceSource source = new BufferedImageLuminanceSource(img);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap);
        String text = result.getText();
        Map<String, String> fields = parseIpsQrPayload(text);
        return Map.of("payload", text, "fields", fields);
    }

    /** Renderuje PDF stranu i pokušava da dekodira QR. */
    public Map<String, Object> decodeIpsQrFromPdf(byte[] pdfBytes, Integer pageIndex1, float dpi)
            throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("Prazan PDF");
            int idx = (pageIndex1 == null ? doc.getNumberOfPages() : pageIndex1) - 1;
            if (idx < 0 || idx >= doc.getNumberOfPages())
                throw new IllegalArgumentException("pageIndex van opsega");
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(doc);
            BufferedImage img = renderer.renderImageWithDPI(idx, dpi);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return decodeIpsQrFromImage(baos.toByteArray());
        }
    }
}

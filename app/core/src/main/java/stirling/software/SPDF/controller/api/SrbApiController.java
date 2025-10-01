package stirling.software.SPDF.controller.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.SrbService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SrbApiController {

    private final SrbService srb;

    /* ===== IBAN ===== */

    @PostMapping(
            path = "/iban/validate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> validateIban(@RequestBody Map<String, String> body) {
        String iban = body.getOrDefault("iban", "");
        boolean valid = srb.isValidIban(iban);
        boolean validRS = srb.isValidIbanRS(iban);
        return Map.of(
                "iban",
                srb.formatIbanPretty(iban),
                "valid",
                valid,
                "validRS",
                validRS,
                "length",
                iban.replaceAll("\\s+", "").length(),
                "country",
                iban.length() >= 2 ? iban.substring(0, 2).toUpperCase() : "");
    }

    /* ===== Model 97 ===== */

    @PostMapping(
            path = "/model97/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, String> genModel97(@RequestBody Map<String, String> body) {
        String base = body.getOrDefault("base", "");
        return srb.generateModel97(base);
    }

    @PostMapping(
            path = "/model97/validate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> valModel97(@RequestBody Map<String, String> body) {
        String ref = body.getOrDefault("reference", "");
        boolean ok = srb.validateModel97(ref);
        return Map.of("reference", ref, "valid", ok);
    }

        /* ===== PIB / JMBG ===== */

        @PostMapping(
                        path = "/pib/validate",
                        consumes = MediaType.APPLICATION_JSON_VALUE,
                        produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
        public Map<String, Object> validatePib(@RequestBody Map<String, String> body) {
                String pib = body.getOrDefault("pib", "");
                boolean ok = srb.validatePib(pib);
                return Map.of("pib", pib, "valid", ok);
        }

        @PostMapping(
                        path = "/jmbg/validate",
                        consumes = MediaType.APPLICATION_JSON_VALUE,
                        produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
        public Map<String, Object> validateJmbg(@RequestBody Map<String, String> body) {
                String jmbg = body.getOrDefault("jmbg", "");
                return srb.validateJmbg(jmbg);
        }

    /* ===== Transliteracija ===== */

    @PostMapping(
            path = "/translit",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, String> translit(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        String dir = body.getOrDefault("direction", "lat2cyr");
        String out =
                "lat2cyr".equalsIgnoreCase(dir)
                        ? srb.latinToCyrillic(text)
                        : srb.cyrillicToLatin(text);
        return Map.of("text", out);
    }

    /* ===== IPS-QR ===== */

    @PostMapping(
            path = "/ipsqr",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, String> ipsqr(@RequestBody Map<String, String> body) throws Exception {
        String payload =
                srb.buildIpsQrPayload(
                        body.get("iban"),
                        body.getOrDefault("recipient", ""),
                        body.getOrDefault("amount", ""),
                        body.getOrDefault("currency", "RSD"),
                        body.getOrDefault("purpose", ""),
                        body.getOrDefault("model", "97"),
                        body.getOrDefault("reference", ""));
        String pngB64 = srb.qrPngBase64(payload, 512);
        return Map.of("payload", payload, "qrPngBase64", pngB64);
    }

    @PostMapping(
            path = "/ipsqr/decode/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> ipsqrDecodeImage(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file)
            throws Exception {
        return srb.decodeIpsQrFromImage(file.getBytes());
    }

    @PostMapping(
            path = "/ipsqr/decode/pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> ipsqrDecodePdf(
            @RequestPart("pdf") org.springframework.web.multipart.MultipartFile pdf,
            @RequestParam(required = false) Integer pageIndex,
            @RequestParam(defaultValue = "144") float dpi)
            throws Exception {
        return srb.decodeIpsQrFromPdf(pdf.getBytes(), pageIndex, dpi);
    }

    @PostMapping(
            path = "/ipsqr/parse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, String> ipsqrParse(@RequestBody Map<String, String> body) {
        return srb.parseIpsQrPayload(body.getOrDefault("payload", ""));
    }
}

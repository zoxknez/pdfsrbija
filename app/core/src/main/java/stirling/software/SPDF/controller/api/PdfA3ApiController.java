package stirling.software.SPDF.controller.api;

// using service's lightweight validation result
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.PdfA3Service;

@RestController
@RequestMapping("/api/pdfa3")
@RequiredArgsConstructor
public class PdfA3ApiController {

    private final PdfA3Service service;

    @PostMapping(path = "/attach", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> attachUbl(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestPart("ubl") MultipartFile ubl,
            @RequestParam(name = "ublName", required = false) String ublName)
            throws Exception {
        byte[] out = service.toPdfA3bWithUbl(pdf.getBytes(), ubl.getBytes(), ublName);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(
                ContentDisposition.attachment().filename("pdfa3-with-ubl.pdf").build());
        return new ResponseEntity<>(out, h, HttpStatus.OK);
    }

    @PostMapping(
            path = "/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Object validate(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam(name = "mode", required = false) String mode)
            throws Exception {
        // default: heuristic (fast)
        if (mode != null && "strict".equalsIgnoreCase(mode)) {
            var sr = service.validatePdfAStrict(pdf.getBytes());
            Map<String, Object> out = new HashMap<>();
            out.put("mode", "strict");
            out.put("isValid", sr.isValid);
            out.put("errorCount", sr.getErrorCount());
            out.put("errors", sr.errors);
            return out;
        } else {
            var vr = service.validatePdfA(pdf.getBytes());
            Map<String, Object> out = new HashMap<>();
            out.put("mode", "heuristic");
            out.put("isValid", vr.isValid());
            out.put("details", vr.details());
            return out;
        }
    }

    @PostMapping(
            path = "/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> attachments(@RequestPart("pdf") MultipartFile pdf) throws Exception {
        var list = service.listAttachments(pdf.getBytes());
        return ResponseEntity.ok(Map.of("count", list.size(), "attachments", list));
    }

    // Dev-only diagnostics: dumps Catalog/Names/AF COS structures to help debug Preflight
    // complaints about /EmbeddedFile. Remove or guard in production.
    @PostMapping(
            path = "/debug",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> debugDump(@RequestPart("pdf") MultipartFile pdf)
            throws Exception {
        String dump = service.debugDumpPdfaStructures(pdf.getBytes());
        return ResponseEntity.ok(dump);
    }
}

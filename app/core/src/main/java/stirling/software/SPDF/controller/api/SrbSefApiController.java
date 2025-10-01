package stirling.software.SPDF.controller.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.SrbSefService;
import stirling.software.SPDF.service.SrbUblService;

@RestController
@RequestMapping("/api/sef")
@RequiredArgsConstructor
public class SrbSefApiController {

    private final SrbUblService ublService;
    private final SrbSefService sefService;

    @PostMapping(
            path = "/parse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> parse(@RequestPart("ubl") MultipartFile ubl) throws Exception {
        return ublService.parseInvoiceSummary(ubl.getBytes());
    }

    @PostMapping(
            path = "/draft",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> draft(@RequestPart("ubl") MultipartFile ubl) throws Exception {
        return sefService.draft(ubl.getBytes());
    }

    @PostMapping(path = "/submit", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> submit(@RequestParam("draftId") String draftId) throws Exception {
        return sefService.submit(draftId);
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> status(@RequestParam("id") String id) {
        return sefService.status(id);
    }

    @GetMapping(path = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> cancel(@RequestParam("id") String id) {
        return sefService.cancel(id);
    }

    @PostMapping(path = "/storno", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public Map<String, Object> storno(@RequestParam("id") String id) {
        return sefService.storno(id);
    }
}

package stirling.software.SPDF.controller.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.PrescriptionOcrService;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class PrescriptionOcrApiController {

    private final PrescriptionOcrService service;

    @PostMapping(
            path = "/prescription",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PrescriptionOcrService.Result ocrPrescription(@RequestPart("file") MultipartFile file)
            throws Exception {
        return service.extract(file.getBytes(), file.getOriginalFilename());
    }

    @PostMapping(
            path = "/prescription/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody byte[] ocrPrescriptionPreview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "dpi", required = false, defaultValue = "150") float dpi)
            throws Exception {
        return service.renderZonePreview(file.getBytes(), file.getOriginalFilename(), dpi);
    }
}

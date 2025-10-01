package stirling.software.SPDF.controller.api;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.config.EndpointConfiguration;
import stirling.software.SPDF.service.PrescriptionOcrService;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class PrescriptionOcrApiController {

    private final PrescriptionOcrService service;
    private final EndpointConfiguration endpointConfiguration;

    @PostMapping(
            path = "/prescription",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PrescriptionOcrService.Result ocrPrescription(@RequestPart("file") MultipartFile file)
            throws Exception {
        if (!endpointConfiguration.isGroupEnabled("tesseract")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tesseract OCR nije instaliran ili je onemogućen na ovom sistemu.");
        }
        try {
            return service.extract(file.getBytes(), file.getOriginalFilename());
        } catch (Throwable t) {
            // Zahvati i native/JNA greške i vrati user-friendly poruku
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Greška prilikom pokretanja Tesseract OCR-a (native biblioteke).",
                    t);
        }
    }

    @PostMapping(
            path = "/prescription/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody byte[] ocrPrescriptionPreview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "dpi", required = false, defaultValue = "150") float dpi)
            throws Exception {
        if (!endpointConfiguration.isGroupEnabled("tesseract")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tesseract OCR nije instaliran ili je onemogućen na ovom sistemu.");
        }
        try {
            return service.renderZonePreview(file.getBytes(), file.getOriginalFilename(), dpi);
        } catch (Throwable t) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Greška prilikom pokretanja Tesseract OCR-a (native biblioteke).",
                    t);
        }
    }
}

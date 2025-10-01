package stirling.software.SPDF.controller.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.SrbIpsQrOverlayService;

@RestController
@RequestMapping("/api/ipsqr/pdf")
@RequiredArgsConstructor
public class SrbIpsQrController {

    private final SrbIpsQrOverlayService overlayService;

    @PostMapping(path = "/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlay(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam String iban,
            @RequestParam String recipient,
            @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "RSD") String currency,
            @RequestParam(required = false) String purpose,
            @RequestParam(defaultValue = "97") String model,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Integer pageIndex,
            @RequestParam(required = false) String corner,
            @RequestParam(defaultValue = "180") float sizePt,
            @RequestParam(defaultValue = "36") float marginX,
            @RequestParam(defaultValue = "36") float marginY)
            throws Exception {
        byte[] out =
                overlayService.overlayIpsQr(
                        pdf.getBytes(),
                        iban,
                        recipient,
                        amount,
                        currency,
                        purpose,
                        model,
                        reference,
                        pageIndex,
                        corner,
                        sizePt,
                        marginX,
                        marginY);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(ContentDisposition.attachment().filename("with-ipsqr.pdf").build());
        return ResponseEntity.ok().headers(h).body(out);
    }

    @PostMapping(path = "/overlay-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlayBatch(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam String iban,
            @RequestParam String recipient,
            @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "RSD") String currency,
            @RequestParam(required = false) String purpose,
            @RequestParam(defaultValue = "97") String model,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "last") String pagesSpec,
            @RequestParam(required = false) String corner,
            @RequestParam(defaultValue = "180") float sizePt,
            @RequestParam(defaultValue = "36") float marginX,
            @RequestParam(defaultValue = "36") float marginY)
            throws Exception {
        byte[] out =
                overlayService.overlayIpsQrBatch(
                        pdf.getBytes(),
                        iban,
                        recipient,
                        amount,
                        currency,
                        purpose,
                        model,
                        reference,
                        pagesSpec,
                        corner,
                        sizePt,
                        marginX,
                        marginY);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(ContentDisposition.attachment().filename("with-ipsqr.pdf").build());
        return ResponseEntity.ok().headers(h).body(out);
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> preview(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam String iban,
            @RequestParam String recipient,
            @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "RSD") String currency,
            @RequestParam(required = false) String purpose,
            @RequestParam(defaultValue = "97") String model,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Integer pageIndex,
            @RequestParam(required = false) String corner,
            @RequestParam(defaultValue = "180") float sizePt,
            @RequestParam(defaultValue = "36") float marginX,
            @RequestParam(defaultValue = "36") float marginY,
            @RequestParam(defaultValue = "144") float dpi)
            throws Exception {
        byte[] png =
                overlayService.renderIpsQrPreview(
                        pdf.getBytes(),
                        iban,
                        recipient,
                        amount,
                        currency,
                        purpose,
                        model,
                        reference,
                        pageIndex,
                        corner,
                        sizePt,
                        marginX,
                        marginY,
                        dpi);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(h).body(png);
    }

    @PostMapping(path = "/page", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> page(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestParam(required = false) Integer pageIndex,
            @RequestParam(defaultValue = "144") float dpi)
            throws Exception {
        byte[] png = overlayService.renderPagePng(pdf.getBytes(), pageIndex, dpi);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(h).body(png);
    }
}

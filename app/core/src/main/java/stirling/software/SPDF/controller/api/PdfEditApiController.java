package stirling.software.SPDF.controller.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.PdfEditService;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfEditApiController {

    private final PdfEditService editService;

    /** mode=pdf (replacementPdf) ili mode=image (image). targetPage/sourcePage su 1-based. */
    @PostMapping(path = "/page/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> replacePage(
            @RequestParam(defaultValue = "pdf") String mode,
            @RequestParam int targetPage,
            @RequestParam(defaultValue = "false") boolean keepSize,
            @RequestParam(required = false) Integer sourcePage,
            @RequestParam(defaultValue = "contain") String fit,
            @RequestPart("pdf") MultipartFile pdf,
            @RequestPart(value = "replacementPdf", required = false) MultipartFile replacementPdf,
            @RequestPart(value = "image", required = false) MultipartFile image)
            throws Exception {

        byte[] out;
        if ("image".equalsIgnoreCase(mode)) {
            if (image == null)
                throw new IllegalArgumentException("Nedostaje 'image' za mode=image");
            out =
                    editService.replacePageWithImage(
                            pdf.getBytes(), image.getBytes(), targetPage, fit);
        } else {
            if (replacementPdf == null)
                throw new IllegalArgumentException("Nedostaje 'replacementPdf' za mode=pdf");
        if (keepSize) {
                out =
                        editService.replacePageWithPdfKeepSize(
                pdf.getBytes(), replacementPdf.getBytes(), targetPage, sourcePage, fit);
            } else {
                out =
                        editService.replacePageWithPdf(
                                pdf.getBytes(), replacementPdf.getBytes(), targetPage, sourcePage);
            }
        }

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(ContentDisposition.attachment().filename("replaced.pdf").build());
        return ResponseEntity.ok().headers(h).body(out);
    }
}

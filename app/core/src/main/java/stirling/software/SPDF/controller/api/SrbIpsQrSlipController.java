package stirling.software.SPDF.controller.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.SrbIpsQrSlipService;

@RestController
@RequestMapping("/api/ipsqr/slip")
@RequiredArgsConstructor
public class SrbIpsQrSlipController {

    private final SrbIpsQrSlipService slipService;

    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generate(
            @RequestParam String iban,
            @RequestParam String recipient,
            @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "RSD") String currency,
            @RequestParam(required = false) String purpose,
            @RequestParam(defaultValue = "97") String model,
            @RequestParam(required = false) String reference)
            throws Exception {

        byte[] pdf =
                slipService.generateSlip(
                        iban, recipient, amount, currency, purpose, model, reference);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(
                ContentDisposition.attachment().filename("ips-qr-slip.pdf").build());
        return ResponseEntity.ok().headers(h).body(pdf);
    }
}

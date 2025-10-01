package stirling.software.SPDF.controller.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import stirling.software.SPDF.service.SrbCurrencyService;

@RestController
@RequestMapping("/api/kurs")
@RequiredArgsConstructor
public class SrbCurrencyApiController {

    private final SrbCurrencyService currencyService;

    @GetMapping(path = "/danas", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Double> today(@RequestParam(required = false) String date) throws Exception {
        LocalDate d = (date == null || date.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        return currencyService.getNbsDailyRates(d);
    }
}

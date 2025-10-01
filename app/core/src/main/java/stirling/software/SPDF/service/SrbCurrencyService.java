package stirling.software.SPDF.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SrbCurrencyService {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    // NBS daily exchange rates (JSON): https://api.nbs.rs/od/statistika/kursneListe?date=YYYY-MM-DD
    public Map<String, Double> getNbsDailyRates(LocalDate date) throws IOException, InterruptedException {
        String d = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = "https://api.nbs.rs/od/statistika/kursneListe?date=" + d;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json").build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("NBS API status: " + resp.statusCode());
        }
        JsonNode root = om.readTree(resp.body());
        // Expecting an array of objects with fields like: "valuta": "EUR", "srednjiKurs": "117.12"
        Map<String, Double> out = new LinkedHashMap<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                String code = n.path("valuta").asText(null);
                String srednji = n.path("srednjiKurs").asText(null);
                if (code != null && srednji != null) {
                    // Replace comma with dot if present
                    String norm = srednji.replace(',', '.');
                    try {
                        out.put(code, Double.parseDouble(norm));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }
        return out;
    }
}

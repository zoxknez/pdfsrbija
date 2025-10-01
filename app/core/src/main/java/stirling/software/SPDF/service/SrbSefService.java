package stirling.software.SPDF.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.config.SefProperties;

@Service
@RequiredArgsConstructor
public class SrbSefService {

    private final SefProperties props;

    // PdfA3Service can be injected later for automatic PDF/A attachment workflows

    public Map<String, Object> draft(byte[] ublXml) throws Exception {
        // Mapirano na: POST {baseUrl}/api/publicApi/sales-invoice/ubl
        ensureUblLooksValid(ublXml);
        if (props.isMock()) {
            return Map.of(
                    "mode",
                    "mock",
                    "id",
                    UUID.randomUUID().toString(),
                    "status",
                    "DRAFT_CREATED",
                    "timestamp",
                    Instant.now().toString());
        }
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = buildAuthHeaders();
        h.setContentType(MediaType.APPLICATION_XML);
        String url = normalize(props.getBaseUrl()) + props.getApiPrefix() + "/sales-invoice/ubl";
        RequestEntity<byte[]> req = RequestEntity.post(url).headers(h).body(ublXml);
        ResponseEntity<Map<String, Object>> resp;
        try {
            resp = rt.exchange(req, new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception primaryEx) {
            // Fallback na upload endpoint ako direktan XML nije podržan
            try {
                org.springframework.util.LinkedMultiValueMap<String, Object> mv =
                        new org.springframework.util.LinkedMultiValueMap<>();
                org.springframework.core.io.ByteArrayResource br =
                        new org.springframework.core.io.ByteArrayResource(ublXml) {
                            @Override
                            public String getFilename() {
                                return "invoice.xml";
                            }
                        };
                mv.add("file", br);
                HttpHeaders mh = buildAuthHeaders();
                mh.setContentType(MediaType.MULTIPART_FORM_DATA);
                String upUrl =
                        normalize(props.getBaseUrl())
                                + props.getApiPrefix()
                                + "/sales-invoice/ubl/upload";
                RequestEntity<org.springframework.util.MultiValueMap<String, Object>> upReq =
                        RequestEntity.post(upUrl).headers(mh).body(mv);
                resp = rt.exchange(upReq, new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception uploadEx) {
                throw primaryEx; // prijavi originalni neuspeh
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "live");
        out.put("statusCode", resp.getStatusCode().value());
        out.put("body", resp.getBody());
        // Pokušaj da izvučemo ID ako ga API vraća pod raznim ključevima
        Object body = resp.getBody();
        if (body instanceof Map<?, ?> m) {
            Object id = null;
            if (m.containsKey("id")) id = m.get("id");
            else if (m.containsKey("invoiceId")) id = m.get("invoiceId");
            else if (m.containsKey("uuid")) id = m.get("uuid");
            if (id != null) out.put("id", id);
        }
        return out;
    }

    public Map<String, Object> submit(String draftId) {
        // Mapirano na: POST {baseUrl}/api/publicApi/sales-invoice/send
        if (props.isMock()) {
            return Map.of(
                    "mode",
                    "mock",
                    "id",
                    draftId,
                    "status",
                    "SUBMITTED",
                    "timestamp",
                    Instant.now().toString());
        }
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = buildAuthHeaders();
        String url = normalize(props.getBaseUrl()) + props.getApiPrefix() + "/sales-invoice/send";
        // Neki API-jevi očekuju JSON telo sa ID, drugi query; ovde koristimo JSON {"id": draftId}
        Map<String, String> body = Map.of("id", draftId);
        RequestEntity<Map<String, String>> req =
                RequestEntity.post(url)
                        .headers(h)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
        ResponseEntity<Map<String, Object>> resp;
        try {
            resp = rt.exchange(req, new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception primaryEx) {
            // Fallback: some APIs expect {"uuid": ...}
            Map<String, String> alt = Map.of("uuid", draftId);
            RequestEntity<Map<String, String>> altReq =
                    RequestEntity.post(url)
                            .headers(h)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(alt);
            try {
                resp =
                        rt.exchange(
                                altReq, new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception jsonEx) {
                // Fallback: try query param ?id=
                String qUrl = url + "?id=" + urlEncode(draftId);
                RequestEntity<Void> qReq = RequestEntity.post(qUrl).headers(h).build();
                resp = rt.exchange(qReq, new ParameterizedTypeReference<Map<String, Object>>() {});
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "live");
        out.put("statusCode", resp.getStatusCode().value());
        out.put("body", resp.getBody());
        return out;
    }

    public Map<String, Object> status(String invoiceId) {
        // Mapirano na: GET {baseUrl}/api/publicApi/sales-invoice?id=<invoiceId>
        if (props.isMock()) {
            return Map.of(
                    "mode",
                    "mock",
                    "id",
                    invoiceId,
                    "status",
                    "ACCEPTED",
                    "timestamp",
                    Instant.now().toString());
        }
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = buildAuthHeaders();
        String url =
                normalize(props.getBaseUrl())
                        + props.getApiPrefix()
                        + "/sales-invoice?id="
                        + urlEncode(invoiceId);
        RequestEntity<Void> req = RequestEntity.get(url).headers(h).build();
        ResponseEntity<Map<String, Object>> resp =
                rt.exchange(req, new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "live");
        out.put("statusCode", resp.getStatusCode().value());
        out.put("body", resp.getBody());
        return out;
    }

    public Map<String, Object> cancel(String invoiceId) {
        // Mapirano na: GET {baseUrl}/api/publicApi/sales-invoice/cancel?id=<invoiceId>
        if (props.isMock()) {
            return Map.of(
                    "mode",
                    "mock",
                    "id",
                    invoiceId,
                    "status",
                    "CANCELLED",
                    "timestamp",
                    Instant.now().toString());
        }
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = buildAuthHeaders();
        String url =
                normalize(props.getBaseUrl())
                        + props.getApiPrefix()
                        + "/sales-invoice/cancel?id="
                        + urlEncode(invoiceId);
        RequestEntity<Void> req = RequestEntity.get(url).headers(h).build();
        ResponseEntity<Map<String, Object>> resp =
                rt.exchange(req, new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "live");
        out.put("statusCode", resp.getStatusCode().value());
        out.put("body", resp.getBody());
        return out;
    }

    public Map<String, Object> storno(String invoiceId) {
        // Mapirano na: POST {baseUrl}/api/publicApi/sales-invoice/storno
        if (props.isMock()) {
            return Map.of(
                    "mode",
                    "mock",
                    "id",
                    invoiceId,
                    "status",
                    "STORNO_REQUESTED",
                    "timestamp",
                    Instant.now().toString());
        }
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = buildAuthHeaders();
        String url = normalize(props.getBaseUrl()) + props.getApiPrefix() + "/sales-invoice/storno";
        Map<String, String> body = Map.of("id", invoiceId);
        RequestEntity<Map<String, String>> req =
                RequestEntity.post(url)
                        .headers(h)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
        ResponseEntity<Map<String, Object>> resp;
        try {
            resp = rt.exchange(req, new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception jsonEx) {
            // Fallback: query param ?id=
            String qUrl = url + "?id=" + urlEncode(invoiceId);
            RequestEntity<Void> qReq = RequestEntity.post(qUrl).headers(h).build();
            resp = rt.exchange(qReq, new ParameterizedTypeReference<Map<String, Object>>() {});
        }
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "live");
        out.put("statusCode", resp.getStatusCode().value());
        out.put("body", resp.getBody());
        return out;
    }

    private void ensureUblLooksValid(byte[] xml) throws Exception {
        // Minimalna provera: da li sadrži <Invoice i </Invoice
        String s = new String(xml, StandardCharsets.UTF_8);
        if (!s.contains("<Invoice") || !s.contains("</Invoice")) {
            throw new IllegalArgumentException("Nije prepoznat UBL Invoice");
        }
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders h = new HttpHeaders();
        String token = props.getBearerToken();
        if (token != null && !token.isBlank()) {
            if (props.getAuthScheme() != null && !props.getAuthScheme().isBlank()) {
                h.set(HttpHeaders.AUTHORIZATION, props.getAuthScheme() + " " + token);
            } else if (props.getAuthHeaderName() != null && !props.getAuthHeaderName().isBlank()) {
                h.set(props.getAuthHeaderName(), token);
            } else {
                h.setBearerAuth(token);
            }
        }
        return h;
    }

    private static String normalize(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String urlEncode(String v) {
        try {
            return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return v;
        }
    }
}

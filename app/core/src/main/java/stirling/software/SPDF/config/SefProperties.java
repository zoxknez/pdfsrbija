package stirling.software.SPDF.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "srb.sef")
public class SefProperties {
    /** Base URL za SEF API (npr. https://demoefaktura... ili https://efaktura...). */
    private String baseUrl;

    /** API ključ ili token za autentikaciju. */
    private String bearerToken;

    /** Ako je true, servis radi u mock režimu bez mrežnih poziva. */
    private boolean mock = true;

    /** Prefiks javnog API-ja (podrazumevano: /api/publicApi). */
    private String apiPrefix = "/api/publicApi";

    /** Naziv header-a za autentikaciju (npr. X-API-KEY ili Authorization). */
    private String authHeaderName = "X-API-KEY";

    /**
     * Shema za Authorization header (npr. Bearer). Ako je postavljena koristi se "Authorization:
     * <schema> <token>".
     */
    private String authScheme;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }
}

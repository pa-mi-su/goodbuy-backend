package app.goodbuy.adapters.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds properties under "goodbuy.catalog".
 *
 * Example:
 *   goodbuy.catalog.enabled=true
 *   goodbuy.catalog.provider=eandb
 *   goodbuy.catalog.eandb.base-url=https://ean-db.com/api/v2
 *   goodbuy.catalog.eandb.jwt=*** (from application-secrets.properties)
 */
@Validated
@ConfigurationProperties(prefix = "goodbuy.catalog")
public class CatalogProperties {

    /** Master enable switch. */
    private boolean enabled = false;

    /** Which external provider to use when enabled. */
    private Provider provider; // e.g., eandb, eansearch

    private EanSearch eansearch = new EanSearch();
    private EanDb eandb = new EanDb();

    // --- getters/setters ---
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public EanSearch getEansearch() { return eansearch; }
    public void setEansearch(EanSearch eansearch) { this.eansearch = eansearch; }

    public EanDb getEandb() { return eandb; }
    public void setEandb(EanDb eandb) { this.eandb = eandb; }

    /** Supported provider values. */
    public enum Provider { eansearch, eandb }

    // ---------- Provider blocks ----------

    @Validated
    public static class EanSearch {
        /** Example: https://www.ean-search.org/api */
        private String baseUrl;

        /** API key for EAN-Search. */
        private String apiKey;

        /** Milliseconds. */
        @Min(100) @Max(30000)
        private int connectTimeoutMs = 2000;

        /** Milliseconds. */
        @Min(100) @Max(30000)
        private int readTimeoutMs = 3000;

        // getters/setters
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    @Validated
    public static class EanDb {
        /** Example: https://ean-db.com/api/v2 */
        private String baseUrl;

        /** Bearer JWT for EAN-DB (keep in application-secrets.properties). */
        private String jwt;

        /** Milliseconds. */
        @Min(100) @Max(30000)
        private int connectTimeoutMs = 2000;

        /** Milliseconds. */
        @Min(100) @Max(30000)
        private int readTimeoutMs = 3000;

        // getters/setters
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getJwt() { return jwt; }
        public void setJwt(String jwt) { this.jwt = jwt; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}

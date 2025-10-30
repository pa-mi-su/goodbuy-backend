package app.goodbuy.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "goodbuy.catalog")
public class CatalogProperties {
    private String provider = "eansearch"; // future-proof switch
    private boolean enabled = false;      // keep OFF by default

    private EanSearch eansearch = new EanSearch();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public EanSearch getEansearch() { return eansearch; }
    public void setEansearch(EanSearch eansearch) { this.eansearch = eansearch; }

    public static class EanSearch {
        private String baseUrl;
        private String apiKey;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 4000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}

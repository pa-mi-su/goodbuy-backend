package app.goodbuy.adapters.core.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "goodbuy.s3")
public class S3StorageProperties {

    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private String endpoint;
    private String publicBaseUrl;

    public String accessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String secretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String region() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String bucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String endpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String publicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
}

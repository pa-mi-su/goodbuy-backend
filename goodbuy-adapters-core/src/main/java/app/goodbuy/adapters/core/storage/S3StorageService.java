package app.goodbuy.adapters.core.storage;

import app.goodbuy.core.storage.ProductImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Thin wrapper over AWS S3 for GoodBuy image uploads.
 *
 * Implements ProductImageStoragePort so controllers can depend on the port.
 */
@Service
public class S3StorageService implements ProductImageStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final String bucket;
    private final String region;
    private final String publicBaseUrl;
    private final boolean enabled;

    public S3StorageService(
            @Value("${goodbuy.s3.enabled:true}") boolean enabled,
            @Value("${goodbuy.s3.accessKey}") String accessKey,
            @Value("${goodbuy.s3.secretKey}") String secretKey,
            @Value("${goodbuy.s3.region}") String region,
            @Value("${goodbuy.s3.bucket}") String bucket,
            @Value("${goodbuy.s3.endpoint:}") String endpoint,
            @Value("${goodbuy.s3.publicBaseUrl:}") String publicBaseUrl
    ) {
        this.enabled = enabled;
        this.bucket = bucket;
        this.region = region;
        this.publicBaseUrl = (publicBaseUrl != null && publicBaseUrl.endsWith("/"))
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;

        if (!enabled) {
            this.s3 = null;
            log.warn("S3StorageService disabled via goodbuy.s3.enabled=false for bucket={}", bucket);
            return;
        }

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds));

        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
            log.info("S3StorageService using custom endpoint={} region={}", endpoint, region);
        } else {
            log.info("S3StorageService using default AWS endpoint for region={}", region);
        }

        this.s3 = builder.build();
        log.info("S3StorageService initialized for bucket={}", bucket);
    }

    /**
     * Upload a public-ish object to S3 and return the URL.
     * No ACLs are used – bucket policy handles public access.
     */
    @Override
    public String uploadImage(String key, byte[] bytes, String contentType) {
        if (!enabled) {
            log.info("S3StorageService.uploadImage: skipped because S3 is disabled bucket={} key={}", bucket, key);
            return null;
        }

        int byteCount = (bytes == null ? 0 : bytes.length);

        log.debug("S3StorageService.uploadImage: bucket={} region={} key={} bytes={} contentType={}",
                bucket, region, key, byteCount, contentType);

        try {
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);

            if (contentType != null && !contentType.isBlank()) {
                reqBuilder = reqBuilder.contentType(contentType);
            }

            PutObjectRequest req = reqBuilder.build();

            log.debug("S3StorageService.uploadImage: calling s3.putObject bucket={} key={}", bucket, key);
            s3.putObject(req, RequestBody.fromBytes(bytes));

            String url = buildPublicUrl(key);
            log.info("S3StorageService.uploadImage: success bucket={} key={} bytes={} url={}",
                    bucket, key, byteCount, url);

            return url;
        } catch (Exception ex) {
            log.error("S3StorageService.uploadImage: failed bucket={} key={}", bucket, key, ex);
            throw ex; // Let the caller handle/log as well
        }
    }

    private String buildPublicUrl(String key) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl + "/" + key;
        }
        // Standard AWS S3 URL pattern
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
    }
}

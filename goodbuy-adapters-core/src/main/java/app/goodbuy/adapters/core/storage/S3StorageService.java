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

    public S3StorageService(
            @Value("${goodbuy.s3.accessKey}") String accessKey,
            @Value("${goodbuy.s3.secretKey}") String secretKey,
            @Value("${goodbuy.s3.region}") String region,
            @Value("${goodbuy.s3.bucket}") String bucket,
            @Value("${goodbuy.s3.endpoint:}") String endpoint,
            @Value("${goodbuy.s3.publicBaseUrl:}") String publicBaseUrl
    ) {
        this.bucket = bucket;
        this.region = region;
        this.publicBaseUrl = (publicBaseUrl != null && publicBaseUrl.endsWith("/"))
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds));

        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
            log.info("S3StorageService using custom endpoint={}", endpoint);
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

        int byteCount = (bytes == null ? 0 : bytes.length);

        log.error("🔊 S3StorageService.uploadImage: START bucket={} region={} key={} bytes={} contentType={}",
                bucket, region, key, byteCount, contentType);

        // Log whether we have creds (BUT NOT THE VALUES)
        log.error("🔊 S3StorageService.creds: accessKey_present={} secretKey_present={}",
                (s3 != null),  // s3 is built using creds; but log anyway
                "hidden");      // redact

        try {
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);

            if (contentType != null && !contentType.isBlank()) {
                reqBuilder = reqBuilder.contentType(contentType);
            }

            PutObjectRequest req = reqBuilder.build();

            log.error("🔊 S3StorageService: calling s3.putObject bucket={} key={} ...", bucket, key);

            s3.putObject(req, RequestBody.fromBytes(bytes));

            log.error("🔊 S3StorageService: SUCCESS bucket={} key={}", bucket, key);

        } catch (Exception ex) {
            log.error("❌ S3StorageService.ERROR bucket={} key={} msg={} stack={}",
                    bucket, key, ex.getMessage(), ex.toString());
            throw ex; // Let the caller log too
        }

        // URL generation logging
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String url = publicBaseUrl + "/" + key;
            log.error("🔊 S3StorageService: FINAL_URL={}", url);
            return url;
        }

        String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
        log.error("🔊 S3StorageService: FINAL_URL(default)={}", url);
        return url;
    }
}

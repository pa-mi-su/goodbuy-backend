package app.goodbuy.adapters.core.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class TextractConfig {

    @Bean
    @ConditionalOnProperty(name = "goodbuy.ocr.provider", havingValue = "textract")
    public TextractClient textractClient(
            @Value("${goodbuy.ocr.aws.region:${AWS_REGION:us-east-1}}") String region,
            @Value("${goodbuy.ocr.aws.accessKey:${GOODBUY_OCR_AWS_ACCESS_KEY:}}") String accessKey,
            @Value("${goodbuy.ocr.aws.secretKey:${GOODBUY_OCR_AWS_SECRET_KEY:}}") String secretKey
    ) {
        var builder = TextractClient.builder()
                .region(Region.of(region));

        if (hasText(accessKey) && hasText(secretKey)) {
            builder = builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())
                    )
            );
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

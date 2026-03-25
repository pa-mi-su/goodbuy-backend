package app.goodbuy.adapters.core.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class SesConfig {

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    @Value("${goodbuy.auth.magic-login.aws.accessKey:${GOODBUY_SES_ACCESS_KEY:}}")
    private String accessKey;

    @Value("${goodbuy.auth.magic-login.aws.secretKey:${GOODBUY_SES_SECRET_KEY:}}")
    private String secretKey;

    @Bean
    public SesClient sesClient() {
        var builder = SesClient.builder()
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

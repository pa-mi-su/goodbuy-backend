package app.goodbuy.adapters.core.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class TextractConfig {

    @Bean
    @ConditionalOnProperty(name = "goodbuy.ocr.provider", havingValue = "textract")
    public TextractClient textractClient(
            @Value("${goodbuy.ocr.aws.region:${AWS_REGION:us-east-1}}") String region
    ) {
        return TextractClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

package app.goodbuy.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI goodBuyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GoodBuy API")
                        .description("""
                                Backend for the GoodBuy iOS app.
                                v1 exposes a simple product lookup by GTIN.
                                Even-length GTIN → demo product; odd-length → 404 (not found).
                                """)
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("GoodBuy")
                                .url("https://example.com")
                                .email("support@example.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("README")
                        .url("https://github.com/your-user/goodbuy-backend"));
    }
}

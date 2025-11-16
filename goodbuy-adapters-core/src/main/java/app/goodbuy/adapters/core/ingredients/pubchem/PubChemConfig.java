package app.goodbuy.adapters.core.ingredients.pubchem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubChemConfig {

    @Bean
    public PubChemClient pubChemClient(
            @Value("${goodbuy.pubchem.base-url:https://pubchem.ncbi.nlm.nih.gov/rest/pug}")
            String baseUrl,
            @Value("${goodbuy.pubchem.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @Value("${goodbuy.pubchem.read-timeout-ms:3000}")
            int readTimeoutMs
    ) {
        // uses the constructor you pasted
        return new PubChemClient(baseUrl, connectTimeoutMs, readTimeoutMs);
    }
}

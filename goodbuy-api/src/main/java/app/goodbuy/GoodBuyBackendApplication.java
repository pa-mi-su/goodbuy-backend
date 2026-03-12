package app.goodbuy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GoodBuyBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoodBuyBackendApplication.class, args);
    }
}

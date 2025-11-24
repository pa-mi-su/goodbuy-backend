package app.goodbuy.api;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    private final Environment env;

    public RootController(Environment env) {
        this.env = env;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        String[] profiles = env.getActiveProfiles();
        String active = (profiles.length == 0) ? "default" : String.join(",", profiles);

        return Map.of(
                "app", "goodbuy-backend",
                "env", active,
                "status", "OK"
        );
    }
}

package app.goodbuy.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserRegistrationRequest {

    @NotBlank
    @Email
    private String email;

    private String platform;
    private String appVersion;

    public UserRegistrationRequest() {
    }

    public UserRegistrationRequest(String email, String platform, String appVersion) {
        this.email = email;
        this.platform = platform;
        this.appVersion = appVersion;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }
}

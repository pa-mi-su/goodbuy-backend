package app.goodbuy.users.dto;

public class UserRegistrationResponse {

    private String id;
    private String email;
    private String platform;
    private String appVersion;
    private String lastSeenAt;

    public UserRegistrationResponse() {
    }

    public UserRegistrationResponse(
            String id,
            String email,
            String platform,
            String appVersion,
            String lastSeenAt
    ) {
        this.id = id;
        this.email = email;
        this.platform = platform;
        this.appVersion = appVersion;
        this.lastSeenAt = lastSeenAt;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPlatform() {
        return platform;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getLastSeenAt() {
        return lastSeenAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}

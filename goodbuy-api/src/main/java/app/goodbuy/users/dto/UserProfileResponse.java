package app.goodbuy.users.dto;

public class UserProfileResponse {

    private String id;
    private String email;
    private String platform;
    private String appVersion;
    private String lastSeenAt;

    private long totalScans;
    private long favoritesCount;

    public UserProfileResponse() {
    }

    public UserProfileResponse(
            String id,
            String email,
            String platform,
            String appVersion,
            String lastSeenAt,
            long totalScans,
            long favoritesCount
    ) {
        this.id = id;
        this.email = email;
        this.platform = platform;
        this.appVersion = appVersion;
        this.lastSeenAt = lastSeenAt;
        this.totalScans = totalScans;
        this.favoritesCount = favoritesCount;
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

    public long getTotalScans() {
        return totalScans;
    }

    public long getFavoritesCount() {
        return favoritesCount;
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

    public void setTotalScans(long totalScans) {
        this.totalScans = totalScans;
    }

    public void setFavoritesCount(long favoritesCount) {
        this.favoritesCount = favoritesCount;
    }
}

package haojiwu.flickrtogooglephotos.model;

public class GoogleCredential {
  private final String accessToken;
  private final String refreshToken;

  public GoogleCredential(String accessToken, String refreshToken) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }
}

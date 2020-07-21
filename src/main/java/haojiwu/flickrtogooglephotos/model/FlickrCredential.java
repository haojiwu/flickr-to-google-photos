package haojiwu.flickrtogooglephotos.model;

public class FlickrCredential {
  private final String userId;
  private final String token;
  private final String secret;

  public FlickrCredential(String userId, String token, String secret) {
    this.userId = userId;
    this.token = token;
    this.secret = secret;
  }

  public String getUserId() {
    return userId;
  }

  public String getToken() {
    return token;
  }

  public String getSecret() {
    return secret;
  }
}

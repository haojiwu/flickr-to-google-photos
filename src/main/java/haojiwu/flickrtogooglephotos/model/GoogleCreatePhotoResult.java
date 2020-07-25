package haojiwu.flickrtogooglephotos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.photos.library.v1.proto.NewMediaItem;

public class GoogleCreatePhotoResult {
  public enum Status {
    SUCCESS,
    EXIST_NO_NEED_TO_CREATE,
    EXIST_CAN_NOT_CREATE,
    FAIL
  }
  private Status status;
  private final String sourceId;
  private String googleId;
  private String url;
  private String error;
  private final NewMediaItem newMediaItem;

  private GoogleCreatePhotoResult(Status status, String sourceId,
                                  String error, NewMediaItem newMediaItem, String googleId) {
    this.status = status;
    this.sourceId = sourceId;
    this.error = error;
    this.newMediaItem = newMediaItem;
    this.googleId = googleId;
  }

  public Status getStatus() {
    return status;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getGoogleId() {
    return googleId;
  }

  public String getUrl() {
    return url;
  }

  public String getError() {
    return error;
  }

  @JsonIgnore
  public NewMediaItem getNewMediaItem() {
    return newMediaItem;
  }

  public boolean hasNewMediaItem() {
    return newMediaItem != null;
  }

  public void setGoogleId(String googleId) {
    this.googleId = googleId;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setError(String error) {
    this.error = error;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public static class Builder {
    private Status status;
    private final String sourceId;
    private String error;
    private NewMediaItem newMediaItem;
    private String googleId;

    public Builder(String sourceId) {
      this.sourceId = sourceId;
    }

    public GoogleCreatePhotoResult build() {
      return new GoogleCreatePhotoResult(status, sourceId, error, newMediaItem, googleId);
    }

    public Builder setStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder setError(String error) {
      this.error = error;
      return this;
    }

    public Builder setNewMediaItem(NewMediaItem newMediaItem) {
      this.newMediaItem = newMediaItem;
      return this;
    }

    public Builder setGoogleId(String googleId) {
      this.googleId = googleId;
      return this;
    }
  }


}

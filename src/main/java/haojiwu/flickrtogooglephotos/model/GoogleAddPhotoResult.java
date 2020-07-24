package haojiwu.flickrtogooglephotos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.photos.library.v1.proto.NewMediaItem;

public class GoogleAddPhotoResult {
  public enum Status {
    SUCCESS,
    EXISTING,
    ERROR
  }
  private Status status;
  private final String sourceId;
  private String googleId;
  private String url;
  private String error;
  private final NewMediaItem newMediaItem;

  private GoogleAddPhotoResult(Status status, String sourceId,
                               String error, NewMediaItem newMediaItem) {
    this.status = status;
    this.sourceId = sourceId;
    this.error = error;
    this.newMediaItem = newMediaItem;
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

  public boolean hasGoogleId() {
    return googleId != null;
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

    public Builder(String sourceId) {
      this.sourceId = sourceId;
    }

    public GoogleAddPhotoResult build() {
      return new GoogleAddPhotoResult(status, sourceId, error, newMediaItem);
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
  }


}

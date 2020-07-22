package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrAlbum {
  private final String id;
  private final String title;
  private final String description;
  private final String url;
  private final String coverPhotoId;
  private final List<String> photoIds;

  private FlickrAlbum(String id, String title, String description, String url, String coverPhotoId, List<String> photoIds) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.url = url;
    this.coverPhotoId = coverPhotoId;
    this.photoIds = photoIds;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public String getCoverPhotoId() {
    return coverPhotoId;
  }

  public List<String> getPhotoIds() {
    return photoIds;
  }

  public static class Builder {
    private String id;
    private String title;
    private String description;
    private String url;
    private String coverPhotoId;
    private List<String> photoIds;

    public Builder() {
    }

    public FlickrAlbum build() {
      return new FlickrAlbum(id, title, description, url, coverPhotoId, photoIds);
    }

    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    public Builder setCoverPhotoId(String coverPhotoId) {
      this.coverPhotoId = coverPhotoId;
      return this;
    }

    public Builder setPhotoIds(List<String> photoIds) {
      this.photoIds = photoIds;
      return this;
    }
  }
}

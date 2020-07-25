package haojiwu.flickrtogooglephotos.model;

import com.google.common.base.MoreObjects;

import java.util.List;

public class FlickrPhoto {
  private final String id;
  private final String flickrUrl;
  private final String photoUrl;
  private final String title;
  private final String description;
  private final Float latitude;
  private final Float longitude;
  private final List<String> tags;
  private final Media media;

  public enum Media {
    PHOTO,
    VIDEO
  }

  private FlickrPhoto(String id, String flickrUrl, String photoUrl, String title,
                     String description, Float latitude, Float longitude, List<String> tags, Media media) {
    this.id = id;
    this.flickrUrl = flickrUrl;
    this.photoUrl = photoUrl;
    this.title = title;
    this.description = description;
    this.latitude = latitude;
    this.longitude = longitude;
    this.tags = tags;
    this.media = media;
  }

  public String getId() {
    return id;
  }

  public String getFlickrUrl() {
    return flickrUrl;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Float getLatitude() {
    return latitude;
  }

  public Float getLongitude() {
    return longitude;
  }

  public List<String> getTags() {
    return tags;
  }

  public Media getMedia() {
    return media;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("flickrUrl", flickrUrl)
            .add("photoUrl", photoUrl)
            .add("title", title)
            .add("description", description)
            .add("latitude", latitude)
            .add("longitude", longitude)
            .add("tags", tags)
            .add("media", media)
            .toString();
  }

  public static class Builder {
    private String id;
    private String flickrUrl;
    private String photoUrl;
    private String title;
    private String description;
    private Float latitude;
    private Float longitude;
    private List<String> tags;
    private Media media;

    public Builder() {
    }

    public FlickrPhoto build() {
      return new FlickrPhoto(id, flickrUrl, photoUrl, title, description, latitude, longitude, tags, media);
    }

    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    public Builder setFlickrUrl(String flickrUrl) {
      this.flickrUrl = flickrUrl;
      return this;
    }

    public Builder setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
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

    public Builder setLatitude(Float latitude) {
      this.latitude = latitude;
      return this;
    }

    public Builder setLongitude(Float longitude) {
      this.longitude = longitude;
      return this;
    }

    public Builder setTags(List<String> tags) {
      this.tags = tags;
      return this;
    }

    public Builder setMedia(Media media) {
      this.media = media;
      return this;
    }
  }
}

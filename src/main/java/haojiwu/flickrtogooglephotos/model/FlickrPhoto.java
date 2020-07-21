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

  public FlickrPhoto(String id, String flickrUrl, String photoUrl, String title, String description, Float latitude, Float longitude, List<String> tags) {
    this.id = id;
    this.flickrUrl = flickrUrl;
    this.photoUrl = photoUrl;
    this.title = title;
    this.description = description;
    this.latitude = latitude;
    this.longitude = longitude;
    this.tags = tags;
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
            .toString();
  }
}

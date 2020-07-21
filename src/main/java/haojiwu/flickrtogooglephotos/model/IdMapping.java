package haojiwu.flickrtogooglephotos.model;

import javax.persistence.*;

@Entity
public class IdMapping {
  @EmbeddedId
  private FlickrId flickrId;
  private String googleId;

  public IdMapping() {
  }

  public IdMapping(FlickrId flickrId, String googleId) {
    this.flickrId = flickrId;
    this.googleId = googleId;
  }

  public FlickrId getFlickrId() {
    return flickrId;
  }

  public String getGoogleId() {
    return googleId;
  }
}
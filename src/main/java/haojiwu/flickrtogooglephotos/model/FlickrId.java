package haojiwu.flickrtogooglephotos.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.persistence.Embeddable;
import java.io.Serializable;


@Embeddable
public class FlickrId implements Serializable {
  private String flickrUserId;
  private String flickrEntityId;

  public FlickrId() {

  }

  public FlickrId(String flickrUserId, String flickrEntityId) {
    this.flickrUserId = flickrUserId;
    this.flickrEntityId = flickrEntityId;
  }

  public String getFlickrUserId() {
    return flickrUserId;
  }

  public String getFlickrEntityId() {
    return flickrEntityId;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("flickrUserId", flickrUserId)
            .add("flickrEntityId", flickrEntityId)
            .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FlickrId flickrId = (FlickrId) o;
    return Objects.equal(flickrUserId, flickrId.flickrUserId) &&
            Objects.equal(flickrEntityId, flickrId.flickrEntityId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(flickrUserId, flickrEntityId);
  }
}
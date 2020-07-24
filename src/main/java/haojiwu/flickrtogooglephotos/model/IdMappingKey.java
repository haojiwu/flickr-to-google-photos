package haojiwu.flickrtogooglephotos.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.persistence.Embeddable;
import java.io.Serializable;


@Embeddable
public class IdMappingKey implements Serializable {
  private String userId;
  private String sourceId;

  public IdMappingKey() {

  }

  public IdMappingKey(String userId, String sourceId) {
    this.userId = userId;
    this.sourceId = sourceId;
  }

  public String getUserId() {
    return userId;
  }

  public String getSourceId() {
    return sourceId;
  }


  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("userId", userId)
            .add("sourceEntityId", sourceId)
            .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdMappingKey that = (IdMappingKey) o;
    return Objects.equal(userId, that.userId) &&
            Objects.equal(sourceId, that.sourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(userId, sourceId);
  }
}
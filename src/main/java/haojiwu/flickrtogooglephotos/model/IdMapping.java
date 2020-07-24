package haojiwu.flickrtogooglephotos.model;

import javax.persistence.*;

@Entity
public class IdMapping {
  @EmbeddedId
  private IdMappingKey idMappingKey;
  private String targetId;

  public IdMapping() {
  }

  public IdMapping(IdMappingKey idMappingKey, String targetId) {
    this.idMappingKey = idMappingKey;
    this.targetId = targetId;
  }

  public IdMappingKey getIdMappingKey() {
    return idMappingKey;
  }

  public String getTargetId() {
    return targetId;
  }
}
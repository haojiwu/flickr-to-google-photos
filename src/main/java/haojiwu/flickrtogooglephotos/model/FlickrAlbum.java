package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrAlbum {
  private final String id;
  private final String title;
  private final String description;
  private final String url;
  private final String coverPhotoId;
  private final List<String> photoIds;

  public FlickrAlbum(String id, String title, String description, String url, String coverPhotoId, List<String> photoIds) {
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
}

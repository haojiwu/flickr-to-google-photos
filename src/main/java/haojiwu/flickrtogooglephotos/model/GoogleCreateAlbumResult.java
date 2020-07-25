package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class GoogleCreateAlbumResult {
  private final String id;
  private final String url;
  private final long itemCount;
  private final List<String> failedSourcePhotoIds;

  public GoogleCreateAlbumResult(String id, String url, long itemCount, List<String> failedSourcePhotoIds) {
    this.id = id;
    this.url = url;
    this.itemCount = itemCount;
    this.failedSourcePhotoIds = failedSourcePhotoIds;
  }

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }

  public long getItemCount() {
    return itemCount;
  }

  public List<String> getFailedSourcePhotoIds() {
    return failedSourcePhotoIds;
  }
}

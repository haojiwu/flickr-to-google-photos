package haojiwu.flickrtogooglephotos.model;

public class GoogleCreateAlbumResult {
  private final String id;
  private final String url;
  private final long itemCount;

  public GoogleCreateAlbumResult(String id, String url, long itemCount) {
    this.id = id;
    this.url = url;
    this.itemCount = itemCount;
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
}

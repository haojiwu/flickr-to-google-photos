package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrAlbumList {
  private final List<FlickrAlbum> flickrAlbums;
  private final int total;
  private final int page;
  private final int pageSize;
  private final boolean hasNext;

  public FlickrAlbumList(List<FlickrAlbum> flickrAlbums, int total, int page, int pageSize, boolean hasNext) {
    this.flickrAlbums = flickrAlbums;
    this.total = total;
    this.page = page;
    this.pageSize = pageSize;
    this.hasNext = hasNext;
  }

  public List<FlickrAlbum> getFlickrAlbums() {
    return flickrAlbums;
  }

  public int getTotal() {
    return total;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public boolean isHasNext() {
    return hasNext;
  }
}

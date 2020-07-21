package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrAlbumList {
  private final List<FlickrAlbum> flickrAlbums;
  private final int total;
  private final int start;
  private final boolean hasNext;

  public FlickrAlbumList(List<FlickrAlbum> flickrAlbums, int total, int start, boolean hasNext) {
    this.flickrAlbums = flickrAlbums;
    this.total = total;
    this.start = start;
    this.hasNext = hasNext;
  }

  public List<FlickrAlbum> getFlickrAlbums() {
    return flickrAlbums;
  }

  public int getTotal() {
    return total;
  }

  public int getStart() {
    return start;
  }

  public boolean isHasNext() {
    return hasNext;
  }
}

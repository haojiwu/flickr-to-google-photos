package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrPhotoList {
  private final List<FlickrPhoto> flickrPhotos;
  private final int total;
  private final int start;
  private final boolean hasNext;

  public FlickrPhotoList(List<FlickrPhoto> flickrPhotos, int total, int start, boolean hasNext) {
    this.flickrPhotos = flickrPhotos;
    this.total = total;
    this.start = start;
    this.hasNext = hasNext;
  }

  public List<FlickrPhoto> getFlickrPhotos() {
    return flickrPhotos;
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

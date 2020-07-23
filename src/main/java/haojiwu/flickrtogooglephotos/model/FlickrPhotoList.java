package haojiwu.flickrtogooglephotos.model;

import java.util.List;

public class FlickrPhotoList {
  private final List<FlickrPhoto> flickrPhotos;
  private final int total;
  private final int page;
  private final int pageSize;
  private final boolean hasNext;

  public FlickrPhotoList(List<FlickrPhoto> flickrPhotos, int total, int page, int pageSize, boolean hasNext) {
    this.flickrPhotos = flickrPhotos;
    this.total = total;
    this.page = page;
    this.pageSize = pageSize;
    this.hasNext = hasNext;
  }

  public List<FlickrPhoto> getFlickrPhotos() {
    return flickrPhotos;
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

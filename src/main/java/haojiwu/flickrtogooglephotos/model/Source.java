package haojiwu.flickrtogooglephotos.model;

public enum Source {
  FLICKR("Flickr");
  private final String name;

  public String getName() {
    return name;
  }

  Source(String name) {
    this.name = name;
  }
}

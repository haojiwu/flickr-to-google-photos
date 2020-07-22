package haojiwu.flickrtogooglephotos.controller;

import org.springframework.http.HttpStatus;

public class ErrorInfo {
  public final String status;
  public final String url;
  public final String ex;

  public ErrorInfo(HttpStatus status, String url, Exception ex) {
    this.status = status.toString();
    this.url = url;
    this.ex = ex.getLocalizedMessage();
  }
}
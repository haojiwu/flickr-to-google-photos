package haojiwu.flickrtogooglephotos.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.FlickrRuntimeException;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.Photosets;
import com.flickr4java.flickr.tags.Tag;
import com.google.common.collect.ImmutableSet;
import haojiwu.flickrtogooglephotos.model.FlickrAlbum;
import haojiwu.flickrtogooglephotos.model.FlickrAlbumList;
import haojiwu.flickrtogooglephotos.model.FlickrCredential;
import haojiwu.flickrtogooglephotos.model.FlickrPhoto;
import haojiwu.flickrtogooglephotos.model.FlickrPhotoList;
import haojiwu.flickrtogooglephotos.service.FlickrService;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class FlickrController {
  private static final Logger logger = LoggerFactory.getLogger(FlickrController.class);

  private static final String DEFAULT_ALBUMS_PAGE_SIZE = "5";

  private static final Set<String> EXCLUDE_TAGS_PREFIX = ImmutableSet.of(
          "geo:lon=", "geo:lat=", "geotagged");

  @Autowired
  private FlickrService flickrService;

  @RequestMapping("/flickr/auth")
  public void auth(HttpServletResponse response) throws IOException {
    logger.info("start flickr auth");
    String authUrl = flickrService.getAuthUrl();
    logger.info("send redirect to {}", authUrl);
    response.sendRedirect(authUrl);
  }

  @GetMapping("/flickr/auth/complete")
  public FlickrCredential complete(@RequestParam(name = "oauth_verifier") String oauthVerifier,
                                   @RequestParam(name = "oauth_token") String token) throws FlickrException {
    logger.info("start flickr auth complete {} {}", oauthVerifier, token);
    Auth auth = flickrService.handleAuthComplete(oauthVerifier, token);
    return new FlickrCredential(auth.getUser().getId(), auth.getToken(), auth.getTokenSecret());
  }

  @ResponseStatus(value=HttpStatus.BAD_REQUEST)
  @ExceptionHandler({IllegalArgumentException.class,FlickrException.class,JsonParseException.class})
  @ResponseBody
  ErrorInfo handleBadRequest(HttpServletRequest req, Exception ex) {
    logger.error("handleBadRequest", ex);
    return new ErrorInfo(HttpStatus.BAD_REQUEST, req.getRequestURL().toString(), ex);
  }

  @ResponseStatus(value=HttpStatus.SERVICE_UNAVAILABLE)
  @ExceptionHandler({IOException.class, FlickrRuntimeException.class})
  @ResponseBody
  ErrorInfo handleServiceUnavailable(HttpServletRequest req, Exception ex) {
    logger.error("handleServiceUnavailable", ex);
    return new ErrorInfo(HttpStatus.SERVICE_UNAVAILABLE, req.getRequestURL().toString(), ex);
  }

  static FlickrPhoto convertPhoto(Photo photo) {
    FlickrPhoto.Builder builder = new FlickrPhoto.Builder()
            .setId(photo.getId())
            .setFlickrUrl(photo.getUrl())
            .setTitle(photo.getTitle())
            .setDescription(photo.getDescription())
            .setMedia(photo.getMedia());

    List<String> tags = photo.getTags().stream()
            .map(Tag::getValue)
            .filter(tag -> EXCLUDE_TAGS_PREFIX.stream().noneMatch(tag::startsWith))
            .collect(Collectors.toList());

    builder.setTags(tags);

    if (photo.getGeoData() != null) {
      builder.setLatitude(photo.getGeoData().getLatitude())
              .setLongitude(photo.getGeoData().getLongitude());
    }

    String photoUrl;
    if (photo.getMedia().equals("video")) {
      photoUrl = photo.getVideoOriginalUrl();
    } else { // media == "photo"
      try {
        photoUrl = photo.getOriginalUrl();
      } catch (FlickrException e) { // should never happen
        if (photo.getLarge2048Size() != null) {
          // https://www.flickr.com/services/api/misc.urls.html
          // Large 2048 only exists after March 1st 2012
          photoUrl = photo.getLarge2048Url();
        } else {
          photoUrl = photo.getLargeUrl();
        }
        logger.error("getOriginalUrl fail, use large url instead {}", photoUrl, e);
      }
    }
    builder.setPhotoUrl(photoUrl);
    return builder.build();
  }


  @GetMapping("/flickr/allPhotos")
  public FlickrPhotoList getAllPhotos(@RequestParam String token, @RequestParam String secret,
                                      @RequestParam int page) throws FlickrException {

    PhotoList<Photo> photos = flickrService.getPhotos(token, secret, page);

    List<FlickrPhoto> flickrPhotos = photos.stream()
            .map(FlickrController::convertPhoto)
            .collect(Collectors.toList());

    return new FlickrPhotoList(flickrPhotos, photos.getTotal(), page, photos.getPerPage(),photos.size() == photos.getPerPage());
  }

  @GetMapping("/flickr/allAlbums")
  public FlickrAlbumList getAllAlbums(@RequestParam String token, @RequestParam String secret, @RequestParam String userId,
                                      @RequestParam int page,
                                      @RequestParam(defaultValue = DEFAULT_ALBUMS_PAGE_SIZE) int pageSize) throws FlickrException {

    Photosets photosets = flickrService.getPhotosets(token, secret, userId, page, pageSize);

    List<FlickrAlbum> flickrAlbums = new ArrayList<>();
    for (Photoset photoset: photosets.getPhotosets()) {
      List<Photo> photos = flickrService.getAllPhotosInPhotoset(token,secret, photoset.getId());

      FlickrAlbum flickrAlbum = new FlickrAlbum.Builder()
              .setId(photoset.getId())
              .setTitle(photoset.getTitle())
              .setDescription(StringEscapeUtils.unescapeHtml4(photoset.getDescription()))
              .setUrl(photoset.getUrl())
              .setCoverPhotoId(photoset.getPrimaryPhoto().getId())
              .setPhotoIds(photos.stream()
                      .map(Photo::getId)
                      .collect(Collectors.toList()))
              .build();

      flickrAlbums.add(flickrAlbum);
    }
    return new FlickrAlbumList(flickrAlbums, photosets.getTotal(), page, photosets.getPerPage(),
            photosets.getPhotosets().size() == photosets.getPerPage());
  }
}

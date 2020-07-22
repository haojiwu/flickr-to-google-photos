package haojiwu.flickrtogooglephotos.controller;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.Photosets;
import com.flickr4java.flickr.tags.Tag;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuth1Token;
import com.google.common.collect.ImmutableSet;
import haojiwu.flickrtogooglephotos.model.*;
import haojiwu.flickrtogooglephotos.service.FlickrService;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class FlickrController {
  private static final Logger logger = LoggerFactory.getLogger(FlickrController.class);

  private static final int ALBUMS_PER_PAGE = 5;
  private static final int PHOTOS_PER_PAGE = 500;

  private static final Set<String> EXCLUDE_TAGS_PREFIX = ImmutableSet.of(
          "geo:lon=", "geo:lat=", "geotagged");

  @Value("${app.flickr.key}")
  private String apiKey;
  @Value("${app.flickr.secret}")
  private String apiSecret;
  @Value("${app.host}")
  private String appHost;

  private final Map<String, OAuth1RequestToken> requestTokenStore = new ConcurrentHashMap<>();

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
  @ExceptionHandler({IllegalArgumentException.class,FlickrException.class})
  @ResponseBody
  ErrorInfo handleBadRequest(HttpServletRequest req, Exception ex) {
    logger.error("handleBadRequest", ex);
    return new ErrorInfo(HttpStatus.BAD_REQUEST, req.getRequestURL().toString(), ex);
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
                                      @RequestParam int start) throws FlickrException {

    PhotoList<Photo> photos = flickrService.getPhotos(token, secret, start);

    List<FlickrPhoto> flickrPhotos = photos.stream()
            .map(FlickrController::convertPhoto)
            .collect(Collectors.toList());

    return new FlickrPhotoList(flickrPhotos, photos.getTotal(), start, photos.size() == photos.getPerPage());
  }

  @GetMapping("/flickr/allAlbums")
  public FlickrAlbumList getAllAlbums(@RequestParam String token, @RequestParam String secret, @RequestParam String userId,
                                      @RequestParam int start,
                                      @RequestParam(defaultValue = "5") int batchSize) throws FlickrException {

    Photosets photosets = flickrService.getPhotosets(token, secret, userId, start, batchSize);

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
    return new FlickrAlbumList(flickrAlbums, photosets.getTotal(), start,
            photosets.getPhotosets().size() == photosets.getPerPage());
  }
}

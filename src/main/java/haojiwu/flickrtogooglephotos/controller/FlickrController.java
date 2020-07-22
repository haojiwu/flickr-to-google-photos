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
import haojiwu.flickrtogooglephotos.model.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class FlickrController {
  Logger logger = LoggerFactory.getLogger(FlickrController.class);

  private static final int ALBUMS_PER_PAGE = 5;
  private static final int PHOTOS_PER_PAGE = 500;

  @Value("${app.flickr.key}")
  private String apiKey;
  @Value("${app.flickr.secret}")
  private String apiSecret;
  @Value("${app.host}")
  private String appHost;

  private final Map<String, OAuth1RequestToken> requestTokenStore = new ConcurrentHashMap<>();


  @RequestMapping("/flickr/auth")
  public void auth(HttpServletResponse response) throws IOException {
    Flickr flickr = new Flickr(apiKey, apiSecret, new REST());
    AuthInterface authInterface = flickr.getAuthInterface();

    OAuth1RequestToken requestToken = authInterface.getRequestToken(appHost + "/flickr/auth/complete");
    requestTokenStore.put(requestToken.getToken(), requestToken);
    String url = authInterface.getAuthorizationUrl(requestToken, Permission.READ);
    System.out.println("Follow this URL to authorise yourself on Flickr");
    System.out.println(url);
    response.sendRedirect(url);
  }

  @GetMapping("/flickr/auth/complete")
  public FlickrCredential complete(@RequestParam(name = "oauth_verifier") String oauthVerifier,
                                   @RequestParam(name = "oauth_token") String token) throws FlickrException {

    Flickr flickr = new Flickr(apiKey, apiSecret, new REST());
    AuthInterface authInterface = flickr.getAuthInterface();

    System.out.println("token = " + token);
    OAuth1RequestToken requestToken = requestTokenStore.get(token);
    OAuth1Token accessToken = authInterface.getAccessToken(requestToken, oauthVerifier);
    System.out.println("accessToken token=" + accessToken.getToken() + ", getTokenSecret=" + accessToken.getTokenSecret());
    Auth auth = authInterface.checkToken(accessToken);
    System.out.println("auth user=" + auth.getUser().getId() + ", auth permission" + auth.getPermission());


    return new FlickrCredential(auth.getUser().getId(),   accessToken.getToken(), accessToken.getTokenSecret());
  }

  @GetMapping("/flickr/allPhotos")
  public FlickrPhotoList getAllPhotos(@RequestParam String token, @RequestParam String secret,
                                      @RequestParam int start) throws FlickrException {

    Flickr flickr = new Flickr(apiKey, apiSecret, new REST());

    Auth auth = new Auth(Permission.READ, null);
    auth.setToken(token);
    auth.setTokenSecret(secret);
    RequestContext.getRequestContext().setAuth(auth);

    Set<String> extra = new HashSet();
    extra.add("original_format");
    extra.add("geo");
    extra.add("description");
    extra.add("tags");
    extra.add("media");

    int page = start / PHOTOS_PER_PAGE + 1;
    PhotoList<Photo> photos = flickr.getPeopleInterface().getPhotos("me", null, null, null,
            null, null, null, null, extra, PHOTOS_PER_PAGE, page);
    List<FlickrPhoto> flickrPhotos = new ArrayList<>();
    for (Photo photo: photos) {
      System.out.println(photo.getId() + ", " + photo.getTitle() + ", " + photo.getDescription());
      System.out.println(photo.getOriginalUrl());
      if (photo.getCountry() != null) {
        System.out.println(photo.getCountry().getName());
      }
      if (photo.getCounty() != null) {
        System.out.println(photo.getCounty().getName());
      }
      if (photo.getRegion() != null) {
        System.out.println(photo.getRegion().getName());
      }
      if (photo.getLocality() != null) {
        System.out.println(photo.getLocality().getName());
      }
      Float latitude = null;
      Float longitude = null;
      if (photo.getGeoData() != null) {
        System.out.println(photo.getGeoData().getLatitude() + ", " + photo.getGeoData().getLongitude());
        latitude = photo.getGeoData().getLatitude();
        longitude = photo.getGeoData().getLongitude();
      }


      System.out.println(StringUtils.join(photo.getTags().stream().map(Tag::getValue).toArray(),","));

      String photoUrl = photo.getOriginalUrl();
      if (photo.getMedia().equals("video")) {
        photoUrl = photo.getVideoOriginalUrl();
      }

      FlickrPhoto flickrPhoto = new FlickrPhoto(photo.getId(), photo.getUrl(), photoUrl,
              photo.getTitle(), photo.getDescription(), latitude, longitude,
              photo.getTags().stream().map(Tag::getValue).collect(Collectors.toList()), photo.getMedia());
      flickrPhotos.add(flickrPhoto);
      System.out.println(flickrPhoto.toString());
      logger.info(photo.getMedia());
      logger.info(photo.getMediaStatus());
      if (photo.getOriginalUrl().endsWith(".mp4")) {


        logger.info(photo.getVideoOriginalUrl());
        logger.info(photo.getHdMp4Url());
        logger.info(photo.getOriginalFormat());
        logger.info(photo.getSiteMP4Url());
        logger.info(photo.getMobileMp4Url());
      }
    }

    return new FlickrPhotoList(flickrPhotos, photos.getTotal(), start, photos.size() == photos.getPerPage());
  }

  @GetMapping("/flickr/allAlbums")
  public FlickrAlbumList getAllAlbums(@RequestParam String token, @RequestParam String secret, @RequestParam String userId,
                                      @RequestParam int start) throws FlickrException {
    Flickr flickr = new Flickr(apiKey, apiSecret, new REST());

    Auth auth = new Auth(Permission.READ, null);
    auth.setToken(token);
    auth.setTokenSecret(secret);
    RequestContext.getRequestContext().setAuth(auth);

    List<FlickrAlbum> flickrAlbums = new ArrayList<>();
    int page = start / ALBUMS_PER_PAGE + 1;
    Photosets photosets = flickr.getPhotosetsInterface().getList(userId, ALBUMS_PER_PAGE, page, null);
    for (Photoset photoset: photosets.getPhotosets()) {
      List<String> photoIds = new ArrayList<>();
      int photoPage = 1;
      while (true) {
        PhotoList<Photo> photos = flickr.getPhotosetsInterface().getPhotos(photoset.getId(), PHOTOS_PER_PAGE, photoPage);
        photoIds.addAll(photos.stream().map(Photo::getId).collect(Collectors.toList()));
        if (photos.size() < photos.getPerPage()) {
          break;
        }
        photoPage++;
      }

      FlickrAlbum flickrAlbum = new FlickrAlbum(photoset.getId(), photoset.getTitle(), photoset.getDescription(),
              photoset.getUrl(), photoset.getPrimaryPhoto().getId(), photoIds);
      flickrAlbums.add(flickrAlbum);
    }
    return new FlickrAlbumList(flickrAlbums, photosets.getTotal(), start,
            photosets.getPhotosets().size() == photosets.getPerPage());
  }
}

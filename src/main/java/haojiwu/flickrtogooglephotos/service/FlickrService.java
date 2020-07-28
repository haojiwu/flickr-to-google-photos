package haojiwu.flickrtogooglephotos.service;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photosets.Photosets;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuth1Token;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FlickrService {
  private static final Logger logger = LoggerFactory.getLogger(FlickrService.class);

  // max photos per page by Flickr API
  // https://www.flickr.com/services/api/flickr.photosets.getPhotos.html
  private static final int PHOTOS_PAGE_SIZE = 500; // max photo per page by Flickr API

  private static final Set<String> PHOTO_EXTRA_PARAMETER = ImmutableSet.of(
          "original_format", "geo", "description", "tags", "media");


  @Value("${app.host}")
  private String appHost;

  private final Map<String, OAuth1RequestToken> requestTokenStore = new ConcurrentHashMap<>();
  private final Flickr flickr;

  public FlickrService(@Value("${app.flickr.key}") String apiKey, @Value("${app.flickr.secret}") String apiSecret) {
    this.flickr = new Flickr(apiKey, apiSecret, new REST());
  }

  private String getAuthCallbackUrl() {
    return appHost + "/flickr/auth/complete";
  }

  public String getAuthUrl() {
    AuthInterface authInterface = flickr.getAuthInterface();
    OAuth1RequestToken requestToken = authInterface.getRequestToken(getAuthCallbackUrl());
    logger.info("add request token {} to mapping store", requestToken.getToken());
    requestTokenStore.put(requestToken.getToken(), requestToken);
    String ret = authInterface.getAuthorizationUrl(requestToken, Permission.READ);
    logger.info("Auth URL: {}", ret);
    return ret;
  }

  public Auth handleAuthComplete(String oauthVerifier, String token) throws FlickrException {
    AuthInterface authInterface = flickr.getAuthInterface();
    OAuth1RequestToken requestToken = requestTokenStore.get(token);
    if (requestToken == null) {
      throw new IllegalArgumentException("Unknown token " + token);
    }
    logger.info("remove request token {} from mapping store", token);
    requestTokenStore.remove(token);

    OAuth1Token accessToken = authInterface.getAccessToken(requestToken, oauthVerifier);
    return authInterface.checkToken(accessToken);
  }

  public PhotoList<Photo> getPhotos(String token, String secret, int page) throws FlickrException {
    Auth auth = new Auth(Permission.READ, null);
    auth.setToken(token);
    auth.setTokenSecret(secret);
    RequestContext.getRequestContext().setAuth(auth);

    logger.info("start to get photo token: {}, page: {}", token, page);
    return flickr.getPeopleInterface().getPhotos("me", null, null, null,
            null, null, null, null, PHOTO_EXTRA_PARAMETER, PHOTOS_PAGE_SIZE, page);
  }

  public Photosets getPhotosets(String token, String secret, String userId,
                                int page, int pageSize) throws FlickrException {
    Auth auth = new Auth(Permission.READ, null);
    auth.setToken(token);
    auth.setTokenSecret(secret);
    RequestContext.getRequestContext().setAuth(auth);
    logger.info("start to get photo set token: {}, page: {}, pageSize: {}", token, page, pageSize);
    return flickr.getPhotosetsInterface().getList(userId, pageSize, page, null);
  }

  public List<Photo> getAllPhotosInPhotoset(String token, String secret, String photoSetId) throws FlickrException {
    Auth auth = new Auth(Permission.READ, null);
    auth.setToken(token);
    auth.setTokenSecret(secret);
    RequestContext.getRequestContext().setAuth(auth);

    List<Photo> ret = new ArrayList<>();
    int page = 1; // flickr page starts from 1
    while (true) {
      logger.info("start to get photos from photo set {} token: {},  page: {}", photoSetId, token, page);
      PhotoList<Photo> photos = flickr.getPhotosetsInterface().getPhotos(photoSetId, PHOTOS_PAGE_SIZE, page);
      logger.info("photos in photo set {}, page: {}, size: {}, perPage: {}, pages: {}",
              photoSetId, photos.getPage(), photos.size(), photos.getPerPage(), photos.getPages());
      ret.addAll(photos);
      if (photos.getPage() == photos.getPages()) {
        break;
      }
      page++;
    }
    return ret;

  }



}

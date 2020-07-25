package haojiwu.flickrtogooglephotos.service;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.proto.AlbumPosition;
import com.google.photos.library.v1.proto.NewEnrichmentItem;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.AlbumPositionFactory;
import com.google.photos.library.v1.util.NewEnrichmentItemFactory;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.Album;
import haojiwu.flickrtogooglephotos.model.FlickrAlbum;
import haojiwu.flickrtogooglephotos.model.FlickrPhoto;
import haojiwu.flickrtogooglephotos.model.IdMapping;
import haojiwu.flickrtogooglephotos.model.Source;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GoogleService {
  private static final Logger logger = LoggerFactory.getLogger(GoogleService.class);

  private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();

  @Value("${app.host}")
  private String appHost;

  @Autowired
  private IdMappingService idMappingService;

  private final String clientId;
  private final String clientSecret;
  private final AuthorizationCodeFlow authorizationCodeFlow;
  private final LoadingCache<String, String> userIdCache;
  private final LoadingCache<String, PhotosLibraryClient> photosLibraryClientCache;

  public GoogleService(@Value("${app.google.clientId}") String clientId, @Value("${app.google.clientSecret}") String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authorizationCodeFlow = new GoogleAuthorizationCodeFlow.Builder(
            NET_HTTP_TRANSPORT,
            JacksonFactory.getDefaultInstance(),
            clientId,
            clientSecret,
            Arrays.asList("https://www.googleapis.com/auth/photoslibrary",
                    "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata",
                    "https://www.googleapis.com/auth/userinfo.profile"))
            .setAccessType("offline")
            .setApprovalPrompt("force")
            .build();
    this.userIdCache = buildUserIdCache();
    this.photosLibraryClientCache = buildPhotosLibraryClientCache();
  }

  private String getAuthCallbackUrl() {
    return appHost + "/google/auth/complete";
  }

  public String getAuthUrl() {
    String ret = authorizationCodeFlow.newAuthorizationUrl()
            .setState(String.valueOf(Math.random()))
            .setRedirectUri(getAuthCallbackUrl())
            .build();
    logger.info("Auth URL: {}", ret);
    return ret;
  }

  public TokenResponse handleAuthComplete(@RequestParam String code) throws IOException {
    return authorizationCodeFlow.newTokenRequest(code)
            .setRedirectUri(getAuthCallbackUrl())
            .execute();
  }


  Userinfo getUserInfo(String refreshToken) throws IOException {
    UserCredentials userCredentials = UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(refreshToken)
            .build();

    String accessToken = userCredentials.refreshAccessToken().getTokenValue();
    Oauth2 oauth2 = new Oauth2.Builder(new NetHttpTransport(),
            JacksonFactory.getDefaultInstance(), null)
            .setHttpRequestInitializer((request -> request.getHeaders()
                    .put("Authorization", "Bearer " + accessToken)))
            .build();
    logger.info("Start to get userinfo, accessToken: {}", accessToken);
    Userinfo ret = oauth2.userinfo().get().execute();
    logger.info("accessToken: {}, userinfo: {}", accessToken, ret);
    return ret;
  }

  LoadingCache<String, String> buildUserIdCache() {
    return CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(
                    new CacheLoader<String, String>() {
                      @Override
                      public String load(String refreshToken) throws IOException {
                        return getUserInfo(refreshToken).getId();
                      }
                    });
  }

  public String getUserId(String refreshToken) throws IOException {
    try {
      return userIdCache.get(refreshToken);
    } catch (ExecutionException e) {
      logger.error("Fail to get user info. refreshToken: {}", refreshToken, e);
      assert(e.getCause() instanceof IOException);
      throw (IOException) e.getCause();
    }
  }

  LoadingCache<String, PhotosLibraryClient> buildPhotosLibraryClientCache() {
    return CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(
                    new CacheLoader<String, PhotosLibraryClient>() {
                      @Override
                      public PhotosLibraryClient load(String refreshToken) throws IOException {
                        UserCredentials userCredentials = UserCredentials.newBuilder()
                                .setClientId(clientId)
                                .setClientSecret(clientSecret)
                                .setRefreshToken(refreshToken)
                                .build();

                        PhotosLibrarySettings settings =
                                PhotosLibrarySettings.newBuilder()
                                        .setCredentialsProvider(
                                                FixedCredentialsProvider.create(userCredentials))
                                        .build();

                        return PhotosLibraryClient.initialize(settings);
                      }
                    });
  }

  public PhotosLibraryClient getPhotosLibraryClient(String refreshToken) throws IOException {
    try {
      return photosLibraryClientCache.get(refreshToken);
    } catch (ExecutionException e) {
      logger.error("Fail to build PhotosLibraryClient. refreshToken: {}", refreshToken, e);
      assert(e.getCause() instanceof IOException);
      throw (IOException) e.getCause();
    }
  }

  static String buildDefaultAlbumTitle(Source source) {
    return "Photos from " + source.getName();
  }


  public String getDefaultAlbumId(PhotosLibraryClient photosLibraryClient, String userId, Source source) {
    String defaultAlbumTitle = buildDefaultAlbumTitle(source);
    String defaultAlbumId = idMappingService.findById(userId, userId)
            .map(IdMapping::getTargetId)
            .orElse(null);
    if (defaultAlbumId == null) {
      for (Album album: photosLibraryClient.listAlbums(true).iterateAll()) {
        if (album.getTitle().equals(defaultAlbumTitle)) {
          defaultAlbumId = album.getId();
          break;
        }
      }
      if (defaultAlbumId == null) {
        defaultAlbumId = photosLibraryClient.createAlbum(defaultAlbumTitle).getId();
        idMappingService.saveOrUpdate(userId, userId, defaultAlbumId);
      }
    }
    return defaultAlbumId;
  }

  static String buildItemDescription(FlickrPhoto sourcePhoto) {

    StringBuilder itemDescription = new StringBuilder();
    if (StringUtils.isNotBlank(sourcePhoto.getTitle())) {
      itemDescription.append(sourcePhoto.getTitle()).append("\n");
    }
    if (StringUtils.isNotBlank(sourcePhoto.getDescription())) {
      itemDescription.append(sourcePhoto.getDescription()).append("\n");
    }

    itemDescription.append(sourcePhoto.getTags().stream()
            .map(tag -> "#" + tag)
            .collect(Collectors.joining(" ")));
    return itemDescription.toString();
  }

  public NewMediaItem uploadPhotoAndCreateNewMediaItem(PhotosLibraryClient photosLibraryClient,
                                                       FlickrPhoto sourcePhoto,
                                                       String photoLocalPath) throws IOException {

    String mineType;
    // flickr doesn't provide original video for API download. It will be converted to MP4.
    if (sourcePhoto.getMedia() == FlickrPhoto.Media.VIDEO) {
      mineType = "video/mp4";
    } else {
      mineType = "image/png"; // For google photo API it also works for JPG image
    }

    // Open the file and automatically close it after upload
    try (RandomAccessFile file = new RandomAccessFile(photoLocalPath, "r")) {
      UploadMediaItemRequest uploadRequest =
              UploadMediaItemRequest.newBuilder()
                      .setMimeType(mineType)
                      .setDataFile(file)
                      .build();
      UploadMediaItemResponse uploadResponse = photosLibraryClient.uploadMediaItem(uploadRequest);
      if (uploadResponse.getError().isPresent()) {
        logger.error("upload photo fail. photoLocalPath: {}", photoLocalPath, uploadResponse.getError().get().getCause());
        throw (ApiException) uploadResponse.getError().get().getCause();
      }
      String uploadToken = uploadResponse.getUploadToken().get();
      logger.info("upload success with uploadToken {}, photoLocalPath: {}", uploadToken, photoLocalPath);
      String itemDescription = buildItemDescription(sourcePhoto);
      return NewMediaItemFactory.createNewMediaItem(uploadToken, sourcePhoto.getFlickrUrl(), itemDescription);
    }
  }

  static String buildAlbumDescription(FlickrAlbum sourceAlbum) {
    StringBuilder albumDescription = new StringBuilder();
    if (StringUtils.isNotBlank(sourceAlbum.getDescription())) {
      albumDescription.append(sourceAlbum.getDescription()).append("\n");
    }
    albumDescription.append(sourceAlbum.getUrl());
    return albumDescription.toString();
  }

  public Album createAlbum(PhotosLibraryClient photosLibraryClient, FlickrAlbum sourceAlbum) {
    Album googleAlbum = photosLibraryClient.createAlbum(sourceAlbum.getTitle());

    NewEnrichmentItem newEnrichmentItem =
            NewEnrichmentItemFactory.createTextEnrichment(buildAlbumDescription(sourceAlbum));
    AlbumPosition albumPosition = AlbumPositionFactory.createFirstInAlbum();
    photosLibraryClient.addEnrichmentToAlbum(googleAlbum.getId(), newEnrichmentItem, albumPosition);
    return googleAlbum;
  }

}

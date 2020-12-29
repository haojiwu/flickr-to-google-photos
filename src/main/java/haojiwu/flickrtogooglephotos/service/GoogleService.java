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
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.NewEnrichmentItem;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.proto.NewMediaItemResult;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.AlbumPositionFactory;
import com.google.photos.library.v1.util.NewEnrichmentItemFactory;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import haojiwu.flickrtogooglephotos.model.FlickrAlbum;
import haojiwu.flickrtogooglephotos.model.FlickrPhoto;
import haojiwu.flickrtogooglephotos.model.GoogleCreatePhotoResult;
import haojiwu.flickrtogooglephotos.model.IdMapping;
import haojiwu.flickrtogooglephotos.model.IdMappingKey;
import haojiwu.flickrtogooglephotos.model.Source;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GoogleService {
  private static final Logger logger = LoggerFactory.getLogger(GoogleService.class);
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();
  private static final AlbumPosition FIRST_IN_ALBUM = AlbumPositionFactory.createFirstInAlbum();


  @Value("${app.host}")
  private String appHost;

  @Value("${app.photoFolder}")
  private String photoFolder;

  @Value("${app.deleteLocalFile:true}")
  private boolean deleteLocalFile;

  @Autowired
  private IdMappingService idMappingService;

  @Autowired
  private RetryService retryService;

  @Autowired
  private ExifService exifService;

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

  private PhotosLibraryClient getPhotosLibraryClient(String refreshToken) throws IOException {
    try {
      return photosLibraryClientCache.get(refreshToken);
    } catch (ExecutionException e) {
      logger.error("Fail to build PhotosLibraryClient. refreshToken: {}", refreshToken, e);
      assert(e.getCause() instanceof IOException);
      throw (IOException) e.getCause();
    }
  }


  private String downloadPhoto(FlickrPhoto sourcePhoto) throws IOException {
    String filename;
    if (sourcePhoto.getMedia() == FlickrPhoto.Media.VIDEO) {
      filename = sourcePhoto.getId() + ".mp4";
    } else { // photo
      filename = sourcePhoto.getDownloadUrl().substring(sourcePhoto.getDownloadUrl().lastIndexOf("/"));
    }
    String localPath = photoFolder + "/" + filename;
    retryService.downloadFile(sourcePhoto.getDownloadUrl(), localPath);
    return localPath;
  }

  public GoogleCreatePhotoResult downloadAndUploadSourcePhoto(String refreshToken,
                                                              Map<String, String> existingSourceIdToGoogleId,
                                                              FlickrPhoto sourcePhoto,
                                                              boolean forceUnique)  {
    String sourceId = sourcePhoto.getId();
    if (existingSourceIdToGoogleId.containsKey(sourceId)) {
      logger.info("Don't need t to migrate {}", sourceId);
      return new GoogleCreatePhotoResult.Builder(sourceId)
              .setStatus(GoogleCreatePhotoResult.Status.EXIST_NO_NEED_TO_CREATE)
              .setGoogleId(existingSourceIdToGoogleId.get(sourceId))
              .build();
    }
    String photoLocalPathDownloaded = null;
    String photoLocalPath = null;
    try {
      PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);

      photoLocalPathDownloaded = downloadPhoto(sourcePhoto);

      ExifService.Request request = new ExifService.Request(photoLocalPathDownloaded);
      if (sourcePhoto.getMedia() == FlickrPhoto.Media.PHOTO
              && sourcePhoto.getLatitude() != null
              && sourcePhoto.getLongitude() != null) {
        request.latitude = sourcePhoto.getLatitude();
        request.longitude = sourcePhoto.getLongitude();
      }

      if (forceUnique) {
        request.userComment = "flickr-to-google-photos " +  DATE_FORMAT.format(new Date());
      }

      photoLocalPath = exifService.tagPhoto(request);

      NewMediaItem newMediaItem = uploadPhotoAndCreateNewMediaItem(photosLibraryClient, sourcePhoto, photoLocalPath);

      return new GoogleCreatePhotoResult.Builder(sourcePhoto.getId())
              .setStatus(GoogleCreatePhotoResult.Status.SUCCESS)
              .setNewMediaItem(newMediaItem)
              .build();
    } catch (Exception e) {
      logger.error("Error when adding photo: {}", sourcePhoto.getId(), e);
      return new GoogleCreatePhotoResult.Builder(sourcePhoto.getId())
              .setStatus(GoogleCreatePhotoResult.Status.FAIL)
              .setError(e.getMessage())
              .build();
    } finally {
      if (deleteLocalFile) {
        if (photoLocalPath != null) {
          new File(photoLocalPath).delete();
        }
        if (photoLocalPathDownloaded != null && !StringUtils.equals(photoLocalPathDownloaded, photoLocalPath)) {
          new File(photoLocalPathDownloaded).delete();
        }
      }
    }
  }

  public void createMediaItems(String refreshToken,
                               String userId,
                               List<GoogleCreatePhotoResult> results) throws IOException {
    PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);
    List<GoogleCreatePhotoResult> resultsWithNewItems = results.stream()
            .filter(GoogleCreatePhotoResult::hasNewMediaItem)
            .collect(Collectors.toList());

    if (resultsWithNewItems.isEmpty()) {
      logger.info("No new media items need to be created. userId {}", userId);
      return;
    }

    BatchCreateMediaItemsResponse response = retryService.batchCreateMediaItems(photosLibraryClient,
            resultsWithNewItems.stream()
                    .map(GoogleCreatePhotoResult::getNewMediaItem)
                    .collect(Collectors.toList()));
    assert (response.getNewMediaItemResultsList().size() == resultsWithNewItems.size());
    for (int i = 0; i < response.getNewMediaItemResultsList().size(); i++) {
      NewMediaItemResult itemsResponse = response.getNewMediaItemResultsList().get(i);
      GoogleCreatePhotoResult result = resultsWithNewItems.get(i);
      Status status = itemsResponse.getStatus();
      if (status.getCode() == Code.OK_VALUE) {
        MediaItem mediaItem = itemsResponse.getMediaItem();
        logger.info("success create mediaItem: id {}, source id: {}", mediaItem.getId(), result.getSourceId());
        result.setGoogleId(mediaItem.getId());
        result.setUrl(mediaItem.getProductUrl());
        // Google Photos can upload the same (in terms of checksum, including exif) photo again but only keep one media item.
        // If
        //  1. use already upload it manually
        //  2. other app upload it
        //  3. the same photo upload multiple times in flickr
        // NewMediaItemResult still has OK_VALUE status, but the photo information from import is not written to media item.
        // Also, for 1 and 2, our app can't add this photo to any album we created.
        // Therefore we need to assign different return status for this photo.
        // For photo, we compare filename in media item with input (flickr url) to know if we need to assign EXIST_CAN_NOT_CREATE status
        // User can post /google/photo again with "forceUnique=true" to force create unique photo (by adding exif tag)
        // This issue may not exist for video since every flickr video was encoded to mp4 (flickr api doesn't support original video)
        // Video files should have different checksum after encoding
        if (StringUtils.isNotBlank(mediaItem.getFilename())
                && !mediaItem.getFilename().equals(result.getNewMediaItem().getSimpleMediaItem().getFileName())) {
          logger.info("photo is not unique and it doesn't create mediaItem with input information. "
                          + "existing mediaItem filename {} is not equal to input {}",
                  mediaItem.getFilename(), result.getNewMediaItem().getSimpleMediaItem().getFileName());
          result.setStatus(GoogleCreatePhotoResult.Status.EXIST_CAN_NOT_CREATE);
        }
      } else {
        logger.error("fail to create mediaItem, error: {}, source id: {}", status.getMessage(), result.getSourceId());
        result.setStatus(GoogleCreatePhotoResult.Status.FAIL);
        result.setError(status.getMessage());
      }
    }
  }

  public void updateDefaultAlbumAndIdMapping(String refreshToken,
                                             String userId,
                                             Source source,
                                             List<GoogleCreatePhotoResult> results) throws IOException {
    PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);
    List<GoogleCreatePhotoResult> resultWithSuccessStatus = results.stream()
            .filter(r -> r.getStatus() == GoogleCreatePhotoResult.Status.SUCCESS)
            .collect(Collectors.toList());

    if (resultWithSuccessStatus.isEmpty()) {
      logger.info("Not media item to add default album and update id mapping. userId: {}", userId);
      return;
    }

    String defaultAlbumId = getDefaultAlbumId(photosLibraryClient, userId, source);

    logger.info("add {} new uploaded photo to default album {}", resultWithSuccessStatus.size(), defaultAlbumId);
    retryService.batchAddMediaItemsToAlbum(photosLibraryClient, defaultAlbumId,
            resultWithSuccessStatus.stream()
                    .map(GoogleCreatePhotoResult::getGoogleId)
                    .collect(Collectors.toList()));

    idMappingService.saveOrUpdateAll(
            resultWithSuccessStatus.stream()
                    .map(r -> new IdMapping(new IdMappingKey(userId, r.getSourceId()), r.getGoogleId()))
                    .collect(Collectors.toList()));
  }

  private static String buildDefaultAlbumTitle(Source source) {
    return "Photos from " + source.getName();
  }

  private String getDefaultAlbumId(PhotosLibraryClient photosLibraryClient, String userId, Source source) {
    String defaultAlbumTitle = buildDefaultAlbumTitle(source);
    String defaultAlbumId = idMappingService.findById(userId, userId)
            .map(IdMapping::getTargetId)
            .orElse(null);
    if (defaultAlbumId == null) {
      for (Album album: retryService.listAlbums(photosLibraryClient,true).iterateAll()) {
        if (album.getTitle().equals(defaultAlbumTitle)) {
          defaultAlbumId = album.getId();
          break;
        }
      }
      if (defaultAlbumId == null) {
        defaultAlbumId = retryService.createAlbum(photosLibraryClient, defaultAlbumTitle).getId();
        // a little hack: user id as source id to map to default album id
        idMappingService.saveOrUpdate(userId, userId, defaultAlbumId);
      }
    }
    return defaultAlbumId;
  }

  private static String buildItemDescription(FlickrPhoto sourcePhoto) {
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
    String ret = itemDescription.toString();
    // NewMediaItemFactory.createNewMediaItem() forces description can't be empty, although it is not part of API requirement.
    // Put one space as placeholder
    if (ret.isEmpty()) {
      ret = " ";
    }
    return ret;
  }

  private NewMediaItem uploadPhotoAndCreateNewMediaItem(PhotosLibraryClient photosLibraryClient,
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
      UploadMediaItemResponse uploadResponse = retryService.uploadMediaItem(photosLibraryClient, uploadRequest);
      if (uploadResponse.getError().isPresent()) {
        logger.error("upload photo fail. photoLocalPath: {}", photoLocalPath, uploadResponse.getError().get().getCause());
        throw (ApiException) uploadResponse.getError().get().getCause();
      }
      String uploadToken = uploadResponse.getUploadToken().get();
      logger.info("upload success with uploadToken {}, photoLocalPath: {}", uploadToken, photoLocalPath);
      String itemDescription = buildItemDescription(sourcePhoto);
      return NewMediaItemFactory.createNewMediaItem(uploadToken, sourcePhoto.getUrl(), itemDescription);
    }
  }

  private static String buildAlbumDescription(FlickrAlbum sourceAlbum) {
    StringBuilder albumDescription = new StringBuilder();
    if (StringUtils.isNotBlank(sourceAlbum.getDescription())) {
      albumDescription.append(sourceAlbum.getDescription()).append("\n");
    }
    albumDescription.append(sourceAlbum.getUrl());
    return albumDescription.toString();
  }

  public Album createAlbum(String refreshToken, FlickrAlbum sourceAlbum) throws IOException {
    PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);
    Album googleAlbum = retryService.createAlbum(photosLibraryClient, sourceAlbum.getTitle());

    NewEnrichmentItem newEnrichmentItem =
            NewEnrichmentItemFactory.createTextEnrichment(buildAlbumDescription(sourceAlbum));
    retryService.addEnrichmentToAlbum(photosLibraryClient, googleAlbum.getId(), newEnrichmentItem, FIRST_IN_ALBUM);
    return googleAlbum;
  }

  public void batchAddMediaItemsToAlbum(String refreshToken,
                                        String albumId,
                                        List<String> mediaItemIds) throws IOException {
    PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);
    retryService.batchAddMediaItemsToAlbum(photosLibraryClient, albumId, mediaItemIds);
  }

  public void updateAlbumCoverPhoto(String refreshToken,
                                    Album album,
                                    String newCoverPhotoMediaItemId) throws IOException {
    PhotosLibraryClient photosLibraryClient = getPhotosLibraryClient(refreshToken);
    retryService.updateAlbumCoverPhoto(photosLibraryClient, album, newCoverPhotoMediaItemId);
  }

}

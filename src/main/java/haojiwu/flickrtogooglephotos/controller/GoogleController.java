package haojiwu.flickrtogooglephotos.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.common.collect.Lists;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.proto.NewMediaItemResult;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import haojiwu.flickrtogooglephotos.model.FlickrAlbum;
import haojiwu.flickrtogooglephotos.model.FlickrPhoto;
import haojiwu.flickrtogooglephotos.model.GoogleCreateAlbumResult;
import haojiwu.flickrtogooglephotos.model.GoogleCreatePhotoResult;
import haojiwu.flickrtogooglephotos.model.GoogleCredential;
import haojiwu.flickrtogooglephotos.model.IdMapping;
import haojiwu.flickrtogooglephotos.model.IdMappingKey;
import haojiwu.flickrtogooglephotos.model.Source;
import haojiwu.flickrtogooglephotos.service.ExifService;
import haojiwu.flickrtogooglephotos.service.GoogleService;
import haojiwu.flickrtogooglephotos.service.IdMappingService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class GoogleController {
  Logger logger = LoggerFactory.getLogger(GoogleController.class);
  private static final int GOOGLE_BATCH_SIZE_MAX = 50;
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");


  @Value("${app.photoFolder}")
  private String photoFolder;


  @Autowired
  private IdMappingService idMappingService;

  @Autowired
  private GoogleService googleService;

  @Autowired
  private ExifService exifService;

  @RequestMapping("/google/auth")
  public void auth(HttpServletResponse response) throws IOException {
    logger.info("start google auth");
    String authUrl = googleService.getAuthUrl();
    logger.info("send redirect to {}", authUrl);
    response.sendRedirect(authUrl);
  }

  @RequestMapping("/google/auth/complete")
  public GoogleCredential complete(@RequestParam String state, @RequestParam String code) throws IOException {
    logger.info("start google auth complete, state: {}, code: {}", state, code);
    TokenResponse tokenResponse = googleService.handleAuthComplete(code);
    return new GoogleCredential(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
  }

  @ResponseStatus(value=HttpStatus.BAD_REQUEST)
  @ExceptionHandler({IllegalArgumentException.class,JsonParseException.class,GoogleJsonResponseException.class,InvalidArgumentException.class})
  @ResponseBody
  ErrorInfo handleBadRequest(HttpServletRequest req, Exception ex) {
    logger.error("handleBadRequest", ex);
    return new ErrorInfo(HttpStatus.BAD_REQUEST, req.getRequestURL().toString(), ex);
  }

  @ResponseStatus(value=HttpStatus.SERVICE_UNAVAILABLE)
  @ExceptionHandler({IOException.class,ApiException.class})
  @ResponseBody
  ErrorInfo handleServiceUnavailable(HttpServletRequest req, Exception ex) {
    logger.error("handleServiceUnavailable", ex);
    return new ErrorInfo(HttpStatus.SERVICE_UNAVAILABLE, req.getRequestURL().toString(), ex);
  }

  String downloadPhoto(FlickrPhoto sourcePhoto) throws IOException {
    String filename;
    if (sourcePhoto.getMedia() == FlickrPhoto.Media.VIDEO) {
      filename = sourcePhoto.getId() + ".mp4";
    } else { // photo
      filename = sourcePhoto.getPhotoUrl().substring(sourcePhoto.getPhotoUrl().lastIndexOf("/"));
    }
    String localPath = photoFolder + "/" + filename;
    InputStream in = new URL(sourcePhoto.getPhotoUrl()).openStream();
    Path path = Paths.get(photoFolder + "/" + filename);
    Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);

    return localPath;
  }

  GoogleCreatePhotoResult downloadAndUploadSourcePhoto(PhotosLibraryClient photosLibraryClient,
                                                       Map<String, String> existingSourceIdToGoogleId,
                                                       FlickrPhoto sourcePhoto, boolean forceUnique) {
    String sourceId = sourcePhoto.getId();
    if (existingSourceIdToGoogleId.containsKey(sourceId)) {
      logger.info("Don't need t to migrate {}", sourceId);
      return new GoogleCreatePhotoResult.Builder(sourceId)
              .setStatus(GoogleCreatePhotoResult.Status.EXIST_NO_NEED_TO_CREATE)
              .setGoogleId(existingSourceIdToGoogleId.get(sourceId))
              .build();
    }
    try {
      String photoLocalPath;
      try {
        photoLocalPath = downloadPhoto(sourcePhoto);
      } catch (IOException e) {
        if (StringUtils.containsIgnoreCase(e.getMessage(), "HTTP response code: 50")) {
          // Flickr API sometimes has connection issue. retry once
          logger.info("Flickr 5xx error (usually gateway error). retry once for {}. error: {}",
                  sourcePhoto.getPhotoUrl(), e.getMessage());
          photoLocalPath = downloadPhoto(sourcePhoto);
        } else {
          throw e;
        }
      }

      if (sourcePhoto.getMedia() == FlickrPhoto.Media.PHOTO
              && sourcePhoto.getLatitude() != null
              && sourcePhoto.getLongitude() != null) {
        photoLocalPath = exifService.geotagPhoto(photoLocalPath, sourcePhoto.getLatitude(), sourcePhoto.getLongitude());
      }

      if (forceUnique) {
        photoLocalPath = exifService.appendUserComment(photoLocalPath,
                "flickr-to-google-photos " +  DATE_FORMAT.format(new Date()));
      }

      NewMediaItem newMediaItem = googleService.uploadPhotoAndCreateNewMediaItem(
              photosLibraryClient, sourcePhoto, photoLocalPath);

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
    }
  }

  void createMediaItems(PhotosLibraryClient photosLibraryClient, String userId, List<GoogleCreatePhotoResult> results) {
    List<GoogleCreatePhotoResult> resultsWithNewItems = results.stream()
            .filter(GoogleCreatePhotoResult::hasNewMediaItem)
            .collect(Collectors.toList());

    if (resultsWithNewItems.isEmpty()) {
      logger.info("No new media items need to be created. userId {}", userId);
      return;
    }

    BatchCreateMediaItemsResponse response = photosLibraryClient.batchCreateMediaItems(resultsWithNewItems.stream()
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
        // our app can upload successfully, but the media item data will not be overwritten.
        // Also, for 1 and 2, our app can't add this photo to any album we created.
        // Therefore we need to assign different return status for this photo.
        // For photo, we compare filename in media item with from input (flickr url) to know if we need to assign
        // EXIST_CAN_NOT_CREATE status
        // This issue may not exist for video since every flickr video was encoded to mp4 (flickr api doesn't support original video)
        // Video files should have different checksum after encoding
        if (StringUtils.isNotBlank(mediaItem.getFilename())
                && !mediaItem.getFilename().equals(result.getNewMediaItem().getSimpleMediaItem().getFileName())) {
          logger.info("mediaItem was uploaded by user. mediaItem filename {} is not equal to input {}",
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

  void updateDefaultAlbumAndIdMapping(PhotosLibraryClient photosLibraryClient,
                                      String userId, Source source, List<GoogleCreatePhotoResult> results) {

    List<GoogleCreatePhotoResult> resultWithSuccessStatus = results.stream()
            .filter(r -> r.getStatus() == GoogleCreatePhotoResult.Status.SUCCESS)
            .collect(Collectors.toList());

    if (resultWithSuccessStatus.isEmpty()) {
      logger.info("Not media item to add default album and update id mapping. userId: {}", userId);
      return;
    }

    String defaultAlbumId = googleService.getDefaultAlbumId(photosLibraryClient, userId, source);

    logger.info("add {} new uploaded photo to default album {}", resultWithSuccessStatus.size(), defaultAlbumId);
    photosLibraryClient.batchAddMediaItemsToAlbum(defaultAlbumId,
            resultWithSuccessStatus.stream()
                    .map(GoogleCreatePhotoResult::getGoogleId)
                    .collect(Collectors.toList()));

    idMappingService.saveOrUpdateAll(
            resultWithSuccessStatus.stream()
                    .map(r -> new IdMapping(new IdMappingKey(userId, r.getSourceId()), r.getGoogleId()))
                    .collect(Collectors.toList()));
  }



  @PostMapping("/google/photo")
  public List<GoogleCreatePhotoResult> createPhotos(@RequestBody List<FlickrPhoto> sourcePhotos, @RequestParam String refreshToken,
                                                    @RequestParam(defaultValue = "FLICKR") Source source,
                                                    @RequestParam(defaultValue = "false") boolean forceUnique) throws IOException {
    if (sourcePhotos.size() > GOOGLE_BATCH_SIZE_MAX) {
      throw new IllegalArgumentException("Google Photo API only accept " + GOOGLE_BATCH_SIZE_MAX + " photos in each batch");
    }

    PhotosLibraryClient photosLibraryClient = googleService.getPhotosLibraryClient(refreshToken);
    String userId = googleService.getUserId(refreshToken);
    Map<String, String> existingSourceIdToGoogleId = idMappingService.findSourceIdToTargetIdMap(userId,
            sourcePhotos.stream()
                    .map(FlickrPhoto::getId)
                    .collect(Collectors.toList()));

    List<GoogleCreatePhotoResult> results = sourcePhotos.stream()
            .map(sourcePhoto -> downloadAndUploadSourcePhoto(
                    photosLibraryClient, existingSourceIdToGoogleId, sourcePhoto, forceUnique))
            .collect(Collectors.toList());

    createMediaItems(photosLibraryClient, userId, results);
    updateDefaultAlbumAndIdMapping(photosLibraryClient, userId, source, results);
    return results;
  }

  @PostMapping("/google/album")
  public GoogleCreateAlbumResult createAlbum(@RequestBody FlickrAlbum sourceAlbum, @RequestParam String refreshToken,
                                             @RequestParam(defaultValue = "FLICKR") Source   source) throws IOException {

    PhotosLibraryClient photosLibraryClient = googleService.getPhotosLibraryClient(refreshToken);
    String userId = googleService.getUserId(refreshToken);

    logger.info("start to create google album from source album id: {}", sourceAlbum.getId());
    Album googleAlbum = googleService.createAlbum(photosLibraryClient, sourceAlbum);

    Map<String, String> sourcePhotoIdToGooglePhotoId = idMappingService.findSourceIdToTargetIdMap(
            userId, sourceAlbum.getPhotoIds());
    List<String> googlePhotoIds = sourceAlbum.getPhotoIds().stream()
            .map(sourcePhotoIdToGooglePhotoId::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    int batchNum = 0;
    for (List<String> googlePhotoIdsBatch: Lists.partition(googlePhotoIds, GOOGLE_BATCH_SIZE_MAX)) {
      logger.info("Start to batchAddMediaItemsToAlbum. album id: {}, size: {}, batch: {}",
              googleAlbum.getId(), googlePhotoIdsBatch.size(), batchNum);
      photosLibraryClient.batchAddMediaItemsToAlbum(googleAlbum.getId(), googlePhotoIdsBatch);
      batchNum++;
    }

    String coverGooglePhotoId = sourcePhotoIdToGooglePhotoId.get(sourceAlbum.getCoverPhotoId());
    if (coverGooglePhotoId != null) {
      logger.info("start to update album cover,  album id: {}, cover id: {}", googleAlbum.getId(), coverGooglePhotoId);
      photosLibraryClient.updateAlbumCoverPhoto(googleAlbum, coverGooglePhotoId);
    }
    List<String> failedSourcePhotoIds = sourceAlbum.getPhotoIds().stream()
            .filter(id -> !sourcePhotoIdToGooglePhotoId.containsKey(id))
            .collect(Collectors.toList());

    return new GoogleCreateAlbumResult(googleAlbum.getId(), googleAlbum.getProductUrl(),
            googlePhotoIds.size(), failedSourcePhotoIds);
  }

}

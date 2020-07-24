package haojiwu.flickrtogooglephotos.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.common.collect.Lists;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.*;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import haojiwu.flickrtogooglephotos.model.*;
import haojiwu.flickrtogooglephotos.service.GoogleService;
import haojiwu.flickrtogooglephotos.service.IdMappingService;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
public class GoogleController {
  Logger logger = LoggerFactory.getLogger(GoogleController.class);
  private static final int GOOGLE_BATCH_SIZE_MAX = 50;

  @Value("${app.google.clientId}")
  private String clientId;
  @Value("${app.google.clientSecret}")
  private String clientSecret;
  @Value("${app.host}")
  private String appHost;
  @Value("${app.photoFolder}")
  private String photoFolder;


  @Autowired
  private IdMappingService idMappingService;

  @Autowired
  private GoogleService googleService;

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
  @ExceptionHandler({IllegalArgumentException.class,JsonParseException.class,GoogleJsonResponseException.class})
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



  Map<String, String> findSourceIdToGoogleIdMap(String userId, List<String> sourceIds) {
    Iterable<IdMapping> existingIdMappingIter = idMappingService.findAllByIds(userId, sourceIds);
    return StreamSupport.stream(existingIdMappingIter.spliterator(), false)
            .collect(Collectors.toMap(
                    mapping -> mapping.getIdMappingKey().getSourceId(),
                    IdMapping::getTargetId));
  }

  String downloadPhoto(FlickrPhoto sourcePhoto) throws IOException {
    String filename;
    if (sourcePhoto.getMedia().equals("video")) {
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

  String geotagPhoto(String photoLocalPath, float latitude, float longitude) {
    try {
      File inputFile = new File(photoLocalPath);
      final ImageMetadata metadata = Imaging.getMetadata(inputFile);
      if (metadata instanceof JpegImageMetadata) {
        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

        if (jpegMetadata.getExif() == null || jpegMetadata.getExif().getGPS() == null) {
          TiffOutputSet outputSet = null;
          final TiffImageMetadata exif = jpegMetadata.getExif();

          if (null != exif) {
            outputSet = exif.getOutputSet();
          }

          if (null == outputSet) {
            outputSet = new TiffOutputSet();
          }
          outputSet.setGPSInDegrees(longitude, latitude);
          int filenamePrefixIndex = photoLocalPath.lastIndexOf(".");
          String geotaggedPhotoLocalPath = photoLocalPath.substring(0, filenamePrefixIndex)
                  + "_geotagged" + photoLocalPath.substring(filenamePrefixIndex);

          try (FileOutputStream fileOutputStream = new FileOutputStream(geotaggedPhotoLocalPath);
               OutputStream outputStream = new BufferedOutputStream(fileOutputStream)) {
            new ExifRewriter().updateExifMetadataLossless(inputFile, outputStream, outputSet);
            logger.info("Geotag photo: {}, new path {}", photoLocalPath, geotaggedPhotoLocalPath);
            return geotaggedPhotoLocalPath;
          }
        } else {
          logger.info("Photo already has geotag {}", photoLocalPath);
        }
      }

    } catch (ImageReadException | ImageWriteException | IOException e) {
      logger.error("Fail to geotag photo {}", photoLocalPath, e);
    }
    return photoLocalPath;
  }

  GoogleCreatePhotoResult downloadAndUploadSourcePhoto(PhotosLibraryClient photosLibraryClient,
                                                       Map<String, String> existingSourceIdToGoogleId, FlickrPhoto sourcePhoto) {
    String sourceId = sourcePhoto.getId();
    if (existingSourceIdToGoogleId.containsKey(sourceId)) {
      logger.info("Don't need t to migrate {}", sourceId);
      return new GoogleCreatePhotoResult.Builder(sourceId)
              .setStatus(GoogleCreatePhotoResult.Status.EXISTING)
              .setGoogleId(existingSourceIdToGoogleId.get(sourceId))
              .build();
    }
    try {
      String photoLocalPath = downloadPhoto(sourcePhoto);
      if (sourcePhoto.getMedia().equals("photo")
              && sourcePhoto.getLatitude() != null
              && sourcePhoto.getLongitude() != null) {
        photoLocalPath = geotagPhoto(photoLocalPath, sourcePhoto.getLatitude(), sourcePhoto.getLongitude());
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
              .setStatus(GoogleCreatePhotoResult.Status.ERROR)
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
      } else {
        logger.error("fail to create mediaItem, error: {}, source id: {}", status.getMessage(), result.getSourceId());
        result.setStatus(GoogleCreatePhotoResult.Status.ERROR);
        result.setError(status.getMessage());
      }
    }
  }

  void updateDefaultAlbumAndIdMapping(PhotosLibraryClient photosLibraryClient,
                                      String userId, String source, List<GoogleCreatePhotoResult> results) {

    List<GoogleCreatePhotoResult> resultWithNewCreatedMediaItem = results.stream()
            .filter(GoogleCreatePhotoResult::hasNewMediaItem)
            .filter(GoogleCreatePhotoResult::hasGoogleId)
            .collect(Collectors.toList());

    if (resultWithNewCreatedMediaItem.isEmpty()) {
      logger.info("Not media item to add default album and update id mapping. userId: {}", userId);
      return;
    }

    String defaultAlbumId = googleService.getDefaultAlbumId(photosLibraryClient, userId, source);

    logger.info("add {} new uploaded photo to default album {}", resultWithNewCreatedMediaItem.size(), defaultAlbumId);
    photosLibraryClient.batchAddMediaItemsToAlbum(defaultAlbumId,
            resultWithNewCreatedMediaItem.stream()
                    .map(GoogleCreatePhotoResult::getGoogleId)
                    .collect(Collectors.toList()));

    idMappingService.saveOrUpdateAll(
            resultWithNewCreatedMediaItem.stream()
                    .map(r -> new IdMapping(new IdMappingKey(userId, r.getSourceId()), r.getGoogleId()))
                    .collect(Collectors.toList()));
  }



  @PostMapping("/google/photos")
  public List<GoogleCreatePhotoResult> createPhotos(@RequestBody List<FlickrPhoto> sourcePhotos, @RequestParam String refreshToken,
                                                 @RequestParam(defaultValue = "Flickr") String source) throws IOException {
    if (sourcePhotos.size() > GOOGLE_BATCH_SIZE_MAX) {
      throw new IllegalArgumentException("Google Photo API only accept " + GOOGLE_BATCH_SIZE_MAX + " photos in each batch");
    }

    if (!source.equals("Flickr")) {
      throw new IllegalArgumentException("We only support Flickr as photo source for now");
    }

    PhotosLibraryClient photosLibraryClient = googleService.getPhotosLibraryClient(refreshToken);
    String userId = googleService.getUserId(refreshToken);
    Map<String, String> existingSourceIdToGoogleId = findSourceIdToGoogleIdMap(userId,
            sourcePhotos.stream()
                    .map(FlickrPhoto::getId)
                    .collect(Collectors.toList()));

    List<GoogleCreatePhotoResult> results = sourcePhotos.stream()
            .map(sourcePhoto -> downloadAndUploadSourcePhoto(photosLibraryClient, existingSourceIdToGoogleId, sourcePhoto))
            .collect(Collectors.toList());

    createMediaItems(photosLibraryClient, userId, results);
    updateDefaultAlbumAndIdMapping(photosLibraryClient, userId, source, results);
    return results;
  }

  @PostMapping("/google/album")
  public GoogleCreateAlbumResult createAlbum(@RequestBody FlickrAlbum sourceAlbum, @RequestParam String refreshToken,
                       @RequestParam(defaultValue = "Flickr") String source) throws IOException {
    if (!source.equals("Flickr")) {
      throw new IllegalArgumentException("We only support Flickr as photo source for now");
    }

    PhotosLibraryClient photosLibraryClient = googleService.getPhotosLibraryClient(refreshToken);
    String userId = googleService.getUserId(refreshToken);

    logger.info("start to create google album from source album id: {}", sourceAlbum.getId());
    Album googleAlbum = googleService.createAlbum(photosLibraryClient, sourceAlbum);

    Map<String, String> sourcePhotoIdToGooglePhotoId = findSourceIdToGoogleIdMap(userId, sourceAlbum.getPhotoIds());
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

    return new GoogleCreateAlbumResult(googleAlbum.getId(), googleAlbum.getProductUrl(), googlePhotoIds.size());
  }

}

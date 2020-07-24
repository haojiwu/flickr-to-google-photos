package haojiwu.flickrtogooglephotos.controller;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.collect.Lists;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.proto.*;
import com.google.photos.library.v1.util.AlbumPositionFactory;
import com.google.photos.library.v1.util.NewEnrichmentItemFactory;
import com.google.photos.library.v1.util.NewMediaItemFactory;
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
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
public class GoogleController {
  Logger logger = LoggerFactory.getLogger(GoogleController.class);

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
  @ExceptionHandler({IllegalArgumentException.class})
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





  private static void printTagValue(final JpegImageMetadata jpegMetadata,
                                    final TagInfo tagInfo) {
    final TiffField field = jpegMetadata.findEXIFValueWithExactMatch(tagInfo);
    if (field == null) {
      System.out.println(tagInfo.name + ": " + "Not Found.");
    } else {
      System.out.println(tagInfo.name + ": "
              + field.getValueDescription());
    }
  }

  Set<String> buildExistingSourceIds(String userId, List<FlickrPhoto> sourcePhotos) {
    Iterable<IdMapping> existingIdMappingIter = idMappingService.findAllByIds(userId, sourcePhotos.stream()
            .map(FlickrPhoto::getId)
            .collect(Collectors.toList()));
    return StreamSupport.stream(existingIdMappingIter.spliterator(), false)
            .map(IdMapping::getIdMappingKey)
            .map(IdMappingKey::getSourceId)
            .collect(Collectors.toSet());
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


  @PostMapping("/google/photos")
  public List<GoogleAddPhotoResult> addPhotos(@RequestBody List<FlickrPhoto> sourcePhotos, @RequestParam String refreshToken,
                        @RequestParam(defaultValue = "Flickr") String source) throws IOException {
    if (sourcePhotos.size() > 50) {
      throw new IllegalArgumentException("Google Photo API only accept 50 photos in each batch");
    }

    PhotosLibraryClient photosLibraryClient = googleService.getPhotosLibraryClient(refreshToken);
    String userId = googleService.getUserId(refreshToken);
    String defaultAlbumId = googleService.getDefaultAlbumId(photosLibraryClient, userId, source);
    Set<String> existingSourceIds = buildExistingSourceIds(userId, sourcePhotos);


    List<GoogleAddPhotoResult> results = sourcePhotos.stream()
            .map(sourcePhoto -> {
                      if (existingSourceIds.contains(sourcePhoto.getId())) {
                        logger.info("Don't need t to migrate {}", sourcePhoto.getId());
                        return new GoogleAddPhotoResult.Builder(sourcePhoto.getId())
                                .setStatus(GoogleAddPhotoResult.Status.EXISTING)
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

                        return new GoogleAddPhotoResult.Builder(sourcePhoto.getId())
                                .setStatus(GoogleAddPhotoResult.Status.SUCCESS)
                                .setNewMediaItem(newMediaItem)
                                .build();
                      } catch (Exception e) {
                        logger.error("Error when add photo: {}", sourcePhoto.getId(), e);
                        return new GoogleAddPhotoResult.Builder(sourcePhoto.getId())
                                .setStatus(GoogleAddPhotoResult.Status.ERROR)
                                .setError(e.getMessage())
                                .build();
                      }
                    })
            .collect(Collectors.toList());

    List<GoogleAddPhotoResult> resultsWithNewItems = results.stream()
            .filter(GoogleAddPhotoResult::hasNewMediaItem)
            .collect(Collectors.toList());

    BatchCreateMediaItemsResponse response = photosLibraryClient.batchCreateMediaItems(resultsWithNewItems.stream()
            .map(GoogleAddPhotoResult::getNewMediaItem)
            .collect(Collectors.toList()));
    assert (response.getNewMediaItemResultsList().size() == resultsWithNewItems.size());
    for (int i = 0; i < response.getNewMediaItemResultsList().size(); i++) {
      NewMediaItemResult itemsResponse = response.getNewMediaItemResultsList().get(i);
      GoogleAddPhotoResult result = resultsWithNewItems.get(i);
      Status status = itemsResponse.getStatus();
      if (status.getCode() == Code.OK_VALUE) {
        // The item is successfully created in the user's library
        MediaItem mediaItem = itemsResponse.getMediaItem();
        logger.info("success create mediaItem: {}, source: {}", mediaItem, result.getSourceId());

        result.setGoogleId(mediaItem.getId());
        result.setUrl(mediaItem.getProductUrl());

      } else {
        logger.error("fail to create mediaItem, error: {}, source: {}", status.getMessage(), result.getSourceId());

        result.setStatus(GoogleAddPhotoResult.Status.ERROR);
        result.setError(status.getMessage());
      }
    }

    List<GoogleAddPhotoResult> resultWithGoogleId = results.stream()
            .filter(GoogleAddPhotoResult::hasGoogleId)
            .collect(Collectors.toList());

    logger.info("add {} new uploaded photo to default album {}", resultWithGoogleId.size(), defaultAlbumId);
    photosLibraryClient.batchAddMediaItemsToAlbum(defaultAlbumId,
            resultWithGoogleId.stream()
                    .map(GoogleAddPhotoResult::getGoogleId)
                    .collect(Collectors.toList()));

    idMappingService.saveOrUpdateAll(
            resultWithGoogleId.stream()
                    .map(r -> new IdMapping(new IdMappingKey(userId, r.getSourceId()), r.getGoogleId()))
                    .collect(Collectors.toList()));

    return results;
  }

  @PostMapping("/google/addAlbum")
  public void addAlbum(@RequestBody FlickrAlbum flickrAlbum, @RequestParam String refreshToken,
                       @RequestParam String flickrUserId) throws IOException {

    UserCredentials userCredentials = UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            //.setAccessToken(new AccessToken(accessToken, null))
            .setRefreshToken(refreshToken)
            .build();

    PhotosLibrarySettings settings =
            PhotosLibrarySettings.newBuilder()
                    .setCredentialsProvider(
                            FixedCredentialsProvider.create(userCredentials))
                    .build();

    try (PhotosLibraryClient photosLibraryClient =
                 PhotosLibraryClient.initialize(settings)) {

//      Album.Builder albumBuilder = Album.newBuilder()
//              .setTitle(flickrAlbum.getTitle());
//      idMappingService.findById(flickrUserId, flickrAlbum.getCoverPhotoId())
//              .map(IdMapping::getGoogleId)
//              .ifPresent(albumBuilder::setCoverPhotoMediaItemId);
//      Album googleAlbum = albumBuilder.build();

      Album googleAlbum = photosLibraryClient.createAlbum(flickrAlbum.getTitle());


      String textEnrichment = "";
      if (StringUtils.isNotBlank(flickrAlbum.getDescription())) {
        textEnrichment += flickrAlbum.getDescription() + "\n";
      }
      textEnrichment += flickrAlbum.getUrl();
      NewEnrichmentItem newEnrichmentItem =
              NewEnrichmentItemFactory.createTextEnrichment(textEnrichment);
      AlbumPosition albumPosition = AlbumPositionFactory.createFirstInAlbum();
      photosLibraryClient.addEnrichmentToAlbum(googleAlbum.getId(), newEnrichmentItem, albumPosition);

      Iterable<IdMapping> googlePhotoIdsIter = idMappingService.findAllByIds(flickrUserId, flickrAlbum.getPhotoIds());
      Map<String, String> flickrPhotoIdToGooglePhotoId = StreamSupport.stream(googlePhotoIdsIter.spliterator(), false)
              .collect(Collectors.toMap(m -> m.getIdMappingKey().getSourceId(), IdMapping::getTargetId));

      List<String> googlePhotoIds = flickrAlbum.getPhotoIds().stream()
              .map(flickrPhotoIdToGooglePhotoId::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      for (List<String> googlePhotoIdsPart: Lists.partition(googlePhotoIds, 4)) {
        logger.info("Start to batchAddMediaItemsToAlbum {}", googlePhotoIdsPart.size());
        photosLibraryClient.batchAddMediaItemsToAlbum(googleAlbum.getId(), googlePhotoIdsPart);
      }

      logger.info("googleAlbum id {}", googleAlbum.getId());
      idMappingService.findById(flickrUserId, flickrAlbum.getCoverPhotoId())
              .map(IdMapping::getTargetId)
              .ifPresent(googleCoverPhotoId -> {
                        logger.info("googleCoverPhotoId {}", googleCoverPhotoId);
                        photosLibraryClient.updateAlbumCoverPhoto(googleAlbum, googleCoverPhotoId);
                      }
              );

    } catch (ApiException e) {
      logger.error("exception 3", e);
      // Error during album creation
    }

  }

}

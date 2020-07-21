package haojiwu.flickrtogooglephotos.controller;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.collect.Lists;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.proto.*;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.library.v1.util.AlbumPositionFactory;
import com.google.photos.library.v1.util.NewEnrichmentItemFactory;
import com.google.photos.library.v1.util.NewMediaItemFactory;
import com.google.photos.types.proto.Album;
import com.google.photos.types.proto.MediaItem;
import com.google.rpc.Code;
import com.google.rpc.Status;
import haojiwu.flickrtogooglephotos.model.*;
import haojiwu.flickrtogooglephotos.service.IdMappingService;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

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

  @Value("${app.google.clientId}")
  private String clientId;
  @Value("${app.google.clientSecret}")
  private String clientSecret;
  @Value("${app.host}")
  private String appHost;
  @Value("${app.photoFolder}")
  private String photoFolder;


  @Autowired
  IdMappingService idMappingService;

  @RequestMapping("/google/auth")
  public void auth(HttpServletResponse response) throws IOException {
    AuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            new NetHttpTransport(), JacksonFactory.getDefaultInstance(),
            clientId,
            clientSecret,
            Arrays.asList("https://www.googleapis.com/auth/photoslibrary",
                    "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata"))
            .setAccessType("offline").setApprovalPrompt("force").build();
    String url = flow.newAuthorizationUrl().setState("xyz")
            .setRedirectUri(appHost + "/google/auth/complete").build();

    response.sendRedirect(url);
  }

  @RequestMapping("/google/auth/complete")
  public GoogleCredential complete(@RequestParam String state, @RequestParam String code) throws IOException {
    AuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            new NetHttpTransport(), JacksonFactory.getDefaultInstance(),
            clientId,
            clientSecret,
            Arrays.asList("https://www.googleapis.com/auth/photoslibrary",
                    "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata"))
            .setAccessType("offline").setApprovalPrompt("force").build();
    TokenResponse tokenResponse = flow.newTokenRequest(code)
            .setRedirectUri(appHost + "/google/auth/complete").execute();
    System.out.println("tokenResponse=" + tokenResponse.getAccessToken() + ", " + tokenResponse.getRefreshToken());

    return new GoogleCredential(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

//
//    Credential credential = flow.createAndStoreCredential(tokenResponse, null);
//    System.out.println("credential=" + credential.getAccessToken() + ", " + credential.getRefreshToken());
//
//    UserCredentials userCredentials = UserCredentials.newBuilder()
//            .setClientId("753348695249-nvvh6gu47m8j70kgiu41j6v1l8ajfkvu.apps.googleusercontent.com")
//            .setClientSecret("08Cm2CQx3EIxFwzuPvmzGLSP")
//            .setAccessToken(new AccessToken(credential.getAccessToken(), null))
//            .setRefreshToken(credential.getRefreshToken())
//            .build();
//
//    PhotosLibrarySettings settings =
//            PhotosLibrarySettings.newBuilder()
//                    .setCredentialsProvider(
//                            FixedCredentialsProvider.create(userCredentials))
//                    .build();
//
//    try (PhotosLibraryClient photosLibraryClient =
//                 PhotosLibraryClient.initialize(settings)) {
//
//      // Create a new Album  with at title
//      Album createdAlbum = photosLibraryClient.createAlbum("My Album");
//
//      // Get some properties from the album, such as its ID and product URL
//      String id = createdAlbum.getId();
//      String url = createdAlbum.getProductUrl();
//
//      System.out.println("id=" + id + ", url=" + url);
//    } catch (ApiException e) {
//      logger.error("exception", e);
//      // Error during album creation
//    }
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
  @PostMapping("/google/addPhotos")
  public void addPhotos(@RequestBody List<FlickrPhoto> flickrPhotos, @RequestParam String refreshToken,
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
      String googleAlbumId = idMappingService.findById(flickrUserId, flickrUserId)
              .map(IdMapping::getGoogleId)
              .orElse(null);
      if (googleAlbumId == null) {
        // TODO: remove it
        for (Album album: photosLibraryClient.listAlbums(true).iterateAll()) {
          if (album.getTitle().equals("Moved from Flickr")) {
            googleAlbumId = album.getId();
            break;
          }
        }
        if (googleAlbumId == null) {
          googleAlbumId = photosLibraryClient.createAlbum("Moved from Flickr").getId();
          idMappingService.saveOrUpdate(flickrUserId, flickrUserId, googleAlbumId);
        }
      }

      List<NewMediaItem> newItems = new ArrayList<>();


      Iterable<IdMapping> existingIdMappingIter = idMappingService.findAllByIds(flickrUserId, flickrPhotos.stream()
              .map(FlickrPhoto::getId).collect(Collectors.toList()));
      Set<String> existingFlickrPhotoIds = StreamSupport.stream(existingIdMappingIter.spliterator(), false)
              .map(IdMapping::getFlickrId)
              .map(FlickrId::getFlickrEntityId)
              .collect(Collectors.toSet());

      for (FlickrPhoto flickrPhoto: flickrPhotos) {
        logger.info(flickrPhoto.toString());
        if (existingFlickrPhotoIds.contains(flickrPhoto.getId())) {
          logger.info("Don't need t to migrate {}", flickrPhoto.getId());
          continue;
        }
        InputStream in = new URL(flickrPhoto.getPhotoUrl()).openStream();
        Path path = Paths.get(photoFolder + "/" + flickrPhoto.getId() + ".jpg");
        Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);

        try {
          File file = new File(path.toString());
          final ImageMetadata metadata = Imaging.getMetadata(file);
          if (metadata instanceof JpegImageMetadata) {
            final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

            printTagValue(jpegMetadata,
                    GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
            printTagValue(jpegMetadata, GpsTagConstants.GPS_TAG_GPS_LATITUDE);
            printTagValue(jpegMetadata,
                    GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
            printTagValue(jpegMetadata, GpsTagConstants.GPS_TAG_GPS_LONGITUDE);

            final TiffImageMetadata exifMetadata = jpegMetadata.getExif();
            if (null != exifMetadata) {
              final TiffImageMetadata.GPSInfo gpsInfo = exifMetadata.getGPS();
              if (null != gpsInfo) {
                final String gpsDescription = gpsInfo.toString();

                final double longitude = gpsInfo.getLongitudeAsDegreesEast();
                final double latitude = gpsInfo.getLatitudeAsDegreesNorth();

                System.out.println("    " + "GPS Description: "
                        + gpsDescription);
                System.out.println("    "
                        + "GPS Longitude (Degrees East): " + longitude);
                System.out.println("    "
                        + "GPS Latitude (Degrees North): " + latitude);
              }
            }

            // more specific example of how to manually access GPS values
            final TiffField gpsLatitudeRefField = jpegMetadata.findEXIFValueWithExactMatch(
                    GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
            final TiffField gpsLatitudeField = jpegMetadata.findEXIFValueWithExactMatch(
                    GpsTagConstants.GPS_TAG_GPS_LATITUDE);
            final TiffField gpsLongitudeRefField = jpegMetadata.findEXIFValueWithExactMatch(
                    GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
            final TiffField gpsLongitudeField = jpegMetadata.findEXIFValueWithExactMatch(
                    GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
            if (gpsLatitudeRefField != null && gpsLatitudeField != null &&
                    gpsLongitudeRefField != null &&
                    gpsLongitudeField != null) {
              // all of these values are strings.
              final String gpsLatitudeRef = (String) gpsLatitudeRefField.getValue();
              final RationalNumber gpsLatitude[] = (RationalNumber[]) (gpsLatitudeField.getValue());
              final String gpsLongitudeRef = (String) gpsLongitudeRefField.getValue();
              final RationalNumber gpsLongitude[] = (RationalNumber[]) gpsLongitudeField.getValue();

              final RationalNumber gpsLatitudeDegrees = gpsLatitude[0];
              final RationalNumber gpsLatitudeMinutes = gpsLatitude[1];
              final RationalNumber gpsLatitudeSeconds = gpsLatitude[2];

              final RationalNumber gpsLongitudeDegrees = gpsLongitude[0];
              final RationalNumber gpsLongitudeMinutes = gpsLongitude[1];
              final RationalNumber gpsLongitudeSeconds = gpsLongitude[2];

              // This will format the gps info like so:
              //
              // gpsLatitude: 8 degrees, 40 minutes, 42.2 seconds S
              // gpsLongitude: 115 degrees, 26 minutes, 21.8 seconds E

              System.out.println("    " + "GPS Latitude: "
                      + gpsLatitudeDegrees.toDisplayString() + " degrees, "
                      + gpsLatitudeMinutes.toDisplayString() + " minutes, "
                      + gpsLatitudeSeconds.toDisplayString() + " seconds "
                      + gpsLatitudeRef);
              System.out.println("    " + "GPS Longitude: "
                      + gpsLongitudeDegrees.toDisplayString() + " degrees, "
                      + gpsLongitudeMinutes.toDisplayString() + " minutes, "
                      + gpsLongitudeSeconds.toDisplayString() + " seconds "
                      + gpsLongitudeRef);

            }

            if ((jpegMetadata.getExif() == null || jpegMetadata.getExif().getGPS() == null)
                    && flickrPhoto.getLatitude() != null
                    && flickrPhoto.getLongitude() != null) {
              TiffOutputSet outputSet = null;
              // note that exif might be null if no Exif metadata is found.
              final TiffImageMetadata exif = jpegMetadata.getExif();

              if (null != exif) {
                // TiffImageMetadata class is immutable (read-only).
                // TiffOutputSet class represents the Exif data to write.
                //
                // Usually, we want to update existing Exif metadata by
                // changing
                // the values of a few fields, or adding a field.
                // In these cases, it is easiest to use getOutputSet() to
                // start with a "copy" of the fields read from the image.
                outputSet = exif.getOutputSet();
              }

              if (null == outputSet) {
                outputSet = new TiffOutputSet();
              }
              outputSet.setGPSInDegrees(flickrPhoto.getLongitude(), flickrPhoto.getLatitude());
              Path newPath = Paths.get(photoFolder + "/" + flickrPhoto.getId() + "_new.jpg");
              FileOutputStream fileOutputStream = new FileOutputStream(newPath.toString());
              OutputStream outputStream = new BufferedOutputStream(fileOutputStream);
              new ExifRewriter().updateExifMetadataLossless(file, outputStream,
                      outputSet);
              path = newPath;
            }

          }


        } catch (ImageReadException | ImageWriteException e) {
          logger.error("exception", e);
        }

        // Open the file and automatically close it after upload
        try (RandomAccessFile file = new RandomAccessFile(path.toString(), "r")) {
          // Create a new upload request
          UploadMediaItemRequest uploadRequest =
                  UploadMediaItemRequest.newBuilder()
                          // The media type (e.g. "image/png")
                          .setMimeType("image/png")
                          // The file to upload
                          .setDataFile(file)
                          .build();
          // Upload and capture the response
          UploadMediaItemResponse uploadResponse = photosLibraryClient.uploadMediaItem(uploadRequest);
          if (uploadResponse.getError().isPresent()) {
            // If the upload results in an error, handle it
            logger.error("upload fail", uploadResponse.getError().get().getCause());
          } else {
            // If the upload is successful, get the uploadToken
            String uploadToken = uploadResponse.getUploadToken().get();
            logger.info("upload success: uploadToken=" + uploadToken);
            // Use this upload token to create a media item

            String itemDescription = "";
            if (StringUtils.isNotBlank(flickrPhoto.getTitle())) {
              itemDescription += flickrPhoto.getTitle() + "\n";
            }
            if (StringUtils.isNotBlank(flickrPhoto.getDescription())) {
              itemDescription += flickrPhoto.getDescription() + "\n";
            }
            for (String tag: flickrPhoto.getTags()) {
              itemDescription += "#" + tag + " ";
            }
            try {
              // Create a NewMediaItem with the following components:
              // - uploadToken obtained from the previous upload request
              // - filename that will be shown to the user in Google Photos
              // - description that will be shown to the user in Google Photos
              NewMediaItem newMediaItem = NewMediaItemFactory
                      .createNewMediaItem(uploadToken, flickrPhoto.getFlickrUrl(), itemDescription);
              newItems.add(newMediaItem);

            } catch (ApiException e) {
              logger.error("ApiException", e);
              // Handle error
            }

          }
        } catch (ApiException e) {
          logger.error("ApiException 2", e);

          // Handle error
        } catch (IOException e) {
          logger.error("IOException 2", e);

          // Error accessing the local file
        }
      }

      if (!newItems.isEmpty()) {

        Map<String, String> filenameToFlickrPhotoId = flickrPhotos.stream()
                .collect(Collectors.toMap(FlickrPhoto::getFlickrUrl, FlickrPhoto::getId));

        List<MediaItem> successMediaItems = new ArrayList<>();
        BatchCreateMediaItemsResponse response = photosLibraryClient.batchCreateMediaItems(newItems);
        for (NewMediaItemResult itemsResponse : response.getNewMediaItemResultsList()) {
          Status status = itemsResponse.getStatus();
          if (status.getCode() == Code.OK_VALUE) {
            // The item is successfully created in the user's library
            MediaItem createdItem = itemsResponse.getMediaItem();
            successMediaItems.add(createdItem);

          } else {
            logger.error("fail " + status.getMessage());
            // The item could not be created. Check the status and try again
          }
        }



        photosLibraryClient.batchAddMediaItemsToAlbum(googleAlbumId,
                successMediaItems.stream()
                        .map(MediaItem::getId)
                        .collect(Collectors.toList()));
        List<IdMapping> idMappings = successMediaItems.stream()
                .map(mediaItem -> {
                  String flickrPhotoId = filenameToFlickrPhotoId.get(mediaItem.getFilename());
                  return new IdMapping(new FlickrId(flickrUserId, flickrPhotoId), mediaItem.getId());
                })
                .collect(Collectors.toList());
        idMappingService.saveOrUpdateAll(idMappings);

      }

    } catch (ApiException e) {
      logger.error("exception 3", e);
      // Error during album creation
    }
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
              .collect(Collectors.toMap(m -> m.getFlickrId().getFlickrEntityId(), IdMapping::getGoogleId));

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
              .map(IdMapping::getGoogleId)
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

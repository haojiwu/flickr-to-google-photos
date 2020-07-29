package haojiwu.flickrtogooglephotos.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.common.collect.Lists;
import com.google.photos.types.proto.Album;
import haojiwu.flickrtogooglephotos.model.FlickrAlbum;
import haojiwu.flickrtogooglephotos.model.FlickrPhoto;
import haojiwu.flickrtogooglephotos.model.GoogleCreateAlbumResult;
import haojiwu.flickrtogooglephotos.model.GoogleCreatePhotoResult;
import haojiwu.flickrtogooglephotos.model.GoogleCredential;
import haojiwu.flickrtogooglephotos.model.Source;
import haojiwu.flickrtogooglephotos.service.GoogleService;
import haojiwu.flickrtogooglephotos.service.IdMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class GoogleController {
  Logger logger = LoggerFactory.getLogger(GoogleController.class);
  private static final int GOOGLE_BATCH_SIZE_MAX = 50;

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

  @PostMapping("/google/photo")
  public List<GoogleCreatePhotoResult> createPhotos(@RequestBody List<FlickrPhoto> sourcePhotos,
                                                    @RequestParam String refreshToken,
                                                    @RequestParam(defaultValue = "FLICKR") Source source,
                                                    @RequestParam(defaultValue = "false") boolean forceUnique) throws IOException {
    if (sourcePhotos.size() > GOOGLE_BATCH_SIZE_MAX) {
      throw new IllegalArgumentException("Google Photo API only accept " + GOOGLE_BATCH_SIZE_MAX + " photos in each batch");
    }

    String userId = googleService.getUserId(refreshToken);
    Map<String, String> existingSourceIdToGoogleId = idMappingService.findSourceIdToTargetIdMap(userId,
            sourcePhotos.stream()
                    .map(FlickrPhoto::getId)
                    .collect(Collectors.toList()));

    List<GoogleCreatePhotoResult> results = sourcePhotos.stream()
            .map(sourcePhoto -> googleService.downloadAndUploadSourcePhoto(
                    refreshToken, existingSourceIdToGoogleId, sourcePhoto, forceUnique))
            .collect(Collectors.toList());

    googleService.createMediaItems(refreshToken, userId, results);
    googleService.updateDefaultAlbumAndIdMapping(refreshToken, userId, source, results);
    return results;
  }

  @PostMapping("/google/album")
  public GoogleCreateAlbumResult createAlbum(@RequestBody FlickrAlbum sourceAlbum,
                                             @RequestParam String refreshToken,
                                             @RequestParam(defaultValue = "FLICKR") Source source) throws IOException {

    String userId = googleService.getUserId(refreshToken);

    logger.info("start to create google album from source album id: {}", sourceAlbum.getId());
    Album googleAlbum = googleService.createAlbum(refreshToken, sourceAlbum);

    Map<String, String> sourcePhotoIdToGooglePhotoId = idMappingService.findSourceIdToTargetIdMap(
            userId, sourceAlbum.getPhotoIds());
    List<String> googlePhotoIds = sourceAlbum.getPhotoIds().stream()
            .map(sourcePhotoIdToGooglePhotoId::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    int batchNum = 0; // just for logging
    for (List<String> googlePhotoIdsBatch: Lists.partition(googlePhotoIds, GOOGLE_BATCH_SIZE_MAX)) {
      logger.info("Start to batchAddMediaItemsToAlbum. album id: {}, size: {}, batch: {}",
              googleAlbum.getId(), googlePhotoIdsBatch.size(), batchNum);
      googleService.batchAddMediaItemsToAlbum(refreshToken, googleAlbum.getId(), googlePhotoIdsBatch);
      batchNum++;
    }

    String coverGooglePhotoId = sourcePhotoIdToGooglePhotoId.get(sourceAlbum.getCoverPhotoId());
    if (coverGooglePhotoId != null) {
      logger.info("start to update album cover,  album id: {}, cover id: {}", googleAlbum.getId(), coverGooglePhotoId);
      googleService.updateAlbumCoverPhoto(refreshToken, googleAlbum, coverGooglePhotoId);
    }

    List<String> failedSourcePhotoIds = sourceAlbum.getPhotoIds().stream()
            .filter(id -> !sourcePhotoIdToGooglePhotoId.containsKey(id))
            .collect(Collectors.toList());

    return new GoogleCreateAlbumResult(googleAlbum.getId(), googleAlbum.getProductUrl(),
            googlePhotoIds.size(), failedSourcePhotoIds);
  }

}

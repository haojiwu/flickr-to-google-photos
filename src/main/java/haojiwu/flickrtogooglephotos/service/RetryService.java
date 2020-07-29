package haojiwu.flickrtogooglephotos.service;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photosets.Photosets;
import com.google.api.gax.rpc.ApiException;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient;
import com.google.photos.library.v1.proto.AddEnrichmentToAlbumResponse;
import com.google.photos.library.v1.proto.AlbumPosition;
import com.google.photos.library.v1.proto.BatchAddMediaItemsToAlbumResponse;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsResponse;
import com.google.photos.library.v1.proto.NewEnrichmentItem;
import com.google.photos.library.v1.proto.NewMediaItem;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.types.proto.Album;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

@Service
public class RetryService {
  private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
  private static final int MAX_ATTEMPTS = 100;
  private static final long DELAY = 30000;
  private static final double MULTIPLIER = 2;


  @Retryable(
          value = { FlickrException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public PhotoList<Photo> flickrPeopleGetPhotos(Flickr flickr, Auth auth,
                                                Set<String> extras, int perPage, int page) throws FlickrException {
    logger.info("flickrPeopleGetPhotos with retry page: {}", page);
    RequestContext.getRequestContext().setAuth(auth);
    return flickr.getPeopleInterface().getPhotos("me", null, null, null,
            null, null, null, null, extras, perPage, page);
  }

  @Retryable(
          value = { FlickrException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public Photosets flickrPhotosetsGetList(Flickr flickr, Auth auth,
                                          String userId, int pageSize, int page) throws FlickrException {
    logger.info("flickrPhotosetsGetList with retry userId: {}, page: {}", userId, page);
    RequestContext.getRequestContext().setAuth(auth);
    return flickr.getPhotosetsInterface().getList(userId, pageSize, page, null);
  }

  @Retryable(
          value = { FlickrException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public PhotoList<Photo> flickrPhotosetsGetPhotos(Flickr flickr, Auth auth,
                                                   String photosetId, int perPage, int page) throws FlickrException {
    logger.info("flickrPhotosetsGetPhotos with retry photosetId: {}, page: {}", photosetId, page);
    RequestContext.getRequestContext().setAuth(auth);
    return flickr.getPhotosetsInterface().getPhotos(photosetId, perPage, page);
  }


  @Retryable(
          value = { IOException.class },
          maxAttempts = 10,
          backoff = @Backoff(delay = 3000))
  public void downloadFile(String url, String localPath) throws IOException {
    logger.info("downloadFile with retry url: {}, localPath: {}", url, localPath);
    try(InputStream in = new URL(url).openStream()) {
      Path path = Paths.get(localPath);
      Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public BatchCreateMediaItemsResponse batchCreateMediaItems(PhotosLibraryClient photosLibraryClient,
                                                             List<NewMediaItem> newMediaItems) {
    logger.info("batchCreateMediaItems with retry newMediaItems size: {}", newMediaItems.size());
    return photosLibraryClient.batchCreateMediaItems(newMediaItems);
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public InternalPhotosLibraryClient.ListAlbumsPagedResponse listAlbums(PhotosLibraryClient photosLibraryClient,
                                                                        boolean excludeNonAppCreatedData) {
    logger.info("listAlbums with retry");
    return photosLibraryClient.listAlbums(excludeNonAppCreatedData);
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public Album createAlbum(PhotosLibraryClient photosLibraryClient,
                           String albumTitle) {
    logger.info("createAlbum with retry albumTitle: {}", albumTitle);
    return photosLibraryClient.createAlbum(albumTitle);
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public UploadMediaItemResponse uploadMediaItem(PhotosLibraryClient photosLibraryClient,
                                                       UploadMediaItemRequest request) {
    logger.info("uploadMediaItem with retry");
    return photosLibraryClient.uploadMediaItem(request);
  }


  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public AddEnrichmentToAlbumResponse addEnrichmentToAlbum(PhotosLibraryClient photosLibraryClient,
                                                           String albumId,
                                                           NewEnrichmentItem newEnrichmentItem,
                                                           AlbumPosition albumPosition) {
    logger.info("addEnrichmentToAlbum with retry albumId: {}", albumId);
    return photosLibraryClient.addEnrichmentToAlbum(albumId, newEnrichmentItem, albumPosition);
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public BatchAddMediaItemsToAlbumResponse batchAddMediaItemsToAlbum(PhotosLibraryClient photosLibraryClient,
                                                                     String albumId,
                                                                     List<String> mediaItemIds) {
    logger.info("batchAddMediaItemsToAlbum with retry albumId: {}", albumId);
    return photosLibraryClient.batchAddMediaItemsToAlbum(albumId, mediaItemIds);
  }

  @Retryable(
          value = { ApiException.class },
          maxAttempts = MAX_ATTEMPTS,
          backoff = @Backoff(delay = DELAY, multiplier = MULTIPLIER))
  public Album updateAlbumCoverPhoto(PhotosLibraryClient photosLibraryClient,
                                     Album album,
                                     String newCoverPhotoMediaItemId) {
    logger.info("updateAlbumCoverPhoto with retry albumId: {}", album.getId());
    return photosLibraryClient.updateAlbumCoverPhoto(album, newCoverPhotoMediaItemId);
  }

}

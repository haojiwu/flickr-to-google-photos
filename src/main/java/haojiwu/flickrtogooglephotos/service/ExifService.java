package haojiwu.flickrtogooglephotos.service;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Service
public class ExifService {
  private static final Logger logger = LoggerFactory.getLogger(ExifService.class);
  private final ExifRewriter exifRewriter = new ExifRewriter();

  private static TiffOutputSet getOrCreateOutputSet(TiffImageMetadata exif) throws ImageWriteException {
    if (exif != null) {
      return exif.getOutputSet();
    }
    return new TiffOutputSet();
  }

  private static boolean addGps(TiffImageMetadata exif, TiffOutputSet outputSet,
                                        Request request) throws ImageReadException, ImageWriteException {
    if (request.latitude != null && request.longitude != null) {
      if (exif == null || exif.getGPS() == null) {
        outputSet.setGPSInDegrees(request.longitude, request.latitude);
        logger.info("add geotag to {}", request.photoLocalPath);
        return true;
      }
    }
    return false;
  }

  private static boolean addUserComment(JpegImageMetadata jpegMetadata, TiffOutputSet outputSet,
                                        Request request) throws ImageWriteException {
    if (request.userComment != null) {
      final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
      TiffField field = jpegMetadata.findEXIFValueWithExactMatch(ExifTagConstants.EXIF_TAG_USER_COMMENT);
      String input = request.userComment;
      if (field != null) {
        String existingUserComment = null;
        try {
          existingUserComment = field.getStringValue().trim();
        } catch (ImageReadException e) {
          logger.error("read existingUserComment fail", e);
        }

        if (StringUtils.isNotBlank(existingUserComment)) {
          input = field.getValueDescription() + " " + input;
        }
      }
      exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT);
      exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, input);
      logger.info("add user comment \"{}\" to {}", input, request.photoLocalPath);
      return true;
    }
    return false;
  }

  private static String getTaggedFilePath(String photoLocalPath) {
    int filenamePrefixIndex = photoLocalPath.lastIndexOf(".");
    return photoLocalPath.substring(0, filenamePrefixIndex)
            + "_tagged" + photoLocalPath.substring(filenamePrefixIndex);
  }

  private void writeOutputSet(File inputFile, String taggedPhotoLocalPath,
                              TiffOutputSet outputSet) throws IOException, ImageWriteException, ImageReadException {
    try (FileOutputStream fileOutputStream = new FileOutputStream(taggedPhotoLocalPath);
         OutputStream outputStream = new BufferedOutputStream(fileOutputStream)) {
      exifRewriter.updateExifMetadataLossless(inputFile, outputStream, outputSet);
      logger.info("tag photo: {}, new path {}", inputFile.getPath(), taggedPhotoLocalPath);
    }
  }

  public String tagPhoto(Request request) {
    String photoLocalPath = request.photoLocalPath;
    try {
      File inputFile = new File(photoLocalPath);
      final ImageMetadata metadata = Imaging.getMetadata(inputFile);
      if (metadata instanceof JpegImageMetadata) {
        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        final TiffImageMetadata exif = jpegMetadata.getExif();
        TiffOutputSet outputSet = getOrCreateOutputSet(exif);

        boolean changed = addGps(exif, outputSet, request) | addUserComment(jpegMetadata, outputSet, request);
        if (changed) {
          String taggedPhotoLocalPath = getTaggedFilePath(photoLocalPath);
          writeOutputSet(inputFile, taggedPhotoLocalPath, outputSet);
          return taggedPhotoLocalPath;
        }
      }
    } catch (ImageReadException | ImageWriteException | IOException e) {
      logger.error("Fail to append user comment to photo {}", photoLocalPath, e);
    }
    return photoLocalPath;
  }

  public static class Request {
    public final String photoLocalPath;
    public Float latitude;
    public Float longitude;
    public String userComment;

    public Request(String photoLocalPath) {
      this.photoLocalPath = photoLocalPath;
    }
  }

}

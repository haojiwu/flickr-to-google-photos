package haojiwu.flickrtogooglephotos.service;

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
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Service
public class GeotagService {
  private static final Logger logger = LoggerFactory.getLogger(GeotagService.class);
  private final ExifRewriter exifRewriter = new ExifRewriter();

  public String geotagPhoto(String photoLocalPath, float latitude, float longitude) {
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
            exifRewriter.updateExifMetadataLossless(inputFile, outputStream, outputSet);
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
}

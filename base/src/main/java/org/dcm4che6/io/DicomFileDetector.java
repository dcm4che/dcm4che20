package org.dcm4che6.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class DicomFileDetector extends FileTypeDetector {

    public static final String APPLICATION_DICOM = "application/dicom";

    @Override
    public String probeContentType(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] b = new byte[132];
            return (in.readNBytes(b, 0, 132) == 132
                        && b[128] == 'D'
                        && b[129] == 'I'
                        && b[130] == 'C'
                        && b[131] == 'M')
                    ? APPLICATION_DICOM
                    : null;
        }
    }
}

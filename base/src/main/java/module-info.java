import org.dcm4che6.data.ElementDictionary;
import org.dcm4che6.io.DicomFileDetector;

import java.nio.file.spi.FileTypeDetector;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.base {
    exports org.dcm4che6.data;
    exports org.dcm4che6.io;
    exports org.dcm4che6.util;
    exports org.dcm4che6.util.function;

    uses ElementDictionary;

    provides FileTypeDetector with DicomFileDetector;
}

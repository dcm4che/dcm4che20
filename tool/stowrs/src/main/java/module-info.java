import java.nio.file.spi.FileTypeDetector;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che.tool.stowrs {
    requires org.dcm4che.base;
    requires org.dcm4che.json;
    requires org.dcm4che.xml;
    requires info.picocli;
    requires java.json;
    requires java.net.http;
    requires java.xml;

    uses FileTypeDetector;

    opens org.dcm4che6.tool.stowrs to info.picocli;
}
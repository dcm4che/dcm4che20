import java.nio.file.spi.FileTypeDetector;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.wadors {
    requires info.picocli;
    requires java.net.http;

    opens org.dcm4che6.tool.wadors to info.picocli;
}
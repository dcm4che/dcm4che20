/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.jpg2dcm {
    requires org.dcm4che.base;
    requires org.dcm4che.codec;
    requires org.dcm4che.xml;
    requires info.picocli;
    requires java.xml;

    uses java.nio.file.spi.FileTypeDetector;

    opens org.dcm4che6.tool.jpg2dcm to info.picocli;
}
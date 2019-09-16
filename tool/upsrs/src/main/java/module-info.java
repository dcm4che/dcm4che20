/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
module org.dcm4che.tool.upsrs {
    requires org.dcm4che.base;
    requires org.dcm4che.json;
    requires org.dcm4che.xml;
    requires info.picocli;
    requires java.json;
    requires java.net.http;
    requires java.xml;

    opens org.dcm4che6.tool.upsrs to info.picocli;
}
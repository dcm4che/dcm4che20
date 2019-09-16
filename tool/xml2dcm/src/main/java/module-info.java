/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.xml2dcm {
    requires org.dcm4che.base;
    requires org.dcm4che.xml;
    requires info.picocli;

    opens org.dcm4che6.tool.xml2dcm to info.picocli;
}
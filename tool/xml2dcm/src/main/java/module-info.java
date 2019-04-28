/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che6.tool.xml2dcm {
    requires org.dcm4che6.xml;
    requires info.picocli;
    opens org.dcm4che6.tool.xml2dcm to info.picocli;
}
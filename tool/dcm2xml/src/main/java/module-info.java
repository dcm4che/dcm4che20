/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che.tool.dcm2xml {
    requires org.dcm4che.xml;
    requires info.picocli;
    opens org.dcm4che6.tool.dcm2xml to info.picocli;
}
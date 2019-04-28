/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che6.tool.dcm2json {
    requires org.dcm4che6.json;
    requires info.picocli;
    opens org.dcm4che6.tool.dcm2json to info.picocli;
}
/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
module org.dcm4che6.tool.json2dcm {
    requires org.dcm4che6.json;
    requires info.picocli;
    opens org.dcm4che6.tool.json2dcm to info.picocli;
}
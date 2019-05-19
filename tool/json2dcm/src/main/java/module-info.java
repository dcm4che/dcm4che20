/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
module org.dcm4che.tool.json2dcm {
    requires org.dcm4che.base;
    requires org.dcm4che.json;
    requires java.json;
    requires info.picocli;

    opens org.dcm4che6.tool.json2dcm to info.picocli;
}
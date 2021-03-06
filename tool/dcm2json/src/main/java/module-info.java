/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.dcm2json {
    requires org.dcm4che.base;
    requires org.dcm4che.json;
    requires java.json;
    requires info.picocli;

    opens org.dcm4che6.tool.dcm2json to info.picocli;
}
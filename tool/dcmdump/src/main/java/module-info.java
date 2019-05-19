/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2018
 */
module org.dcm4che.tool.dcmdump {
    requires org.dcm4che.base;
    requires info.picocli;

    opens org.dcm4che6.tool.dcmdump to info.picocli;
}
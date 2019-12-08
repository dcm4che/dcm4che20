/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.echoscu {
    requires org.dcm4che.base;
    requires org.dcm4che.conf.model;
    requires org.dcm4che.net;
    requires info.picocli;

    opens org.dcm4che6.tool.echoscu to info.picocli;
}
/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2019
 */
module org.dcm4che.tool.storescp {
    requires org.dcm4che.base;
    requires org.dcm4che.conf.model;
    requires org.dcm4che.net;
    requires org.slf4j;
    requires info.picocli;

    opens org.dcm4che6.tool.storescp to info.picocli;
}
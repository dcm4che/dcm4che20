/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2019
 */
module org.dcm4che.conf.json {
    requires org.dcm4che.conf.model;
    requires org.dcm4che.base;
    requires java.json;
    requires java.json.bind;

    exports org.dcm4che6.conf.json;
}
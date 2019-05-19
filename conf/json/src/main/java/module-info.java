/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2019
 */
module dcm4che.conf.json {
    requires dcm4che.conf.model;
    requires java.json;
    requires java.json.bind;
    requires org.dcm4che.base;

    exports org.dcm4che6.conf.json;
}
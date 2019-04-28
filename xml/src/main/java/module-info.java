/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
module org.dcm4che.xml {
    exports org.dcm4che6.xml;
    requires transitive org.dcm4che.base;
    requires transitive java.xml;
}

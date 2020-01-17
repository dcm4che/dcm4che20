/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2018
 */
module org.dcm4che.tool.mp4dump {
    requires info.picocli;

    opens org.dcm4che6.tool.mp4dump to info.picocli;
}
package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public interface DicomInputHandler {
    default boolean startElement(DicomElement dcmElm, boolean include) {
        return true;
    }

    default boolean endElement(DicomElement dcmElm) {
        return true;
    }

    default boolean startItem(DicomObject dcmObj) {
        return true;
    }

    default boolean endItem(DicomObject dcmObj) {
        return true;
    }

    default boolean dataFragment(DataFragment dataFragment) {
        return true;
    }
}

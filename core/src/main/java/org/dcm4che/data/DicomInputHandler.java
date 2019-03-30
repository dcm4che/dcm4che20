package org.dcm4che.data;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public interface DicomInputHandler {
    default boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        return true;
    }

    default boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        return true;
    }

    default boolean startItem(DicomInputStream dis, DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return true;
    }

    default boolean endItem(DicomInputStream dis, DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return true;
    }

    default boolean dataFragment(DicomInputStream dis, DataFragments fragments, DataFragment dataFragment)
            throws IOException {
        return true;
    }
}

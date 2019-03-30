package org.dcm4che.data;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2018
 */
public class FilterDicomInputHandler implements DicomInputHandler {

    private final DicomInputHandler handler;

    public FilterDicomInputHandler(DicomInputHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        return handler.startElement(dis, dcmElm, bulkData);
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        return handler.endElement(dis, dcmElm, bulkData);
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return handler.startItem(dis, dcmSeq, dcmObj);
    }

    @Override
    public boolean endItem(DicomInputStream dis, DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return handler.endItem(dis, dcmSeq, dcmObj);
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DataFragments fragments, DataFragment dataFragment)
            throws IOException {
        return handler.dataFragment(dis, fragments, dataFragment);
    }

}

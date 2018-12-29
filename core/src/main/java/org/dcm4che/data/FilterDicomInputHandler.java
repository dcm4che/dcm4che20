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
    public boolean startElement(DicomElement dcmElm, boolean bulkData) throws IOException {
        return handler.startElement(dcmElm, bulkData);
    }

    @Override
    public boolean endElement(DicomElement dcmElm) {
        return handler.endElement(dcmElm);
    }

    @Override
    public boolean startItem(DicomObject dcmObj) {
        return handler.startItem(dcmObj);
    }

    @Override
    public boolean endItem(DicomObject dcmObj) {
        return handler.endItem(dcmObj);
    }

    @Override
    public boolean dataFragment(DataFragment dataFragment) throws IOException {
        return handler.dataFragment(dataFragment);
    }

}

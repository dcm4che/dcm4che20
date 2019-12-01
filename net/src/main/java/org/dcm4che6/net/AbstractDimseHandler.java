package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public abstract class AbstractDimseHandler implements DimseHandler {
    final Predicate<Dimse> recognizedOperation;

    public AbstractDimseHandler(Predicate<Dimse> recognizedOperation) {
        this.recognizedOperation = recognizedOperation;
    }

    @Override
    public void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream)
            throws IOException {
        if (!recognizedOperation.test(dimse)) {
            throw new DicomServiceException(Status.UnrecognizedOperation);
        }
        accept(as, pcid, dimse, commandSet, readDataSet(dataStream, as.getTransferSyntax(pcid)));
    }

    protected abstract void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, DicomObject dataSet)
        throws IOException;

    static DicomObject readDataSet(InputStream dataStream, String transferSyntax) throws IOException {
        return dataStream != null
                ? new DicomInputStream(dataStream)
                    .withEncoding(DicomEncoding.of(transferSyntax))
                    .readDataSet()
                : null;
    }

}

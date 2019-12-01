package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
@FunctionalInterface
public interface DimseHandler {
    void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream)
            throws IOException;

}

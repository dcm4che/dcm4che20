package org.dcm4che6.data;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public interface DataFragment {
    DicomElement containedBy();

    int valueLength();

    void writeTo(OutputStream out) throws IOException;

    StringBuilder promptTo(StringBuilder appendTo, int maxLength);
}

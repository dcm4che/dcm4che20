package org.dcm4che.data;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public interface DataFragment {
    DataFragments containedBy();

    int valueLength();

    void writeTo(OutputStream out) throws IOException;
}

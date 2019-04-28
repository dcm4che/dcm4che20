package org.dcm4che6.io;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomParseException extends IOException {

    public DicomParseException() {
        super();
    }

    public DicomParseException(String message) {
        super(message);
    }
}

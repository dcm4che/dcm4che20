package org.dcm4che6.codec;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public class CompressedPixelParserException extends IOException {
    public CompressedPixelParserException(String message) {
        super(message);
    }
}

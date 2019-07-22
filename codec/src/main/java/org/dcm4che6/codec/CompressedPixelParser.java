package org.dcm4che6.codec;

import org.dcm4che6.data.DicomObject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public interface CompressedPixelParser {
    long getCodeStreamPosition();

    DicomObject getImagePixelDescription(DicomObject destination);

    String getTransferSyntaxUID() throws CompressedPixelParserException;
}

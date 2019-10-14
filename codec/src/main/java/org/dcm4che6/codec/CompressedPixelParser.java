package org.dcm4che6.codec;

import org.dcm4che6.data.DicomObject;

import java.util.OptionalLong;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public interface CompressedPixelParser {
    long getCodeStreamPosition();

    default OptionalLong getPositionAfterAPPSegments() {
        return OptionalLong.empty();
    }

    DicomObject getImagePixelDescription(DicomObject destination);

    String getTransferSyntaxUID() throws CompressedPixelParserException;
}

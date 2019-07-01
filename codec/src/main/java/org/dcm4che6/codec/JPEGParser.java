package org.dcm4che6.codec;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public class JPEGParser implements CompressedPixelParser {

    private static final int MAX_BYTES_BEFORE_SOI = 256;

    private final ByteBuffer buf = ByteBuffer.allocate(4);
    private long positionAfterAPP;
    private int sof;
    private int precision;
    private int columns;
    private int rows;
    private int samples;
    private int ss;

    public JPEGParser(SeekableByteChannel channel) throws IOException {
        findSOI(channel);
        positionAfterAPP = channel.position();
        Segment segment;
        while (JPEG.isAPP((segment = nextSegment(channel)).marker)) {
            skip(channel, segment.contentSize);
            positionAfterAPP = channel.position();
        }
        while (!JPEG.isSOF(segment.marker)) {
            skip(channel, segment.contentSize);
            segment = nextSegment(channel);
        }
        sof = segment.marker;
        precision = readUByte(channel);
        columns = readUShort(channel);
        rows = readUShort(channel);
        samples = readUByte(channel);
        skip(channel, segment.contentSize - 6);
        while ((segment = nextSegment(channel)).marker != JPEG.SOS) {
            skip(channel, segment.contentSize);
        }
        ss = readInt(channel) & 0xff;
    }

    public long getPositionAfterAPPSegments() {
        return positionAfterAPP;
    }

    @Override
    public DicomObject getImagePixelDescription(DicomObject destination) {
        if (destination == null)
            destination = DicomObject.newDicomObject();

        destination.setInt(Tag.SamplesPerPixel, VR.US, samples);
        if (samples == 3) {
            destination.setString(Tag.PhotometricInterpretation, VR.CS,
                    (sof == JPEG.SOF3 || sof == JPEG.SOF55) ? "RGB" : "YBR_FULL_422");
            destination.setInt(Tag.PlanarConfiguration, VR.US, 0);
        } else {
            destination.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        }
        destination.setInt(Tag.Rows, VR.US, rows);
        destination.setInt(Tag.Columns, VR.US, columns);
        destination.setInt(Tag.BitsAllocated, VR.US, precision > 8 ? 16 : 8);
        destination.setInt(Tag.BitsStored, VR.US, precision);
        destination.setInt(Tag.HighBit, VR.US, precision - 1);
        destination.setInt(Tag.PixelRepresentation, VR.US, 0);
        if (!(sof == JPEG.SOF3 || (sof == JPEG.SOF55 && ss == 0)))
            destination.setString(Tag.LossyImageCompression, VR.CS,  "01");
        return destination;
    }

    @Override
    public String getTransferSyntaxUID() throws CompressedPixelParserException {
        switch(sof) {
            case JPEG.SOF0:
                return UID.JPEGBaseline1;
            case JPEG.SOF1:
                return UID.JPEGExtended24;
            case JPEG.SOF2:
                return UID.JPEGFullProgressionNonHierarchical1012Retired;
            case JPEG.SOF3:
                return ss == 1 ? UID.JPEGLossless : UID.JPEGLosslessNonHierarchical14;
            case JPEG.SOF55:
                return ss == 0 ? UID.JPEGLSLossless : UID.JPEGLSLossyNearLossless;
        }
        throw new CompressedPixelParserException(String.format("JPEG SOF%d not supported", sof & 0xf));
    }

    private void findSOI(SeekableByteChannel channel) throws IOException {
        int remaining = MAX_BYTES_BEFORE_SOI;
        int b1, b2 = 0;
        do {
            if ((b1 = b2) != 0xff) {
                b1 = readUByte(channel);
                --remaining;
            }
            if (b1 == 0xff && (b2 = readUByte(channel)) == JPEG.SOI)
                return;
        } while (--remaining > 0);
        throw new CompressedPixelParserException("JPEG SOI marker not found");

    }

    private int readUByte(SeekableByteChannel channel) throws IOException {
        buf.clear().limit(1);
        channel.read(buf);
        buf.rewind();
        return buf.get() & 0xff;
    }

    private int readUShort(SeekableByteChannel channel) throws IOException {
        buf.clear().limit(2);
        channel.read(buf);
        buf.rewind();
        return buf.getShort() & 0xff;
    }

    private int readInt(SeekableByteChannel channel) throws IOException {
        buf.clear();
        channel.read(buf);
        buf.rewind();
        return buf.getInt();
    }

    private void skip(SeekableByteChannel channel, long n) throws IOException {
        channel.position(channel.position() + n);
    }

    private static class Segment {
        final int marker;
        final int contentSize;

        private Segment(int marker, int contentSize) {
            this.marker = marker;
            this.contentSize = contentSize;
        }
    }

    private Segment nextSegment(SeekableByteChannel channel) throws IOException {
        int v = readInt(channel);
        requiresFF(channel, v >>> 24);
        int marker = (v >> 16) & 0xff;
        while (JPEG.isStandalone(marker)) {
            marker = v & 0xff;
            v = (v << 16) | readUShort(channel);
            requiresFF(channel, v >>> 24);
        }
        return new Segment(marker, (v & 0xffff) - 2);
    }

    private void requiresFF(SeekableByteChannel channel, int v) throws IOException {
        if (v != 0xff)
            throw new CompressedPixelParserException(
                    String.format("unexpected %2XH on position %d", v, channel.position() - 4));
    }
}

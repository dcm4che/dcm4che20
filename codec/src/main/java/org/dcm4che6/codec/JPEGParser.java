package org.dcm4che6.codec;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class JPEGParser implements CompressedPixelParser {

    private static final long JPEG2000_SIGNATURE_BOX = 0x6a5020200d0a870aL; // jP\040\040<CR><LF><0x87><LF>;
    private static final int CONTIGUOUS_CODESTREAM_BOX = 0x6a703263; // jp2c;

    private final ByteBuffer buf = ByteBuffer.allocate(8);
    private final long codeStreamPosition;
    private long positionAfterAPP = -1L;
    private final Params params;

    public JPEGParser(SeekableByteChannel channel) throws IOException {
        seekCodeStream(channel);
        codeStreamPosition = channel.position();
        switch (readUShort(channel)) {
            case JPEG.FF_SOI:
                params = new JPEGParams(channel);
                break;
            case JPEG.FF_SOC:
                params = new JPEG2000Params(channel);
                break;
            default:
                throw new CompressedPixelParserException("JPEG SOI/SOC marker not found");
        }
    }

    @Override
    public long getCodeStreamPosition() {
        return codeStreamPosition;
    }

    public long getPositionAfterAPPSegments() {
        return positionAfterAPP;
    }

    @Override
    public DicomObject getImagePixelDescription(DicomObject destination) {
        if (destination == null)
            destination = DicomObject.newDicomObject();

        int samples = params.samplesPerPixel();
        destination.setInt(Tag.SamplesPerPixel, VR.US, samples);
        if (samples == 3) {
            destination.setString(Tag.PhotometricInterpretation, VR.CS, params.colorPhotometricInterpretation());
            destination.setInt(Tag.PlanarConfiguration, VR.US, 0);
        } else {
            destination.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        }
        destination.setInt(Tag.Rows, VR.US, params.rows());
        destination.setInt(Tag.Columns, VR.US, params.columns());
        int bitsStored = params.bitsStored();
        destination.setInt(Tag.BitsAllocated, VR.US, bitsStored > 8 ? 16 : 8);
        destination.setInt(Tag.BitsStored, VR.US, bitsStored);
        destination.setInt(Tag.HighBit, VR.US, bitsStored - 1);
        destination.setInt(Tag.PixelRepresentation, VR.US, params.pixelRepresentation());
        if (params.lossyImageCompression())
            destination.setString(Tag.LossyImageCompression, VR.CS,  "01");
        return destination;
    }

    @Override
    public String getTransferSyntaxUID() throws CompressedPixelParserException {
        return params.transferSyntaxUID();
     }

    private void seekCodeStream(SeekableByteChannel channel) throws IOException {
        long startPos = channel.position();
        if (readInt(channel) != 12 || readLong(channel) != JPEG2000_SIGNATURE_BOX) {
            channel.position(startPos);
            return;
        }

        long size = channel.size();
        long boxPos = channel.position();
        long boxLengthType;
        while (((boxLengthType = readLong(channel)) & 0xffffffff) != CONTIGUOUS_CODESTREAM_BOX) {
            if ((boxPos += (boxLengthType >>> 32)) > size) {
                channel.position(startPos);
                return;
            }
            channel.position(boxPos);
        }
    }

    private int readUShort(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear().limit(2);
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.getShort() & 0xffff;
    }

    private int readInt(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear().limit(4);
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.getInt();
    }

    private long readLong(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear();
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.getLong();
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

    private interface Params {
        int samplesPerPixel();
        int rows();
        int columns();
        int bitsStored();
        int pixelRepresentation();
        boolean lossyImageCompression();
        String colorPhotometricInterpretation();
        String transferSyntaxUID() throws CompressedPixelParserException;
    }

    private class JPEGParams implements Params {

        final int sof;
        final ByteBuffer sofParams;
        final ByteBuffer sosParams;

        JPEGParams(SeekableByteChannel channel) throws IOException {
            Segment segment;
            while (org.dcm4che6.codec.JPEG.isAPP((segment = nextSegment(channel)).marker)) {
                skip(channel, segment.contentSize);
                positionAfterAPP = channel.position();
            }
            while (!org.dcm4che6.codec.JPEG.isSOF(segment.marker)) {
                skip(channel, segment.contentSize);
                segment = nextSegment(channel);
            }
            sof = segment.marker;
            channel.read(sofParams = ByteBuffer.allocate(segment.contentSize));
            while ((segment = nextSegment(channel)).marker != JPEG.SOS) {
                skip(channel, segment.contentSize);
            }
            channel.read(sosParams = ByteBuffer.allocate(segment.contentSize));
        }

        @Override
        public int samplesPerPixel() {
            return sofParams.get(5) & 0xff;
        }

        @Override
        public int rows() {
            return sofParams.getShort(3) & 0xffff;
        }

        @Override
        public int columns() {
            return sofParams.getShort(1) & 0xffff;
        }

        @Override
        public int bitsStored() {
            return sofParams.get(0) & 0xff;
        }

        @Override
        public int pixelRepresentation() {
            return 0;
        }

        @Override
        public boolean lossyImageCompression() {
            return !(sof == JPEG.SOF3 || (sof == JPEG.SOF55 && sosParams.get(3) == 0));
        }

        @Override
        public String colorPhotometricInterpretation() {
            return sof == JPEG.SOF3 || sof == JPEG.SOF55 ? "RGB" : "YBR_FULL_422";
        }

        @Override
        public String transferSyntaxUID() throws CompressedPixelParserException {
            switch(sof) {
                case JPEG.SOF0:
                    return UID.JPEGBaseline1;
                case JPEG.SOF1:
                    return UID.JPEGExtended24;
                case JPEG.SOF2:
                    return UID.JPEGFullProgressionNonHierarchical1012Retired;
                case JPEG.SOF3:
                    return sosParams.get(3) == 1 ? UID.JPEGLossless : UID.JPEGLosslessNonHierarchical14;
                case JPEG.SOF55:
                    return sosParams.get(3) == 0 ? UID.JPEGLSLossless : UID.JPEGLSLossyNearLossless;
            }
            throw new CompressedPixelParserException(String.format("JPEG SOF%d not supported", sof & 0xf));
        }
    }

    private class JPEG2000Params implements Params {

        final ByteBuffer sizParams;
        final ByteBuffer codParams;

        JPEG2000Params(SeekableByteChannel channel) throws IOException {
            Segment segment;
            while ((segment = nextSegment(channel)).marker != JPEG.SIZ) {
                skip(channel, segment.contentSize);
            }
            channel.read(sizParams = ByteBuffer.allocate(segment.contentSize));
            while ((segment = nextSegment(channel)).marker != JPEG.COD) {
                skip(channel, segment.contentSize);
            }
            channel.read(codParams = ByteBuffer.allocate(segment.contentSize));
        }

        @Override
        public int samplesPerPixel() {
            return sizParams.getShort(34) & 0xffff; // Csiz
        }

        @Override
        public int rows() {
            return sizParams.getInt(6) - sizParams.getInt(14); // Ysiz - YOsiz;
        }

        @Override
        public int columns() {
            return sizParams.getInt(2) - sizParams.getInt(10); // Xsiz - XOsiz;
        }

        @Override
        public int bitsStored() {
            return (sizParams.get(36) & 0x7f) + 1; // Ssiz
        }

        @Override
        public int pixelRepresentation() {
            return sizParams.get(36) < 0 ? 1 : 0; // Ssiz
        }

        @Override
        public boolean lossyImageCompression() {
            return codParams.get(9) == 0; // Wavelet Transformation
        }

        @Override
        public String colorPhotometricInterpretation() {
            return codParams.get(4) == 0 ? "RGB"    // Multiple component transformation
                    : lossyImageCompression() ? "YBR_ICT" : "YBR_RCT";
        }

        @Override
        public String transferSyntaxUID() {
            return lossyImageCompression() ? UID.JPEG2000 : UID.JPEG2000LosslessOnly;
        }
    }
}

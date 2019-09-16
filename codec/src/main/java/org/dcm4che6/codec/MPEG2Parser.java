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
public class MPEG2Parser implements CompressedPixelParser {

    private static final int BUFFER_SIZE = 8162;
    private static final int SEQUENCE_HEADER_STREAM_ID = (byte) 0xb3;
    private static final int GOP_HEADER_SEARCH_RANGE = 0x100000;
    private static final int GOP_HEADER_STREAM_ID = (byte) 0xb8;
    private static final String[] ASPECT_RATIO_1_1 = { "1", "1" };
    private static final String[] ASPECT_RATIO_4_3 = { "4", "3" };
    private static final String[] ASPECT_RATIO_16_9 = { "16", "9" };
    private static final String[] ASPECT_RATIO_221_100 = { "221", "100" };
    private static final String[][] ASPECT_RATIOS = {
            ASPECT_RATIO_1_1,
            ASPECT_RATIO_4_3,
            ASPECT_RATIO_16_9,
            ASPECT_RATIO_221_100
    };
    private static int[] FPS = {
            24, 1001,
            24, 1000,
            25, 1000,
            30, 1001,
            30, 1000,
            50, 1000,
            60, 1001,
            60, 1000
    };

    private final byte[] data = new byte[BUFFER_SIZE];
    private final ByteBuffer buf = ByteBuffer.wrap(data);
    private final int columns;
    private final int rows;
    private final int aspectRatio;
    private final int frameRate;
    private final int duration;

    public MPEG2Parser(SeekableByteChannel channel) throws IOException {
        Packet packet;
        while (!isVideoStream((packet = nextPacket(channel)).startCode)) {
            skip(channel, packet.length);
        }
        findSequenceHeader(channel, packet.length);
        ((Buffer) buf).clear().limit(7);
        channel.read(buf);
        columns = ((data[0] & 0xff) << 4) | ((data[1] & 0xf0) >> 4);
        rows = ((data[1] & 0x0f) << 8) | (data[2] & 0xff);
        aspectRatio = (data[3] >> 4) & 0x0f;
        frameRate = Math.max(1, Math.min(data[3] & 0x0f, 8));
        int lastGOP = findLastGOP(channel);
        int hh = (data[lastGOP] & 0x7c) >> 2;
        int mm = ((data[lastGOP] & 0x03) << 4) | ((data[lastGOP + 1] & 0xf0) >> 4);
        int ss = ((data[lastGOP + 1] & 0x07) << 3) | ((data[lastGOP + 2] & 0xe0) >> 5);
        duration = hh * 3600 + mm * 60 + ss;
    }

    @Override
    public long getCodeStreamPosition() {
        return 0;
    }

    @Override
    public DicomObject getImagePixelDescription(DicomObject destination) {
        if (destination == null)
            destination = DicomObject.newDicomObject();

        int frameRate2 = (frameRate - 1) << 1;
        int fps = FPS[frameRate2];
        destination.setInt(Tag.CineRate, VR.IS, fps);
        destination.setFloat(Tag.FrameTime, VR.DS, ((float) FPS[frameRate2 + 1]) / fps);
        destination.setInt(Tag.SamplesPerPixel, VR.US, 3);
        destination.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_PARTIAL_420");
        destination.setInt(Tag.PlanarConfiguration, VR.US, 0);
        destination.setInt(Tag.FrameIncrementPointer, VR.AT, Tag.FrameTime);
        destination.setInt(Tag.NumberOfFrames, VR.IS, (int) (duration * fps * 1000L / FPS[frameRate2 + 1]));
        destination.setInt(Tag.Rows, VR.US, rows);
        destination.setInt(Tag.Columns, VR.US, columns);
        if (aspectRatio > 0 && aspectRatio < 5)
            destination.setString(Tag.PixelAspectRatio, VR.IS, ASPECT_RATIOS[aspectRatio-1]);
        destination.setInt(Tag.BitsAllocated, VR.US, 8);
        destination.setInt(Tag.BitsStored, VR.US, 8);
        destination.setInt(Tag.HighBit, VR.US, 7);
        destination.setInt(Tag.PixelRepresentation, VR.US, 0);
        destination.setString(Tag.LossyImageCompression, VR.CS,  "01");
        return destination;
    }

    @Override
    public String getTransferSyntaxUID() {
        return frameRate <= 5 && columns <= 720 ? UID.MPEG2 : UID.MPEG2MainProfileHighLevel;
    }

    private void findSequenceHeader(SeekableByteChannel channel, int length) throws IOException {
        int remaining = length;
        ((Buffer) buf).clear().limit(3);
        while ((remaining -= buf.remaining()) > 1) {
            channel.read(buf);
            ((Buffer) buf).rewind();
            if (((data[0] << 16) | (data[1] << 8) | data[2]) == 1) {
                ((Buffer) buf).clear().limit(1);
                remaining--;
                channel.read(buf);
                ((Buffer) buf).rewind();
                if (buf.get() == SEQUENCE_HEADER_STREAM_ID)
                    return;
                buf.limit(3);
            }
            buf.position(data[2] == 0 ? data[1] == 0 ? 2 : 1 : 0);
            data[0] = 0;
        }
        throw new CompressedPixelParserException("MPEG2 sequence header not found");
    }

    private void skip(SeekableByteChannel channel, long n) throws IOException {
        channel.position(channel.position() + n);
    }

    private int findLastGOP(SeekableByteChannel channel) throws IOException {
        long size = channel.size();
        long startPos = size - BUFFER_SIZE;
        long minStartPos = Math.max(0, size - GOP_HEADER_SEARCH_RANGE);
        while (startPos > minStartPos) {
            channel.position(startPos);
            ((Buffer) buf).clear();
            channel.read(buf);
            int i = 0;
            while (i + 8 < BUFFER_SIZE) {
                if (((data[i] << 16) | (data[i + 1] << 8) | data[i + 2]) == 1) {
                    if (data[i + 3] == GOP_HEADER_STREAM_ID)
                        return i + 4;
                }
                i += data[i + 2] == 0 ? data[i + 1] == 0 ? 1 : 2 : 3;
            }
            startPos -= BUFFER_SIZE - 8;
        }
        throw new CompressedPixelParserException("last MPEG2 Group of Pictures not found");
    }

    private static class Packet {
        final int startCode;
        final int length;

        private Packet(int startCode, int length) {
            this.startCode = startCode;
            this.length = length;
        }
    }

    private Packet nextPacket(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear().limit(6);
        channel.read(buf);
        ((Buffer) buf).rewind();
        int startCode = buf.getInt();
        if ((startCode & 0xfffffe00) != 0) {
            throw new CompressedPixelParserException(
                    String.format("Invalid MPEG2 start code %4XH on position %d", startCode, channel.position() - 6));
        }
        return new Packet(startCode, packetLength(startCode));
    }

    private int packetLength(int startCode) {
        return isPackHeader(startCode) ? ((data[4] & 0xc0) != 0) ? 8 : 6 : buf.getShort() & 0xffff;
    }

    private static boolean isPackHeader(int startCode) {
        return startCode == 0x1ba;
    }

    private static boolean isVideoStream(int startCode) {
        return (startCode & 0xfffff0) == 0x1e0;
    }
}

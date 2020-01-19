package org.dcm4che6.codec;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Date;
import java.util.Optional;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class MP4Parser implements CompressedPixelParser {
    private static final int FileBoxType = 0x66747970; // ftyp;
    private static final int MovieBoxType = 0x6d6f6f76; // moov;
    private static final int TrackBoxType = 0x7472616b; // trak
    private static final int MediaBoxType = 0x6d646961; // mdia
    private static final int MediaHeaderBoxType = 0x6d646864; // mdhd
    private static final int MediaInformationBoxType = 0x6d696e66; // minf
    private static final int SampleTableBoxType = 0x7374626c; // stbl
    private static final int SampleDescriptionBoxType = 0x73747364; // stsd
    private static final int VisualSampleEntryTypeAVC1 = 0x61766331; // avc1
    private static final int AvcConfigurationBoxType = 0x61766343; // avcC
    private static final int VisualSampleEntryTypeHVC1 = 0x68766331; // hvc1
    private static final int HevcConfigurationBoxType = 0x68766343; // hvcC
    private static final int SampleSizeBoxType = 0x7374737a; // stsz

    private final ByteBuffer buf = ByteBuffer.allocate(8);
    private MP4FileType mp4FileType;
    private Date creationTime;
    private Date modificationTime;
    private int timescale;
    private long duration;
    private int fp1000s;
    private int rows;
    private int columns;
    private int numFrames;
    private int visualSampleEntryType;
    private int configurationVersion;
    private int profile_idc;
    private int level_idc;

    public MP4Parser(SeekableByteChannel channel) throws IOException {
        long position = channel.position();
        Box box = nextBox(channel, channel.size() - position);
        if (box.type == FileBoxType) {
            mp4FileType = new MP4FileType(readInt(channel), readInt(channel), readInts(channel, box.end));
        } else {
            channel.position(position);
        }
        parseMovieBox(channel, findBox(channel, channel.size(), MovieBoxType));
    }

    @Override
    public Optional<MP4FileType> getMP4FileType() {
        return Optional.ofNullable(mp4FileType);
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public Date getModificationTime() {
        return modificationTime;
    }

    @Override
    public long getCodeStreamPosition() {
        return 0;
    }

    @Override
    public DicomObject getImagePixelDescription(DicomObject destination) {
        if (destination == null)
            destination = DicomObject.newDicomObject();

        destination.setInt(Tag.CineRate, VR.IS, (fp1000s + 500) / 1000);
        destination.setFloat(Tag.FrameTime, VR.DS, 1_000_000.f / fp1000s);
        destination.setInt(Tag.SamplesPerPixel, VR.US, 3);
        destination.setString(Tag.PhotometricInterpretation, VR.CS, "YBR_PARTIAL_420");
        destination.setInt(Tag.PlanarConfiguration, VR.US, 0);
        destination.setInt(Tag.FrameIncrementPointer, VR.AT, Tag.FrameTime);
        destination.setInt(Tag.NumberOfFrames, VR.IS, numFrames);
        destination.setInt(Tag.Rows, VR.US, rows);
        destination.setInt(Tag.Columns, VR.US, columns);
        destination.setInt(Tag.BitsAllocated, VR.US, 8);
        destination.setInt(Tag.BitsStored, VR.US, 8);
        destination.setInt(Tag.HighBit, VR.US, 7);
        destination.setInt(Tag.PixelRepresentation, VR.US, 0);
        destination.setString(Tag.LossyImageCompression, VR.CS, "01");
        return destination;
    }

    @Override
    public String getTransferSyntaxUID() throws CompressedPixelParserException {
        switch (visualSampleEntryType) {
            case VisualSampleEntryTypeAVC1:
                switch (profile_idc) {
                    case 100: // High Profile
                        if (level_idc <= 41)
                            return isBDCompatible()
                                    ? UID.MPEG4AVCH264BDCompatibleHighProfileLevel41
                                    : UID.MPEG4AVCH264HighProfileLevel41;
                        else if (level_idc <= 42)
                            // TODO: distinguish between MPEG4AVCH264HighProfileLevel42For2DVideo
                            //  and MPEG4AVCH264HighProfileLevel42For3DVideo
                            return UID.MPEG4AVCH264HighProfileLevel42For2DVideo;
                        break;
                    case 128: // Stereo High Profile
                        if (level_idc <= 42)
                            return UID.MPEG4AVCH264StereoHighProfileLevel42;
                        break;
                }
                throw profileLevelNotSupported("MPEG-4 AVC profile_idc/level_idc: %d/%d not supported");
            case VisualSampleEntryTypeHVC1:
                if (level_idc <= 51) {
                    switch (profile_idc) {
                        case 1: // Main Profile
                            return UID.HEVCH265MainProfileLevel51;
                        case 2: // Main 10 Profile
                            return UID.HEVCH265Main10ProfileLevel51;
                    }
                }
                throw profileLevelNotSupported("MPEG-4 HEVC profile_idc/level_idc: %d/%d not supported");
        }
        throw new AssertionError("visualSampleEntryType:" + visualSampleEntryType);
    }

    private CompressedPixelParserException profileLevelNotSupported(String format) {
        return new CompressedPixelParserException(String.format(format, profile_idc, level_idc));
    }

    private boolean isBDCompatible() {
        return rows == 1080
                ? columns == 1920
                    && (fp1000s == 23976 || fp1000s == 24000 || fp1000s == 25000 || fp1000s == 29970)
                : rows == 720 && columns == 1280
                    && (fp1000s == 23976 || fp1000s == 24000 || fp1000s == 50000 || fp1000s == 59940);
    }

    private Box nextBox(SeekableByteChannel channel, long remaining) throws IOException {
        long pos = channel.position();
        long type = readLong(channel);
        int size = (int) (type >> 32);
        return new Box((int) type, pos + (size == 0 ? remaining : size == 1 ? readLong(channel) : size));
    }

    private Box findBox(SeekableByteChannel channel, long end, int type) throws IOException {
        long remaining;
        while ((remaining = end - channel.position()) > 0) {
            Box box = nextBox(channel, remaining);
            if (box.type == type)
                return box;
            channel.position(box.end);
        }
        throw new CompressedPixelParserException(boxNotFound(type));
    }

    private static String boxNotFound(int type) {
        return String.format("%c%c%c%c box not found",
                (type >> 24) & 0xff,
                (type >> 16) & 0xff,
                (type >> 8) & 0xff,
                type & 0xff);
    }

    private int[] readInts(SeekableByteChannel channel, long end) throws IOException {
        int[] values = new int[(int) ((end - channel.position()) / 4)];
        for (int i = 0; i < values.length; i++) {
            values[i] = readInt(channel);
        }
        return values;
    }

    private byte readByte(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear().limit(1);
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.get();
    }

    private short readShort(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear().limit(2);
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.getShort();
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

    private static Date toDate(long val) {
        return val > 0 ? new Date((val - 2082844800L) * 1000L) : null;
    }

    private static class Box {
        final int type;
        final long end;

        Box(int type, long end) {
            this.type = type;
            this.end = end;
        }
    }

    private void parseMovieBox(SeekableByteChannel channel, Box box) throws IOException {
        do {
            parseTrackBox(channel, findBox(channel, box.end, TrackBoxType));
        } while (visualSampleEntryType == 0);
        channel.position(box.end);
    }

    private void parseTrackBox(SeekableByteChannel channel, Box box) throws IOException {
        parseMediaBox(channel, findBox(channel, box.end, MediaBoxType));
        channel.position(box.end);
    }

    private void parseMediaBox(SeekableByteChannel channel, Box box) throws IOException {
        parseMediaHeaderBox(channel, findBox(channel, box.end, MediaHeaderBoxType));
        parseMediaInformationBox(channel, findBox(channel, box.end, MediaInformationBoxType));
        channel.position(box.end);
    }

    private void parseMediaHeaderBox(SeekableByteChannel channel, Box box) throws IOException {
        if ((readInt(channel) >>> 24) == 1) {
            creationTime = toDate(readLong(channel));
            modificationTime = toDate(readLong(channel));
            timescale = readInt(channel);
            duration = readLong(channel);
        } else {
            creationTime = toDate(readInt(channel) & 0xffffffffL);
            modificationTime = toDate(readInt(channel) & 0xffffffffL);
            timescale = readInt(channel);
            duration = readInt(channel) & 0xffffffffL;
        }
        channel.position(box.end);
    }

    private void parseMediaInformationBox(SeekableByteChannel channel, Box box) throws IOException {
        parseSampleTableBox(channel,
                findBox(channel, box.end, SampleTableBoxType));
        channel.position(box.end);
    }

    private void parseSampleTableBox(SeekableByteChannel channel, Box box) throws IOException {
        parseSampleDescriptionBox(channel,
                findBox(channel, box.end, SampleDescriptionBoxType));
        parseSampleSizeBox(channel,
                findBox(channel, box.end, SampleSizeBoxType));
        channel.position(box.end);
    }

    private void parseSampleDescriptionBox(SeekableByteChannel channel, Box box) throws IOException {
        skip(channel, 8);
        parseVisualSampleEntry(channel, nextBox(channel, box.end));
        channel.position(box.end);
    }

    private void parseVisualSampleEntry(SeekableByteChannel channel, Box box) throws IOException {
        switch (box.type) {
            case VisualSampleEntryTypeAVC1:
                parseVisualSampleEntryHeader(channel, box);
                parseAvcConfigurationBox(channel,
                        findBox(channel, box.end, AvcConfigurationBoxType));
                break;
            case VisualSampleEntryTypeHVC1:
                parseVisualSampleEntryHeader(channel, box);
                parseHevcConfigurationBox(channel,
                        findBox(channel, box.end, HevcConfigurationBoxType));
                break;
        }
        channel.position(box.end);
    }

    private void parseVisualSampleEntryHeader(SeekableByteChannel channel, Box box) throws IOException {
        visualSampleEntryType = box.type;
        skip(channel, 24);
        int val = readInt(channel);
        columns = val >>> 16;
        rows = val & 0xffff;
        skip(channel, 50);
    }

    private void parseAvcConfigurationBox(SeekableByteChannel channel, Box box) throws IOException {
        int val = readInt(channel);
        configurationVersion = val >>> 24;
        profile_idc = (val >> 16) & 0xff;
        level_idc = val & 0xff;
        channel.position(box.end);
    }

    private void parseHevcConfigurationBox(SeekableByteChannel channel, Box box) throws IOException {
        int val = readShort(channel);
        configurationVersion = val >>> 8;
        profile_idc = val & 0x1F;
        skip(channel, 10);
        level_idc = readByte(channel) & 0xff;
        channel.position(box.end);
    }

    private void parseSampleSizeBox(SeekableByteChannel channel, Box box) throws IOException {
        skip(channel, 8);
        numFrames = readInt(channel);
        fp1000s = (int) ((numFrames * 1000L * timescale + (duration >> 1)) / duration);
        channel.position(box.end);
    }

}

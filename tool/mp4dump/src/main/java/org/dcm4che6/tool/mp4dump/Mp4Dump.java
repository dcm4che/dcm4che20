package org.dcm4che6.tool.mp4dump;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
@CommandLine.Command(
        name = "Mp4Dump",
        mixinStandardHelpOptions = true,
        versionProvider = Mp4Dump.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The Mp4Dump utility.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ Mp4Dump video.mp4",
                "Dump video.mp4." }
)
public class Mp4Dump implements Callable<Integer> {

    private final ByteBuffer buf = ByteBuffer.allocate(8);

    @CommandLine.Parameters(
            description = "MP4 filename to dump.",
            index = "0")
    Path mp4file;


    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Mp4Dump());
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        try (SeekableByteChannel channel = Files.newByteChannel(mp4file)) {
            dumpBoxes(channel, channel.size(), 0);
        }
        return 0;
    }

    private void dumpBoxes(SeekableByteChannel channel, long end, int level) throws IOException {
        long remaining;
        long pos;
        while ((remaining = end - (pos = channel.position())) > 0) {
            Box box = nextBox(channel, remaining);
            System.out.print(pos);
            System.out.print(": ");
            for (int i = 0; i < level; i++) {
                System.out.print('>');
            }
            System.out.print(' ');
            System.out.print((char) ((box.type >> 24) & 0xff));
            System.out.print((char) ((box.type >> 16) & 0xff));
            System.out.print((char) ((box.type >> 8) & 0xff));
            System.out.print((char) ((box.type >> 0) & 0xff));
            System.out.print(' ');
            System.out.println(box.contentSize);
            switch (box.type) {
                case 1635148593: // avc1
                case 1752589105: // hvc1
                    skip(channel,70);
                case 1937011556: // stsd
                    skip(channel,8);
                case 1836019574: // moov
                case 1953653099: // trak
                case 1835297121: // mdia
                case 1835626086: // minf
                case 1937007212: // stbl
                case 1836475768: // mvex
                case 1836019558: // moof
                    dumpBoxes(channel, pos + box.contentSize, level + 1);
                    break;
                default:
                    skip(channel, box.contentSize);
            }
        }
    }

    private Box nextBox(SeekableByteChannel channel, long remaining) throws IOException {
        long type = readLong(channel);
        int size = (int) (type >> 32);
        return size == 0
                ? new Box((int) type, remaining - 8)
                : size == 1
                ? new Box((int) type, readLong(channel) - 16)
                : new Box((int) type, size - 8);
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

    private static class Box {
        final int type;
        final long contentSize;

        Box(int type, long contentSize) {
            this.type = type;
            this.contentSize = contentSize;
        }

    }

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Mp4Dump.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }
}

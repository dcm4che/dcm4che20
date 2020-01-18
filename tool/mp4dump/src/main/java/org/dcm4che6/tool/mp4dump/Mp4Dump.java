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
            dumpBoxes(channel, channel.size(), "");
        }
        return 0;
    }

    private void dumpBoxes(SeekableByteChannel channel, long end, String level) throws IOException {
        long remaining;
        long pos;
        while ((remaining = end - (pos = channel.position())) > 0) {
            Box box = nextBox(channel, remaining);
            System.out.format("%d: %s%c%c%c%c%n", pos, level,
                    (box.type >> 24) & 0xff,
                    (box.type >> 16) & 0xff,
                    (box.type >> 8) & 0xff,
                    box.type  & 0xff);
            switch (box.type) {
                case 1635148593: // avc1
                case 1752589105: // hvc1
                    dumpBoxes(skip(channel, 78), box.end, level + '>');
                    break;
                case 1937011556: // stsd
                    dumpBoxes(skip(channel, 8), box.end, level + '>');
                    break;
                case 1836019574: // moov
                case 1953653099: // trak
                case 1835297121: // mdia
                case 1835626086: // minf
                case 1937007212: // stbl
                case 1836475768: // mvex
                case 1836019558: // moof
                    dumpBoxes(channel, box.end, level + '>');
                    break;
                default:
                    channel.position(box.end);
            }
        }
    }

    private Box nextBox(SeekableByteChannel channel, long remaining) throws IOException {
        long pos = channel.position();
        long type = readLong(channel);
        int size = (int) (type >> 32);
        return new Box((int) type, pos + (size == 0 ? remaining : size == 1 ? readLong(channel) : size));
    }

    private long readLong(SeekableByteChannel channel) throws IOException {
        ((Buffer) buf).clear();
        channel.read(buf);
        ((Buffer) buf).rewind();
        return buf.getLong();
    }

    private SeekableByteChannel skip(SeekableByteChannel channel, long n) throws IOException {
        return channel.position(channel.position() + n);
    }

    private static class Box {
        final int type;
        final long end;

        Box(int type, long end) {
            this.type = type;
            this.end = end;
        }

    }

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Mp4Dump.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }
}

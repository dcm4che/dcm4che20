package org.dcm4che6.tool.jpg2dcm;

import org.dcm4che6.codec.*;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.DateTimeUtils;
import org.dcm4che6.util.UIDUtils;
import org.dcm4che6.xml.SAXReader;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
@CommandLine.Command(
        name = "jpg2dcm",
        mixinStandardHelpOptions = true,
        versionProvider = Jpg2Dcm.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The jpg2dcm utility .",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ jpg2dcm image.jpeg image.dcm",
                "Encapsulate image.jpg into DICOM file image.dcm." }
)
public class Jpg2Dcm implements Callable<Integer> {

    private static final int BUFFER_SIZE = 8162;

    @CommandLine.Parameters(
            description = "JPEG image or MPEG input filename to be encapsulated.",
            index = "0")
    Path jpgfile;

    @CommandLine.Parameters(
            description = "DICOM output filename.",
            index = "1")
    Path dcmfile;

    @CommandLine.Option(names = {"-s"},
            description = "Write sequences with explicit ('EXPLICIT') or undefined ('UNDEFINED') length, or write " +
                    "only non-empty sequences with undefined length ('UNDEFINED_OR_ZERO').",
            paramLabel = "<length>")
    DicomOutputStream.LengthEncoding sequenceLengthEncoding = DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO;

    @CommandLine.Option(names = {"-i"},
            description = "Write sequence items with explicit ('EXPLICIT') or undefined ('UNDEFINED') length, or " +
                    "write only non-empty sequence items with undefined length ('UNDEFINED_OR_ZERO').",
            paramLabel = "<length>")
    DicomOutputStream.LengthEncoding itemLengthEncoding = DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO;

    @CommandLine.Option(names = "--metadata",
            description = "Use metadata from specified XML file.")
    Path xmlFile;

    @CommandLine.Option(names = "-m",
            description = "Set element of metadata in format <attribute=value>.")
    Map<TagPath, String> elements = new HashMap<>();

    @CommandLine.Option(names = "--photo",
            description = {
                    "Encapsulate JPEG image into DICOM Visible Light Photographic image.",
                    "Otherwise encapsulate JPEG image into DICOM Secondary Capture image."
            })
    boolean photo;

    @CommandLine.Option(names = "--noapp",
            description = {
                    "Remove application segments APPn from encapsulated JPEG stream",
                    "Otherwise encapsulate JPEG stream verbatim."
            })
    boolean noapp;

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Jpg2Dcm());
        cl.registerConverter(TagPath.class, TagPath::new);
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        ContentType type = ContentType.probe(jpgfile);
        DicomObject fmi;
        try (SeekableByteChannel channel = Files.newByteChannel(jpgfile)) {
            CompressedPixelParser parser = type.factory.newCompressedPixelParser(channel);
            DicomObject dcmobj = parser.getImagePixelDescription(createMetadata(type));
            fmi = dcmobj.createFileMetaInformation(parser.getTransferSyntaxUID());
            try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(dcmfile))
                    .withSequenceLengthEncoding(sequenceLengthEncoding)
                    .withItemLengthEncoding(itemLengthEncoding)) {
                dos.writeFileMetaInformation(fmi).withEncoding(fmi);
                dos.writeDataSet(dcmobj);
                dos.writeHeader(Tag.PixelData, VR.OB, -1);
                dos.writeHeader(Tag.Item, VR.NONE, 0);
                if (noapp && parser.getPositionAfterAPPSegments().isPresent()) {
                    copyPixelData(channel, parser.getPositionAfterAPPSegments().getAsLong(), dos,
                            (byte) 0xFF, (byte) JPEG.SOI);
                } else {
                    copyPixelData(channel, parser.getCodeStreamPosition(), dos);
                }
                dos.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
            }
        }
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID).orElseThrow();
        String tsuid = fmi.getString(Tag.TransferSyntaxUID).orElseThrow();
        System.out.println(String.format(
                "Encapsulated %s to %s%n  SOP Class UID: %s - %s%n  Transfer Syntax UID: %s - %s%n",
                jpgfile, dcmfile, cuid, UID.nameOf(cuid), tsuid, UID.nameOf(tsuid)));
        return 0;
    }

    private void copyPixelData(SeekableByteChannel channel, long position, DicomOutputStream dos, byte... prefix)
            throws IOException {
        long codeStreamSize = channel.size() - position + prefix.length;
        dos.writeHeader(Tag.Item, VR.NONE, (int) ((codeStreamSize + 1) & ~1));
        dos.write(prefix);
        channel.position(position);
        copy(channel, dos);
        if ((codeStreamSize & 1) != 0)
            dos.write(0);
    }

    private void copy(ByteChannel in, OutputStream out) throws IOException {
        byte[] b = new byte[BUFFER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(b);
        int read;
        while ((read = in.read(buf)) > 0) {
            out.write(b, 0, read);
            buf.clear();
        }
    }

    private DicomObject createMetadata(ContentType type) throws Exception {
        DicomObject metadata = DicomObject.newDicomObject();
        try (InputStream is = Jpg2Dcm.class.getResourceAsStream(type.resource.apply(this))) {
            SAXReader.parse(is, metadata);
        }
        createUIDs(metadata);
        addInstanceCreationDateAndTime(metadata);
        if (xmlFile != null)
            try (InputStream is = Files.newInputStream(xmlFile)) {
                SAXReader.parse(is, metadata);
            }
        elements.forEach((tagPath, value) -> tagPath.setString(metadata, value));
        return metadata;
    }

    private static void createUIDs(DicomObject metadata) {
        metadata.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.randomUID());
        metadata.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.randomUID());
        metadata.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.randomUID());
    }

    private static void addInstanceCreationDateAndTime(DicomObject metadata) {
        LocalDateTime dt = LocalDateTime.now();
        metadata.setString(Tag.InstanceCreationDate, VR.DA, DateTimeUtils.formatDA(dt));
        metadata.setString(Tag.InstanceCreationTime, VR.TM, DateTimeUtils.formatTM(dt));
    }

    enum ContentType {
        IMAGE_JPEG(JPEGParser::new, x -> x.photo ? "photo.xml" : "sc.xml"),
        VIDEO_MPEG(MPEG2Parser::new, x -> "video.xml"),
        VIDEO_MP4(MP4Parser::new, x -> "video.xml");

        final CompressedPixelParserFactory factory;
        final Function<Jpg2Dcm, String> resource;

        ContentType(CompressedPixelParserFactory factory, Function<Jpg2Dcm, String> resource) {
            this.factory = factory;
            this.resource = resource;
        }

        static ContentType probe(Path path) {
            try {
                String type = Files.probeContentType(path);
                if (type == null)
                    throw new IOException(String.format("failed to determine content type of file: '%s'", path));
                switch (type.toLowerCase()) {
                    case "image/jpeg":
                    case "image/jp2":
                        return ContentType.IMAGE_JPEG;
                    case "video/mpeg":
                        return ContentType.VIDEO_MPEG;
                    case "video/mp4":
                        return ContentType.VIDEO_MP4;
                }
                throw new UnsupportedOperationException(
                        String.format("unsupported content type: '%s' of file: '%s'", type, path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Jpg2Dcm.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @FunctionalInterface
    private interface CompressedPixelParserFactory {
        CompressedPixelParser newCompressedPixelParser(SeekableByteChannel channel) throws IOException;
    }
}

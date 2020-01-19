package org.dcm4che6.tool.stowrs;

import org.dcm4che6.codec.CompressedPixelParser;
import org.dcm4che6.codec.JPEGParser;
import org.dcm4che6.codec.MP4Parser;
import org.dcm4che6.codec.MPEG2Parser;
import org.dcm4che6.data.*;
import org.dcm4che6.json.JSONWriter;
import org.dcm4che6.util.DateTimeUtils;
import org.dcm4che6.util.UIDUtils;
import org.dcm4che6.xml.SAXReader;
import org.dcm4che6.xml.SAXWriter;
import picocli.CommandLine;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2018
 */
@CommandLine.Command(
        name = "stowrs",
        mixinStandardHelpOptions = true,
        versionProvider = StowRS.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The stowrs utility .",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ stowrs http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies image.dcm",
                "Upload DICOM file image.dcm to STOW-RS service provided at specified URL." }
)
public class StowRS implements Callable<Integer> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{StowRS.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    private static final String APPLICATION_DICOM_JSON = "application/dicom+json";
    private static final String APPLICATION_DICOM_XML = "application/dicom+xml";

    private static final String XML_1_0 = "1.0";
    private static final String XML_1_1 = "1.1";

    @CommandLine.Parameters(
            description = "Service URL.",
            index = "0")
    URI url;

    @CommandLine.Parameters(
            description = "DICOM or Bulkdata files.",
            index = "1..*",
            arity = "1..*")
    List<Path> files = new ArrayList<>();

    @CommandLine.Option(names = "--boundary",
            description = "Specifies a string that acts as a boundary between message parts.")
    String boundary = toString();

    @CommandLine.Option(names = "--xml11",
            description = "Set version in XML declaration of XML metadata to 1.1; 1.0 by default.")
    boolean xml11;

    @CommandLine.Option(names = "--xmlns",
            description = "Include xmlns='http://dicom.nema.org/PS3.19/models/NativeDICOM' attribute in root element of XML metadata.")
    boolean includeNamespaceDeclaration;

    @CommandLine.Option(names = "--pretty",
            description = "Use additional whitespace in XML or JSON metadata.")
    boolean pretty;

    @CommandLine.Option(names = { "-K", "--no-keyword" },
            description = "Do not include keyword attribute of DicomAttribute element in XML metadata.")
    boolean noKeyword;

    @CommandLine.Option(names = "--json",
            description = {
                "Encode Metadata of Metadata and bulk data requests with Content Type 'application/dicom+json'.",
                "Otherwise Metadata will be encoded with Content Type 'application/dicom+xml'."
            })
    boolean json;

    @CommandLine.Option(names = "--photo",
            description = {
                "Encapsulate JPEG images into DICOM Visible Light Photographic images.",
                "Otherwise encapsulate JPEG images into DICOM Secondary Capture images."
            })
    boolean photo;

    @CommandLine.Option(names = { "-v", "--verbose" },
            description = "Include sent and received HTTP headers in the output.")
    boolean verbose;

    @CommandLine.Option(names = { "--tsuid" },
            description =  "Include Transfer Syntax UID in the Content Type of image and video bulkdata.")
    boolean appendTransferSyntax;

    @CommandLine.Option(names = "--metadata",
            description = "Use metadata from specified XML file.")
    Path xmlFile;

    @CommandLine.Option(names = "-m",
            description = "Set element of metadata in format <attribute=value>.")
    Map<TagPath, String> elements = new HashMap<>();

    @CommandLine.Option(names = { "-o", "--output" },
            description = "Write output to <file> instead of stdout.")
    Path file;

    @CommandLine.Option(names = { "-a", "--accept" },
            description = "Specify Acceptable Media Types for the response payload.")
    List<String> type = new ArrayList<>();

    @CommandLine.Option(names = { "--oauth2-bearer" },
            description = "Specify the Bearer Token for OAuth 2.0 server authentication.")
    String token;

    TransformerHandler th;

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new StowRS());
        cl.registerConverter(TagPath.class, TagPath::new);
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        ContentType partType = probeContentType();
        MultipartBody multipartBody = new MultipartBody(boundary);
        if (partType != ContentType.APPLICATION_DICOM) addMetadataParts(multipartBody, partType);
        files.forEach(path -> multipartBody.addPart(
                partType.contentType(appendTransferSyntax, path), path, path.toUri().toString()));
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        authorizationHeader(builder);
        acceptHeader(builder);
        HttpRequest request = builder
                .header("Content-Type", multipartBody.contentType())
                .POST(multipartBody.bodyPublisher())
                .uri(url)
                .build();
        if (verbose) {
            System.out.println("> POST " + request.uri().getRawPath() + " HTTP/1.1");
            System.out.println("> Host: " + request.uri().getHost() + ":" + request.uri().getPort());
            promptHeaders("> ", request.headers());
            multipartBody.prompt();
        }
        if (file != null)
            send(client, request, HttpResponse.BodyHandlers.ofFile(file));
        else
            send(client, request, HttpResponse.BodyHandlers.ofLines()).body().forEach(System.out::println);
        return 0;
    }

    <T> HttpResponse<T> send(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws Exception {
        HttpResponse<T> response = client.send(request, bodyHandler);
        if (verbose) {
            System.out.println("< HTTP/1.1 " + response.statusCode());
            promptHeaders("< ", response.headers());
        }
        return response;
    }

    void authorizationHeader(HttpRequest.Builder builder) {
        if (token != null)
            builder.header("Authorization", "Bearer " + token);
    }

    void acceptHeader(HttpRequest.Builder builder) {
        for (String t : type)
            builder.header("Accept", t);
    }

    private static void promptHeaders(String prefix, HttpHeaders headers) {
        headers.map().forEach((k,v) -> v.stream().forEach(v1 -> System.out.println(prefix + k + ": " + v1)));
        System.out.println(prefix);
    }

    private void addMetadataParts(MultipartBody multipartBody, ContentType type) throws Exception {
        DicomObject metadata = DicomObject.newDicomObject();
        try (InputStream is = StowRS.class.getResourceAsStream(type.resource.apply(this))) {
            SAXReader.parse(is, metadata);
        }
        createUIDs(metadata);
        addInstanceCreationDateAndTime(metadata);
        if (xmlFile != null)
            try (InputStream is = Files.newInputStream(xmlFile)) {
                SAXReader.parse(is, metadata);
            }
        elements.forEach((tagPath, value) -> tagPath.setString(metadata, value));
        if (json) {
            multipartBody.addPart(APPLICATION_DICOM_JSON, toJSON(metadata, type, files), null);
        } else {
            int count = 0;
            for (Path file : files) {
                if (count++ > 0)
                    updateMetadata(metadata);
                type.setBulkDataURI(metadata, file);
                multipartBody.addPart(APPLICATION_DICOM_XML, toXML(metadata), null);
            }
        }
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

    private byte[] toJSON(DicomObject metadata, ContentType type, List<Path> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = jsonGenerator(out)) {
            JSONWriter jsonWriter = new JSONWriter(gen, out);
            gen.writeStartArray();
            int count = 0;
            for (Path file : files) {
                if (count++ > 0)
                    updateMetadata(metadata);
                type.setBulkDataURI(metadata, file);
                jsonWriter.writeDataSet(metadata);
            }
            gen.writeEnd();
        }
        return out.toByteArray();
    }

    private byte[] toXML(DicomObject metadata) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        saxWriter(out).writeDataSet(metadata);
        return out.toByteArray();
    }

    enum ContentType {
        APPLICATION_DICOM("application/dicom", -1, null, null),
        APPLICATION_PDF("application/pdf", Tag.EncapsulatedDocument, x -> "pdf.xml", null),
        TEXT_XML("text/xml", Tag.EncapsulatedDocument, x -> "cda.xml", JPEGParser::new),
        IMAGE_JPEG("image/jpeg", Tag.PixelData, x -> x.photo ? "photo.xml" : "sc.xml", JPEGParser::new),
        IMAGE_JP2("image/jp2", Tag.PixelData, x -> x.photo ? "photo.xml" : "sc.xml", JPEGParser::new),
        VIDEO_MPEG("video/mpeg", Tag.PixelData, x -> "video.xml", MPEG2Parser::new),
        VIDEO_MP4("video/mp4", Tag.PixelData, x -> "video.xml", MP4Parser::new),
        VIDEO_QT("video/quicktime", Tag.PixelData, x -> "video.xml", MP4Parser::new);

        @FunctionalInterface
        interface ParserGenerator {
            CompressedPixelParser apply(SeekableByteChannel channel) throws IOException;
        }

        final String type;
        final int bulkdataTag;
        final Function<StowRS, String> resource;
        final ParserGenerator parserGenerator;

        ContentType(String type, int bulkdataTag, Function<StowRS, String> resource, ParserGenerator parserGenerator) {
            this.type = type;
            this.bulkdataTag = bulkdataTag;
            this.resource = resource;
            this.parserGenerator = parserGenerator;
        }

        static ContentType probe(Path path) {
            try {
                String type = Files.probeContentType(path);
                if (type == null)
                    throw new IOException(String.format("failed to determine content type of file: '%s'", path));
                switch (type.toLowerCase()) {
                    case "application/dicom":
                        return ContentType.APPLICATION_DICOM;
                    case "application/pdf":
                        return ContentType.APPLICATION_PDF;
                    case "text/xml":
                        return ContentType.TEXT_XML;
                    case "image/jpeg":
                        return ContentType.IMAGE_JPEG;
                    case "image/jp2":
                        return ContentType.IMAGE_JP2;
                    case "video/mpeg":
                        return ContentType.VIDEO_MPEG;
                    case "video/mp4":
                        return ContentType.VIDEO_MP4;
                    case "video/quicktime":
                        return ContentType.VIDEO_QT;
                }
                throw new UnsupportedOperationException(
                        String.format("unsupported content type: '%s' of file: '%s'", type, path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public void setBulkDataURI(DicomObject metadata, Path file) {
            metadata.setBulkData(bulkdataTag, VR.OB, file.toUri().toASCIIString(), null);
        }

        public String contentType(boolean appendTransferSyntax, Path path) {
            if (appendTransferSyntax && parserGenerator != null)
                try (SeekableByteChannel channel = Files.newByteChannel(path)) {
                    return type + ";transfer-syntax=" + parserGenerator.apply(channel).getTransferSyntaxUID();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            return type;
        }
    }

    private static void updateMetadata(DicomObject metadata) {
        metadata.setString(Tag.SOPInstanceUID, VR.UI,
                UIDUtils.nameUIDFromString(metadata.getString(Tag.SOPInstanceUID).get()));
        metadata.setInt(Tag.InstanceNumber, VR.IS,
                metadata.getInt(Tag.InstanceNumber).getAsInt() + 1);
    }

    private ContentType probeContentType() {
        return files.stream().map(PathWithType::new).reduce(PathWithType::requiresTypeEquals).get().type;
    }

    private static class PathWithType {
        final Path path;
        final ContentType type;

        PathWithType(Path path) {
            this.path = path;
            this.type = ContentType.probe(path);
        }

        public PathWithType requiresTypeEquals(PathWithType other) {
            if (!type.equals(other.type))
                throw new IllegalArgumentException(
                        String.format("content type: '%s' of file: '%s' differs from content type: '%s' of file: '%s'",
                                type, path, other.type, other.path));
            return this;
        }
    }

    SAXWriter saxWriter(OutputStream out) throws Exception {
        if (th == null) {
            SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
            th = tf.newTransformerHandler();
            Transformer t = th.getTransformer();
            t.setOutputProperty(OutputKeys.INDENT, pretty ? "yes" : "no");
            t.setOutputProperty(OutputKeys.VERSION, xml11 ? XML_1_1 : XML_1_0);
        }
        th.setResult(new StreamResult(out));
        SAXWriter saxWriter = new SAXWriter(th)
                .withIncludeKeyword(!noKeyword)
                .withIncludeNamespaceDeclaration(includeNamespaceDeclaration);
        return saxWriter;
    }

    private JsonGenerator jsonGenerator(OutputStream out) {
        return Json.createGeneratorFactory(pretty ? Map.of(JsonGenerator.PRETTY_PRINTING, true) : null)
                .createGenerator(out);
    }

}


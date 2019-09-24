package org.dcm4che6.tool.upsrs;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.json.JSONWriter;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.DateTimeUtils;
import org.dcm4che6.util.StringUtils;
import org.dcm4che6.util.function.ThrowingConsumer;
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
import java.net.URL;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
@CommandLine.Command(
        name = "upsrs",
        mixinStandardHelpOptions = true,
        versionProvider = UpsRS.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "UPS-RS Worklist Service User-Agent.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExamples:%n",
        footer = {
                "$ upsrs <base-url>/workitem[?<uid>] {xmlFile}|create",
                "-> Create Workitem",
                "$ upsrs <base-url>/workitem/<uid>",
                "-> Retrieve Workitem",
                "$ upsrs <base-url>/workitem/<uid> {xmlFile}|update",
                "-> Update Workitem",
                "$ upsrs -P|-C|-D {transaction-uid} <base-url>/workitem/<uid>/state",
                "-> Change Workitem State to IN PROGRESS, COMPLETED or CANCELED",
                "$ upsrs [--reason=<reason>] <base-url>/workitem/<uid>/cancelrequest",
                "-> Request Cancellation of Workitem",
                "$ upsrs <base-url>/workitem?<filter>",
                "-> Searches for Workitems",
                "$ upsrs <base-url>/workitem/<uid>/subscribers/<aet>[?<deletionlock>]",
                "-> Subscribe to Workitem",
                "$ upsrs <base-url>/workitem/1.2.840.10008.5.1.4.34.5/subscribers/<aet>[?<deletionlock>]",
                "-> Subscribe to Global Worklist",
                "$ upsrs <base-url>/workitem/1.2.840.10008.5.1.4.34.5.1/subscribers/<aet>?<filter>[&<deletionlock>]",
                "-> Subscribe to Filtered Worklist",
                "$ upsrs -U <base-url>/workitem/<uid>/subscribers/<aet>",
                "-> Unsubscribe from Workitem",
                "$ upsrs -U <base-url>/workitem/1.2.840.10008.5.1.4.34.5/subscribers/<aet>",
                "-> Unsubscribe from Global Worklist",
                "$ upsrs -U <base-url>/workitem/1.2.840.10008.5.1.4.34.5.1/subscribers/<aet>",
                "-> Unsubscribe from Filtered Worklist",
                "$ upsrs <base-url>/workitem/1.2.840.10008.5.1.4.34.5/subscribers/<aet>/suspend",
                "-> Suspend Subscription from Global Worklist",
                "$ upsrs <base-url>/workitem/1.2.840.10008.5.1.4.34.5.1/subscribers/<aet>/suspend",
                "-> Suspend Subscription from Filtered Worklist",
                "$ upsrs ws://<host>:<port>/<base-path>/subscribers/<aet>/suspend",
                "-> Open WebSocket channel to receive Event Reports"
        }
)
public class UpsRS implements Callable<Integer>, WebSocket.Listener {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{UpsRS.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    private static final String APPLICATION_DICOM_JSON = "application/dicom+json";
    private static final String APPLICATION_DICOM_XML = "application/dicom+xml";

    private static final String XML_1_0 = "1.0";
    private static final String XML_1_1 = "1.1";

    @CommandLine.Parameters(
            description = "Service URL.",
            index = "0")
    Target target;

    @CommandLine.Parameters(
            description = "Load dataset from specified XML file.",
            index = "1",
            arity = "0..1")
    Path xmlFile;

    @CommandLine.Option(names = "-s",
            description = "Set element of dataset in format <attribute=value>.")
    Map<TagPath, String> elements;

    @CommandLine.Option(names = "--code",
            description = "Set Code Item of dataset in format <sequence=code> with <code> in format <id>^<text>^<scheme>.")
    Map<TagPath, Code> codes;

    @CommandLine.Option(names = "--xml11",
            description = "Set version in XML declaration of XML metadata to 1.1; 1.0 by default.")
    boolean xml11;

    @CommandLine.Option(names = "--xmlns",
            description = "Include xmlns='http://dicom.nema.org/PS3.19/models/NativeDICOM' attribute in root element of XML payload.")
    boolean includeNamespaceDeclaration;

    @CommandLine.Option(names = "--pretty",
            description = "Use additional whitespace in XML or JSON payload.")
    boolean pretty;

    @CommandLine.Option(names = { "-K", "--no-keyword" },
            description = "Do not include keyword attribute of DicomAttribute element in XML payload.")
    boolean noKeyword;

    @CommandLine.Option(names = "--json",
            description = {
                    "Encode payload with Content Type 'application/dicom+json'.",
                    "Otherwise payload will be encoded with Content Type 'application/dicom+xml'."
            })
    boolean json;

    @CommandLine.Option(names = { "-v", "--verbose" },
            description = "Include sent and received HTTP headers in the output.")
    boolean verbose;

    @CommandLine.Option(names = "--wsclose",
            description = "Close WebSocket channel after <time> s.")
    long time = 60;

    @CommandLine.Option(names = "--soclose",
            description = "Close TCP Connection of WebSocket channel <delay> ms after sending Close control frame.")
    long delay = 50;

    @CommandLine.Option(names = { "-o", "--output" },
            description = "Write output to <file> instead of stdout.")
    Path file;

    @CommandLine.Option(names = { "-a", "--accept" },
            description = "Specify Acceptable Media Types for the response payload.",
            defaultValue = "*/*")
    List<String> type = new ArrayList<>();

    @CommandLine.Option(names = "--oauth2-bearer",
            description = "Specify the Bearer Token for OAUTH 2.0 server authentication.")
    String token;

    @CommandLine.Option(names = "--reason",
            description = "Specify Reason of Cancellation Request.")
    String reason;

    @CommandLine.Option(names = "--reason-code",
            description = "Specify Reason Code in format <id>^<text>^<scheme> of Cancellation Request.")
    Code code;

    @CommandLine.Option(names = "--contact-uri",
            description = "Specify Contact URI of Cancellation Request.")
    String uri;

    @CommandLine.Option(names = "--contact",
            description = "Specify Contact Display Name of Cancellation Request.")
    String name;

    @CommandLine.ArgGroup(exclusive = true)
    Exclusive exclusive = new Exclusive();
    static class Exclusive {
        @CommandLine.Option(names = {"-P", "--process"}, required = true,
                description = "Change Workitem State to IN PROGRESS with given <transaction-uid>",
                paramLabel = "<transaction-uid>")
        String process;
        @CommandLine.Option(names = {"-C", "--complete"}, required = true,
                description = "Change Workitem State to COMPLETED with given <transaction-uid>",
                paramLabel = "<transaction-uid>")
        String complete;
        @CommandLine.Option(names = {"-D", "--cancel"}, required = true,
                description = "Change Workitem State to CANCELED with given <transaction-uid>",
                paramLabel = "<transaction-uid>")
        String cancel;
        @CommandLine.Option(names = {"-U", "--unsubsribe"}, required = true,
                description = "Unsubscribes from the Target Worklist or Target Workitem.")
        boolean unsubscribe;
    }

    StringBuilder event = new StringBuilder();

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new UpsRS());
        cl.registerConverter(Target.class, Target::new);
        cl.registerConverter(TagPath.class, TagPath::new);
        cl.registerConverter(Code.class, Code::new);
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        target.type.op.accept(this);
        return 0;
    }

    private void workitems() throws Exception {
        if (xmlFile == null) {
            get();
        } else {
            post(ensureSPSStartDataTime(workitemPayload(xmlFileEquals("create")
                    ? UpsRS.class.getResource("create.xml")
                    : xmlFile.toUri().toURL())));
        }
    }

    private DicomObject ensureSPSStartDataTime(DicomObject dcmobj) {
        Optional<DicomElement> dicomElement = dcmobj.get(Tag.ScheduledProcedureStepStartDateTime);
        if (dicomElement.isEmpty() || dicomElement.get().isEmpty()) {
            dcmobj.setString(Tag.ScheduledProcedureStepStartDateTime, VR.DA,
                    DateTimeUtils.formatDT(LocalDateTime.now()));
        }
        return dcmobj;
    }

    private void workitem() throws Exception {
        if (xmlFile == null) {
            get();
        } else {
            post(workitemPayload(xmlFileEquals("update")
                    ? null
                    : xmlFile.toUri().toURL()));
        }
    }

    private void state() throws Exception {
        put(statePayload());
    }

    private void cancelrequest() throws Exception {
        post(cancelrequestPayload());
    }

    private void subscribers() throws Exception {
        if (exclusive.unsubscribe) {
            delete();
        } else {
            post(null);
        }
    }

    private void suspend() throws Exception {
        post(null);
    }

    private void websocket() {
        if (verbose) {
            System.out.println("> GET " + target.url.getRawPath() + " HTTP/1.1");
            System.out.println("> Host: " + target.url.getHost() + ":" + target.url.getPort());
            System.out.println("> Connection: Upgrade");
            System.out.println("> Upgrade: WebSocket");
        }
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(target.url, this)
                .whenComplete(this::listening)
                .join();
    }

    private boolean xmlFileEquals(String str) {
        return xmlFile.toString().equals(str);
    }

    private DicomObject workitemPayload(URL xmlURL) throws Exception {
        DicomObject dcmobj = DicomObject.newDicomObject();
        if (xmlURL != null) {
            try (InputStream is = xmlURL.openStream()) {
                SAXReader.parse(is, dcmobj);
            }
        }
        if (elements != null) {
            elements.forEach((tagPath, value) -> tagPath.setString(dcmobj, value));
        }
        if (codes != null) {
            codes.forEach((tagPath, code) -> tagPath.setCode(dcmobj, code));
        }
        return dcmobj;
    }

    private DicomObject statePayload() {
        if (exclusive.process != null)
            return statePayload("IN PROGRESS", exclusive.process);
        if (exclusive.complete != null)
            return statePayload("COMPLETED", exclusive.complete);
        if (exclusive.cancel != null)
            return statePayload("CANCELED", exclusive.cancel);

        throw new IllegalArgumentException("" + target.url + " requires option -P, -C or -D");
    }

    static DicomObject statePayload(String code, String uid) {
        DicomObject dcmobj =  DicomObject.newDicomObject();
        dcmobj.setString(Tag.TransactionUID, VR.UI, uid);
        dcmobj.setString(Tag.ProcedureStepState, VR.CS, code);
        return dcmobj;
    }

    private DicomObject cancelrequestPayload() {
        DicomObject dcmobj =  DicomObject.newDicomObject();
        if (reason != null)
            dcmobj.setString(Tag.ReasonForCancellation, VR.LT, reason);
        if (code != null)
            dcmobj.newDicomSequence(Tag.ProcedureStepDiscontinuationReasonCodeSequence).addItem(code.toItem());
        if (uri != null)
            dcmobj.setString(Tag.ContactURI, VR.UR, uri);
        if (name != null)
            dcmobj.setString(Tag.ContactDisplayName, VR.LO, name);
        return dcmobj;
    }

    private void get() throws Exception {
        send(builder(null).GET().uri(target.url).build());
    }

    private void post(DicomObject data) throws Exception {
        send(builder(data).POST(toBodyPublisher(data)).uri(target.url).build());
    }

    private void put(DicomObject data) throws Exception {
        send(builder(data).PUT(toBodyPublisher(data)).uri(target.url).build());
    }

    private void delete() throws Exception {
        send(builder(null).DELETE().uri(target.url).build());
    }

    private HttpRequest.BodyPublisher toBodyPublisher(DicomObject data) throws Exception {
        return data == null || data.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(json ? toJSON(data) : toXML(data));
    }

    private void send(HttpRequest request) throws Exception {
        if (verbose) {
            promptRequest(request);
        }
        if (file != null)
            send(request, HttpResponse.BodyHandlers.ofFile(file));
        else
            send(request, HttpResponse.BodyHandlers.ofLines()).body().forEach(System.out::println);
    }

    private byte[] toJSON(DicomObject data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = jsonGenerator(out)) {
            JSONWriter jsonWriter = new JSONWriter(gen, out);
            jsonWriter.writeDataSet(data);
        }
        return out.toByteArray();
    }

    private byte[] toXML(DicomObject data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        saxWriter(out).writeDataSet(data);
        return out.toByteArray();
    }

    private JsonGenerator jsonGenerator(OutputStream out) {
        return Json.createGeneratorFactory(pretty ? Map.of(JsonGenerator.PRETTY_PRINTING, true) : null)
                .createGenerator(out);
    }

    private SAXWriter saxWriter(OutputStream out) throws Exception {
        SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
        TransformerHandler th = tf.newTransformerHandler();
        Transformer t = th.getTransformer();
        t.setOutputProperty(OutputKeys.INDENT, pretty ? "yes" : "no");
        t.setOutputProperty(OutputKeys.VERSION, xml11 ? XML_1_1 : XML_1_0);
        th.setResult(new StreamResult(out));
        return new SAXWriter(th)
                .withIncludeKeyword(!noKeyword)
                .withIncludeNamespaceDeclaration(includeNamespaceDeclaration);
    }

    private void promptRequest(HttpRequest request) {
        System.out.println("> " + request.method() + " " + pathWithQuery(request.uri()) + " HTTP/1.1");
        System.out.println("> Host: " + request.uri().getHost() + ":" + request.uri().getPort());
        promptHeaders("> ", request.headers());
    }

    private String pathWithQuery(URI uri) {
        return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + '?' + uri.getRawQuery();
    }

    private HttpRequest.Builder builder(DicomObject data) {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (token != null)
            builder.header("Authorization", "Bearer " + token);
        for (String t : type)
            builder.header("Accept", t);
        if (data != null && !data.isEmpty())
            builder.header("Content-Type", json ? APPLICATION_DICOM_JSON : APPLICATION_DICOM_XML);
        return builder;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpResponse<T> response = client.send(request, bodyHandler);
        if (verbose) {
            System.out.println("< HTTP/1.1 " + response.statusCode());
            promptHeaders("< ", response.headers());
        }
        return response;
    }

    private static void promptHeaders(String prefix, HttpHeaders headers) {
        headers.map().forEach((k,v) -> v.stream().forEach(v1 -> System.out.println(prefix + k + ": " + v1)));
        System.out.println(prefix);
    }

    private void listening(WebSocket webSocket, Throwable th) {
        if (th != null) {
            th.printStackTrace(System.out);
        } else {
            if (verbose) {
                System.out.println("< Connection: Upgrade");
                System.out.println("< Upgrade: WebSocket");
            }
            try {
                Thread.sleep(time * 1000L);
                if (verbose) {
                    System.out.println("* Send Close control frame");
                }
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
                Thread.sleep(delay);
                if (verbose) {
                    System.out.println("* Close TCP Connection");
                }
                webSocket.abort();
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        event.append(data);
        if (last) {
            if (file != null) {
                appendToFile(event);
            } else {
                System.out.println(event);
            }
            event.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    private void appendToFile(StringBuilder event) {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE)) {
            w.append(event);
            w.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Target {
        final URI url;
        final Type type;

        Target(String str) {
            try {
                url = URI.create(str);
                type = Type.of(url);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.TypeConversionException("Invalid Service URL");
            }
        }

        enum Type {
            workitems(UpsRS::workitems),
            workitem(UpsRS::workitem),
            state(UpsRS::state),
            cancelrequest(UpsRS::cancelrequest),
            subscribers(UpsRS::subscribers),
            suspend(UpsRS::suspend),
            websocket(UpsRS::websocket);

            final ThrowingConsumer<UpsRS, Exception> op;

            Type(ThrowingConsumer<UpsRS, Exception> op) {
                this.op = op;
            }

            static Type of(URI url) {
                String[] path = StringUtils.split(url.getPath(), '/');
                switch (url.getScheme()) {
                    case "http":
                    case "https":
                        switch (path[path.length - 1]) {
                            case "workitems":
                                return workitems;
                            case "state":
                                if (path[path.length - 3].equals("workitems"))
                                    return state;
                                break;
                            case "cancelrequest":
                                if (path[path.length - 3].equals("workitems"))
                                    return cancelrequest;
                                break;
                            case "suspend":
                                if (path[path.length - 3].equals("subscribers")
                                        && path[path.length - 5].equals("workitems"))
                                    return suspend;
                                break;
                            default:
                                switch (path[path.length - 2]) {
                                    case "workitems":
                                        return workitem;
                                    case "subscribers":
                                        if (path[path.length - 5].equals("workitems"))
                                            return subscribers;
                                        break;
                                }
                        }
                        break;
                    case "ws":
                    case "wss":
                        if (path[path.length - 2].equals("subscribers"))
                            return websocket;
                }
                throw new IllegalArgumentException();
            }
        }
    }
}

package org.dcm4che.tool.dcm2xml;

import org.dcm4che.data.DicomInputStream;
import org.dcm4che.xml.DicomContentHandlerAdapter;
import picocli.CommandLine;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
@CommandLine.Command(
        name = "dcm2xml",
        mixinStandardHelpOptions = true,
        version = "dcm2xml 6.0.0",
        descriptionHeading = "%n",
        description = { "The dcm2xml utility converts the contents of a DICOM file (file format or raw data set) to " +
                "XML (Extensible Markup Language) according the \"Native DICOM Model\" which is specified for the " +
                "DICOM Application Hosting service found in DICOM part 19." },
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {
                "$ dcm2xml image.dcm",
                "Write XML representation of DICOM file image.dcm to standard output, including only a reference " +
                        "to the pixel data in image.dcm" }
)
public class Dcm2Xml implements Callable<Dcm2Xml> {

    private static final String XML_1_0 = "1.0";
    private static final String XML_1_1 = "1.1";

    @CommandLine.Parameters(description = "DICOM input filename to be converted. Use '-- -' to read from standard input.")
    Path file;

    @CommandLine.Option(names = "--xml11",
            description = "Set version in XML declaration to 1.1; 1.0 by default.")
    boolean xml11;

    @CommandLine.Option(names = "--xmlns",
            description = "Include xmlns='http://dicom.nema.org/PS3.19/models/NativeDICOM' attribute in root element.")
    boolean includeNamespaceDeclaration;

    @CommandLine.Option(names = { "-I", "--indent" },
            description = "Use additional whitespace in XML output.")
    boolean indent;

    @CommandLine.Option(names = { "-K", "--no-keyword" },
            description = "Do not include keyword attribute of DicomAttribute element in XML output.")
    boolean noKeyword;

    @CommandLine.Option(names = { "-B", "--no-bulkdata" },
            description = "Do not include bulkdata in XML output; by default, references to bulkdata are included.")
    boolean noBulkData;

    @CommandLine.Option(names = { "--inline-bulkdata" },
            description = "Include bulkdata directly in XML output; by default, only references to bulkdata are included.")
    boolean withBulkData;

    @CommandLine.Option(names = { "--bulkdata" },
            description = {
            "Filename to which extracted bulkdata is stored if the DICOM object is read from standard input.",
            "Default: <random-number>.dcm2xml"
            },
            paramLabel = "file")
    Path blkfile;

    @CommandLine.Option(names = { "-x", "--xsl" },
            description = "Apply XSLT stylesheet specified by URL.")
    URL url;

    public static void main(String[] args) {
        CommandLine.call(new Dcm2Xml(), args);
    }

    @Override
    public Dcm2Xml call() throws Exception {
        boolean stdin = file.toString().equals("-");
        TransformerHandler th = getTransformerHandler();
        Transformer t = th.getTransformer();
        t.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        t.setOutputProperty(OutputKeys.VERSION, xml11 ? XML_1_1 : XML_1_0);
        th.setResult(new StreamResult(System.out));
        DicomContentHandlerAdapter handler = new DicomContentHandlerAdapter(th)
                .withIncludeKeyword(!noKeyword)
                .withIncludeNamespaceDeclaration(includeNamespaceDeclaration);
        handler.startDocument();
        try (DicomInputStream dis = new DicomInputStream(stdin ? System.in : Files.newInputStream(file))
                .withInputHandler(handler)) {
            if (!withBulkData) {
                dis.withBulkData(DicomInputStream::isBulkData);
                if (!noBulkData)
                    if (stdin)
                        dis.spoolBulkDataTo(blkfile());
                    else
                        dis.withBulkDataURI(file);
            }
            dis.readDataSet();
        }
        handler.endDocument();
        return this;
    }

    private Path blkfile() throws IOException {
        return blkfile != null ? blkfile : Files.createTempFile(Paths.get(""), null, ".dcm2xml");
    }

    private TransformerHandler getTransformerHandler()
            throws TransformerConfigurationException, IOException {
        SAXTransformerFactory tf = (SAXTransformerFactory)
                TransformerFactory.newInstance();
        if (url == null)
            return tf.newTransformerHandler();

        TransformerHandler th = tf.newTransformerHandler(
                new StreamSource(url.toExternalForm()));
        return th;
    }
}

package org.dcm4che.tool.xml2dcm;

import org.dcm4che.data.DicomEncoding;
import org.dcm4che.data.DicomObject;
import org.dcm4che.data.DicomOutputStream;
import org.dcm4che.xml.SAXReader;
import picocli.CommandLine;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
@CommandLine.Command(
        name = "xml2dcm",
        mixinStandardHelpOptions = true,
        version = "xml2dcm 6.0.0",
        descriptionHeading = "%n",
        description = { "The xml2dcm utility converts the contents of an XML (Extensible Markup Language) document " +
                "to DICOM file or data set. The XML document is expected to be valid according the 'Native DICOM " +
                "Model' which is specified for the DICOM Application Hosting service found in DICOM part 19." },
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {
                "$ xml2dcm dataset.xml dataset.dcm",
                "Convert XML document dataset.xml to DICOM file dataset.dcm" }
)
public class Xml2Dcm implements Callable<Xml2Dcm> {

    @CommandLine.Parameters(
            description = "XML input filename to be converted. Use '-- -' to read from standard input.",
            index = "0")
    Path xmlfile;

    @CommandLine.Parameters(
            description = "DICOM output filename.",
            index = "1")
    Path dcmfile;

    public static void main(String[] args) {
        CommandLine.call(new Xml2Dcm(), args);
    }

    @Override
    public Xml2Dcm call() throws Exception {
        boolean stdin = xmlfile.toString().equals("-");
        DicomObject fmi;
        DicomObject dcmobj = new DicomObject();
        try (InputStream in = stdin ? System.in : Files.newInputStream(xmlfile)) {
            fmi = SAXReader.parse(in, dcmobj);
        }
        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(dcmfile))) {
            if (fmi != null)
                dos.writeFileMetaInformation(fmi);
            else
                dos.withEncoding(DicomEncoding.IVR_LE);
            dos.writeDataSet(dcmobj);
        }
        return this;
    }
}

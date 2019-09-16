package org.dcm4che6.tool.xml2dcm;

import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.data.Tag;
import org.dcm4che6.xml.SAXReader;
import picocli.CommandLine;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2019
 */
@CommandLine.Command(
        name = "xml2dcm",
        mixinStandardHelpOptions = true,
        versionProvider = Xml2Dcm.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = {"The xml2dcm utility converts the contents of an XML (Extensible Markup Language) document " +
                "to DICOM file or data set. The XML document is expected to be valid according the 'Native DICOM " +
                "Model' which is specified for the DICOM Application Hosting service found in DICOM part 19."},
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {
                "$ xml2dcm dataset.xml dataset.dcm",
                "Convert XML document dataset.xml to DICOM file dataset.dcm"}
)
public class Xml2Dcm implements Callable<Integer> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{Xml2Dcm.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(
            description = "XML input filename to be converted (stdin: '-- -').",
            index = "0")
    Path xmlfile;

    @CommandLine.Parameters(
            description = "DICOM output filename.",
            index = "1")
    Path dcmfile;

    @CommandLine.Option(names = {"-F", "--no-fmi"},
            description = "Ignore File Meta Information from XML file.")
    boolean nofmi;

    @CommandLine.Option(names = {"-f", "--fmi"},
            description = "Write file format (with File Meta Information).")
    boolean addfmi;

    @CommandLine.Option(names = {"-t"},
            description = {"Write with Implicit VR Little Endian TS ('IVR_LE'), Explicit VR Little Endian TS " +
                    "('EVR_LE'), Explicit VR Big Endian TS ('EVR_BE'), or Deflated Explicit VR Little Endian TS " +
                    "('DEFL_EVR_LE').",
                    "  Default: Write with same TS as input"},
            paramLabel = "<TS>")
    DicomEncoding dicomEncoding;

    @CommandLine.Option(names = {"-g"},
            description = "Write with group length elements.")
    boolean includeGroupLength;

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

    public static void main(String[] args) {
        new CommandLine(new Xml2Dcm()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        boolean stdin = xmlfile.toString().equals("-");
        DicomObject fmi;
        DicomObject dcmobj = DicomObject.newDicomObject();
        try (InputStream in = stdin ? System.in : Files.newInputStream(xmlfile)) {
            fmi = SAXReader.parse(in, dcmobj);
        }
        if (dicomEncoding == null) {
            dicomEncoding =  fmi != null
                    ? DicomEncoding.of(fmi.getString(Tag.TransferSyntaxUID).get())
                    : DicomEncoding.EVR_LE;
        }
        if (nofmi) {
            fmi = null;
        }
        if (fmi == null && addfmi) {
            fmi = dcmobj.createFileMetaInformation(dicomEncoding.transferSyntaxUID);
        }
        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(dcmfile))
            .withIncludeGroupLength(includeGroupLength)
            .withSequenceLengthEncoding(sequenceLengthEncoding)
            .withItemLengthEncoding(itemLengthEncoding)) {
            if (fmi != null) {
                dos.writeFileMetaInformation(fmi).withEncoding(fmi);
            } else {
                dos.withEncoding(DicomEncoding.IVR_LE);
            }
            dos.writeDataSet(dcmobj);
        }
        return 0;
    }
}

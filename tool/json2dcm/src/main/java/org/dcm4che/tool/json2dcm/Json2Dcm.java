package org.dcm4che.tool.json2dcm;

import org.dcm4che.data.*;
import org.dcm4che.json.JSONReader;
import picocli.CommandLine;

import javax.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
@CommandLine.Command(
        name = "json2dcm",
        mixinStandardHelpOptions = true,
        versionProvider = Json2Dcm.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = {"The json2dcm utility converts the contents of a JSON (JavaScript Object Notation) file to " +
                "DICOM file or data set. The input refers to the 'DICOM JSON Model', which is found in DICOM Part 18 " +
                "Section F."},
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {
                "$ json2dcm dataset.json dataset.dcm",
                "Convert JSON file dataset.json to DICOM file dataset.dcm"}
)
public class Json2Dcm implements Callable<Json2Dcm> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() throws Exception {
            return new String[]{Json2Dcm.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(
            description = "JSON input filename to be converted (stdin: '-- -').",
            index = "0")
    Path jsonfile;

    @CommandLine.Parameters(
            description = "DICOM output filename.",
            index = "1")
    Path dcmfile;

    @CommandLine.Option(names = {"-F", "--no-fmi"},
            description = "Ignore File Meta Information from JSON file.")
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
        CommandLine.call(new Json2Dcm(), args);
    }

    @Override
    public Json2Dcm call() throws Exception {
        boolean stdin = jsonfile.toString().equals("-");
        DicomObject fmi;
        DicomObject dcmobj = new DicomObject();
        try (JSONReader reader =
                     new JSONReader(Json.createParser(stdin ? System.in : Files.newInputStream(jsonfile)))) {
            fmi = reader.readDataset(dcmobj);
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
                dos.writeFileMetaInformation(fmi);
            } else {
                dos.withEncoding(DicomEncoding.IVR_LE);
            }
            dos.writeDataSet(dcmobj);
        }
        return this;
    }
}

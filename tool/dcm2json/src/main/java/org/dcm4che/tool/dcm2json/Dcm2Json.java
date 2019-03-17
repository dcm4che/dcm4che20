package org.dcm4che.tool.dcm2json;

import org.dcm4che.data.DicomInputStream;
import org.dcm4che.json.JSONWriter;
import picocli.CommandLine;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
@CommandLine.Command(
        name = "dcm2json",
        mixinStandardHelpOptions = true,
        versionProvider = Dcm2Json.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = {
                "The dcm2json utility converts the contents of a DICOM file (file format or raw data set) to " +
                "JSON (JavaScript Object Notation). The output refers to the 'DICOM JSON Model', which is " +
                "found in DICOM Part 18 Section F." },
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = {
                "$ dcm2json image.dcm",
                "Write JSON representation of DICOM file image.dcm to standard output, including only a reference " +
                "to the pixel data in image.dcm" }
)
public class Dcm2Json implements Callable<Dcm2Json> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() throws Exception {
            return new String[]{Dcm2Json.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(description = "DICOM input filename to be converted. Use '-- -' to read from standard input.")
    Path file;

    @CommandLine.Option(names = { "-I", "--indent" },
            description = "Use additional whitespace in JSON output.")
    boolean indent;

    @CommandLine.Option(names = { "-B", "--no-bulkdata" },
            description = "Do not include bulkdata in XML output; by default, references to bulkdata are included.")
    boolean noBulkData;

    @CommandLine.Option(names = { "--inline-bulkdata" },
            description = "Include bulkdata directly in XML output; by default, only references to bulkdata are included.")
    boolean inlineBulkData;

    @CommandLine.Option(names = { "--bulkdata" },
            description = {
            "Filename to which extracted bulkdata is stored if the DICOM object is read from standard input.",
            "Default: <random-number>.dcm2json"
            },
            paramLabel = "file")
    Path blkfile;

    public static void main(String[] args) {
        CommandLine.call(new Dcm2Json(), args);
    }

    @Override
    public Dcm2Json call() throws Exception {
        boolean stdin = file.toString().equals("-");
        try (JsonGenerator gen = createGenerator(System.out);
             DicomInputStream dis = new DicomInputStream(stdin ? System.in : Files.newInputStream(file))) {
            gen.writeStartObject();
            dis.withInputHandler(new JSONWriter(gen, System.out));
            if (!inlineBulkData) {
                dis.withBulkData(DicomInputStream::isBulkData);
                if (!noBulkData)
                    if (stdin)
                        dis.spoolBulkDataTo(blkfile());
                    else
                        dis.withBulkDataURI(file);
            }
            dis.readDataSet();
            gen.writeEnd();
        }
        return this;
    }

    private Path blkfile() throws IOException {
        return blkfile != null ? blkfile : Files.createTempFile(Paths.get(""), null, ".dcm2json");
    }

    private JsonGenerator createGenerator(OutputStream out) {
        return Json.createGeneratorFactory(indent ? Map.of(JsonGenerator.PRETTY_PRINTING, true) : null)
                .createGenerator(out);
    }
}

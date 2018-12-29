package org.dcm4che.tool.dcmdump;

import org.dcm4che.data.*;
import org.dcm4che.util.TagUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2018
 */
@CommandLine.Command(
        name = "dcmdump",
        mixinStandardHelpOptions = true,
        version = "dcmdump 6.0.0",
        descriptionHeading = "%n",
        description = "The dcmdump utility dumps the contents of a DICOM file (file format or raw data set) " +
                "to standard output in textual form.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ dcmdump image.dcm", "Dump DICOM file image.dcm to standard output" }
)
public class DcmDump implements Callable<DcmDump> {

    @CommandLine.Parameters(description = "DICOM file to dump")
    Path file;

    @CommandLine.Option(names = { "-w", "--width" },
            description = "Set output width to <cols>.")
    int cols = 78;

    public static void main(String[] args) {
        CommandLine.call(new DcmDump(), args);
    }

    @Override
    public DcmDump call() throws Exception {
        try (DicomReader reader = new DicomReader(Files.newInputStream(file))) {
            reader.withInputHandler(new DumpInputHandler(reader));
            reader.readDataSet();
        }
        return this;
    }

    private class DumpInputHandler extends FilterDicomInputHandler {
        private final DicomReader reader;
        private int count;

        public DumpInputHandler(DicomReader reader) {
            super(reader);
            this.reader = reader;
        }

        @Override
        public boolean startElement(DicomElement dcmElm, boolean bulkData) throws IOException {
            if (count++ == 0 && dcmElm.getStreamPosition() != 0) {
                System.out.println(reader.promptFilePreambleTo(toPrompt(0), cols));
            }
            int tag = dcmElm.tag();
            VR vr = dcmElm.vr();
            if (tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet || TagUtils.isPrivateCreator(tag)) {
                dcmElm.containedBy().setString(tag, vr, dcmElm.stringValues());
            }
            System.out.println(reader.promptTo(dcmElm, toPrompt(dcmElm.getStreamPosition()), cols));
            return true;
        }

        private StringBuilder toPrompt(long streamPosition) {
            return new StringBuilder().append(streamPosition).append(':').append(' ');
        }

        @Override
        public boolean endElement(DicomElement dcmElm) {
            int valueLength = dcmElm.valueLength();
            if (valueLength == -1) {
                System.out.println(
                        dcmElm.containedBy()
                                .appendNestingLevel(
                                        toPrompt(reader.getStreamPosition() - 8))
                                .append("(FFFE,E0DD) #0 SequenceDelimitationItem"));
            }
            return true;
        }

        @Override
        public boolean startItem(DicomObject dcmObj) {
            System.out.println(
                    dcmObj.appendNestingLevel(
                            toPrompt(reader.getStreamPosition() - 8))
                            .append("(FFFE,E000) #").append(dcmObj.getItemLength())
                            .append(" Item #").append(dcmObj.containedBy().size() + 1));
            return super.startItem(dcmObj);
        }

        @Override
        public boolean endItem(DicomObject dcmObj) {
            if (dcmObj.getItemLength() == -1) {
                System.out.println(
                        dcmObj.appendNestingLevel(toPrompt(reader.getStreamPosition() - 8))
                                .append("(FFFE,E00D) #0 ItemDelimitationItem"));
            }
            return true;
        }

        @Override
        public boolean dataFragment(DataFragment dataFragment) throws IOException {
            System.out.println(
                    reader.promptTo(dataFragment, toPrompt(reader.getStreamPosition() - 8), cols));
            return true;
        }
    }
}


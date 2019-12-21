package org.dcm4che6.tool.dcmdump;

import org.dcm4che6.data.*;
import org.dcm4che6.data.DataFragment;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.util.TagUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2018
 */
@CommandLine.Command(
        name = "dcmdump",
        mixinStandardHelpOptions = true,
        versionProvider = DcmDump.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The dcmdump utility dumps the contents of a DICOM file (file format or raw data set) " +
                "to standard output in textual form.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ dcmdump image.dcm", "Dump DICOM file image.dcm to standard output." }
)
public class DcmDump implements Callable<Integer>, DicomInputHandler {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{DcmDump.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(description = "DICOM input file to be dumped.")
    Path file;

    @CommandLine.Option(names = { "-w", "--width" },
            description = "Set output width to <cols>.")
    int cols = 80;

    int count;

    public static void main(String[] args) {
        new CommandLine(new DcmDump()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(file))) {
            dis.withInputHandler(this);
            dis.readDataSet();
        }
        return 0;
    }


    @Override
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        if (count++ == 0 && dcmElm.getStreamPosition() != 0) {
            System.out.println(dis.promptFilePreambleTo(toPrompt(0), cols));
        }
        System.out.println(dis.promptTo(dcmElm, toPrompt(dcmElm.getStreamPosition()), cols));
        int tag = dcmElm.tag();
        VR vr = dcmElm.vr();
        if (tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet || TagUtils.isPrivateCreator(tag)) {
            dis.loadValueFromStream();
            dcmElm.containedBy().setString(tag, vr, dcmElm.stringValues());
        }
        return true;
    }

    private StringBuilder toPrompt(long streamPosition) {
        return new StringBuilder().append(streamPosition).append(':').append(' ');
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) {
        int valueLength = dcmElm.valueLength();
        if (valueLength == -1) {
            System.out.println(
                    dcmElm.containedBy()
                            .appendNestingLevel(
                                    toPrompt(dis.getStreamPosition() - 8))
                            .append("(FFFE,E0DD) #0 SequenceDelimitationItem"));
        }
        return true;
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) {
        dcmSeq.addItem(dcmObj);
        System.out.println(
                dcmObj.appendNestingLevel(
                        toPrompt(dis.getStreamPosition() - 8))
                        .append("(FFFE,E000) #").append(dcmObj.getItemLength())
                        .append(" Item #").append(dcmSeq.size()));
        return true;
    }

    @Override
    public boolean endItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) {
        if (dcmObj.getItemLength() == -1) {
            System.out.println(
                    dcmObj.appendNestingLevel(toPrompt(dis.getStreamPosition() - 8))
                            .append("(FFFE,E00D) #0 ItemDelimitationItem"));
        }
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DicomElement fragments, DataFragment dataFragment)
            throws IOException {
        System.out.println(
                dis.promptTo(dataFragment, toPrompt(dis.getStreamPosition() - 8), cols));
        return true;
    }
}


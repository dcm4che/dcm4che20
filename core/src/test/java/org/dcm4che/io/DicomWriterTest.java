package org.dcm4che.io;

import org.dcm4che.data.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class DicomWriterTest {
    private static final byte[] DICM_FMI_DEFL = {'D', 'I', 'C', 'M',
            2, 0, 0, 0, 'U', 'L', 4, 0, 30, 0, 0, 0,
            2, 0, 16, 0, 'U', 'I', 22, 0,
            49, 46, 50, 46, 56, 52, 48, 46, 49, 48, 48, 48, 56, 46, 49, 46, 50, 46, 49, 46, 57, 57,
            3, 0};
    private static final byte[] C_ECHO_RQ = {
            0, 0, 0, 0, 4, 0, 0, 0, 56, 0, 0, 0,
            0, 0, 2, 0, 18, 0, 0, 0,
            49, 46, 50, 46, 56, 52, 48, 46, 49, 48, 48, 48, 56, 46, 49, 46, 49, 0,
            0, 0, 0, 1, 2, 0, 0, 0, 48, 0,
            0, 0, 16, 1, 2, 0, 0, 0, 1, 0,
            0, 0, 0, 8, 2, 0, 0, 0, 1, 1
    };
    private static final byte[] IVR_LE = {
            8, 0, 16, 17, 0, 0, 0, 0,
            64, 0, 0, 1, -1, -1, -1, -1,
            -2, -1, 0, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };
    private static final byte[] EVR_BE_GROUP = {
            0, 8, 0, 0, 85, 76, 0, 4, 0, 0, 0, 12,
            0, 8, 17, 16, 83, 81, 0, 0, 0, 0, 0, 0,
            0, 64, 0, 0, 85, 76, 0, 4, 0, 0, 0, 28,
            0, 64, 1, 0, 83, 81, 0, 0, -1, -1, -1, -1,
            -1, -2, -32, 0, 0, 0, 0, 0,
            -1, -2, -32, -35, 0, 0, 0, 0
    };
    private static final byte[] EXPL_ITEM_LEN = {
            8, 0, 0, 0, 4, 0, 0, 0, 16, 0, 0, 0,
            8, 0, 16, 17, -1, -1, -1, -1,
            -2, -1, -35, -32, 0, 0, 0, 0,
            64, 0, 0, 0, 4, 0, 0, 0, 24, 0, 0, 0,
            64, 0, 0, 1, -1, -1, -1, -1,
            -2, -1, 0, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };
    private static final byte[] EXPL_SEQ_LEN = {
            8, 0, 0, 0, 4, 0, 0, 0, 8, 0, 0, 0,
            8, 0, 16, 17, 0, 0, 0, 0,
            64, 0, 0, 0, 4, 0, 0, 0, 24, 0, 0, 0,
            64, 0, 0, 1, 16, 0, 0, 0,
            -2, -1, 0, -32, -1, -1, -1, -1,
            -2, -1, 13, -32, 0, 0, 0, 0
    };

    private static byte[] preamble_fmi_defl() {
        byte[] b = new byte[128 + DICM_FMI_DEFL.length];
        System.arraycopy(DICM_FMI_DEFL, 0, b, 128, DICM_FMI_DEFL.length);
        return b;
    }

    private static DicomObject c_echo_rq() {
        DicomObject cmd = new DicomObject();
        cmd.setString(Tag.AffectedSOPClassUID, VR.UI, UID.VerificationSOPClass);
        cmd.setInt(Tag.CommandField, VR.US, 48);
        cmd.setInt(Tag.MessageID, VR.US, 1);
        cmd.setInt(Tag.CommandDataSetType, VR.US, 0x0101);
        return cmd;
    }

    private static DicomObject sequences() {
        DicomObject dcmObj = new DicomObject();
        dcmObj.newDicomSequence(Tag.ReferencedStudySequence);
        DicomSequence spsSeq = dcmObj.newDicomSequence(Tag.ScheduledProcedureStepSequence);
        DicomObject spsItem = new DicomObject(spsSeq);
        spsSeq.addItem(spsItem);
        return dcmObj;
    }

    @Test
    void writeFileMetaInformation() throws IOException {
        DicomObject fmi = new DicomObject();
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.DeflatedExplicitVRLittleEndian);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DicomWriter w = new DicomWriter(bout)) {
            w.writeFileMetaInformation(fmi).writeDataSet(new DicomObject());
        }
        assertArrayEquals(preamble_fmi_defl(), bout.toByteArray());
    }

    @Test
    void writeCommandSet() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DicomWriter w = new DicomWriter(bout)) {
            w.writeCommandSet(c_echo_rq());
        }
        assertArrayEquals(C_ECHO_RQ, bout.toByteArray());
    }

    @Test
    void writeSequenceIVR_LE() throws IOException {
        writeSequence(DicomEncoding.IVR_LE, false,
                DicomWriter.LengthEncoding.UNDEFINED_OR_ZERO,
                DicomWriter.LengthEncoding.UNDEFINED_OR_ZERO,
                IVR_LE);
    }

    @Test
    void writeSequenceEVR_BE_GROUP() throws IOException {
        writeSequence(DicomEncoding.EVR_BE, true,
                DicomWriter.LengthEncoding.UNDEFINED_OR_ZERO,
                DicomWriter.LengthEncoding.UNDEFINED_OR_ZERO,
                EVR_BE_GROUP);
    }

    @Test
    void writeSequenceExplicitItemLength() throws IOException {
        writeSequence(DicomEncoding.IVR_LE, true,
                DicomWriter.LengthEncoding.UNDEFINED,
                DicomWriter.LengthEncoding.EXPLICIT,
                EXPL_ITEM_LEN);
    }

    @Test
    void writeSequenceExplicitSequenceLength() throws IOException {
        writeSequence(DicomEncoding.IVR_LE, true,
                DicomWriter.LengthEncoding.EXPLICIT,
                DicomWriter.LengthEncoding.UNDEFINED,
                EXPL_SEQ_LEN);
    }

    private void writeSequence(DicomEncoding encoding, boolean includeGroupLength,
                               DicomWriter.LengthEncoding seqLengthEncoding,
                               DicomWriter.LengthEncoding itemLengthEncoding,
                               byte[] expected)
            throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DicomWriter w = new DicomWriter(bout)
                .withEncoding(encoding)
                .withIncludeGroupLength(includeGroupLength)
                .withSequenceLengthEncoding(seqLengthEncoding)
                .withItemLengthEncoding(itemLengthEncoding)
        ) {
            w.writeDataSet(sequences());
        }
        assertArrayEquals(expected, bout.toByteArray());
    }
}
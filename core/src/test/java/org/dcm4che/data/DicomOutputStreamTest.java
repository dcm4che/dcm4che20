package org.dcm4che.data;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class DicomOutputStreamTest {
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
        try (DicomOutputStream dos = new DicomOutputStream(bout)) {
            dos.writeFileMetaInformation(fmi).writeDataSet(new DicomObject());
        }
        assertArrayEquals(resourceAsBytes("preamble_fmi_defl.dcm"), bout.toByteArray());
    }

    @Test
    void writeCommandSet() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(bout)) {
            dos.writeCommandSet(c_echo_rq());
        }
        assertArrayEquals(C_ECHO_RQ, bout.toByteArray());
    }

    @Test
    void writeSequenceIVR_LE() throws IOException {
        assertArrayEquals(IVR_LE,
            writeDataset(DicomEncoding.IVR_LE, false,
                    DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO,
                    DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO,
                    sequences()));
    }

    @Test
    void writeSequenceEVR_BE_GROUP() throws IOException {
        assertArrayEquals(EVR_BE_GROUP,
            writeDataset(DicomEncoding.EVR_BE, true,
                    DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO,
                    DicomOutputStream.LengthEncoding.UNDEFINED_OR_ZERO,
                    sequences()));
    }

    @Test
    void writeSequenceExplicitItemLength() throws IOException {
        assertArrayEquals(EXPL_ITEM_LEN,
            writeDataset(DicomEncoding.IVR_LE, true,
                    DicomOutputStream.LengthEncoding.UNDEFINED,
                    DicomOutputStream.LengthEncoding.EXPLICIT,
                    sequences()));
    }

    @Test
    void writeSequenceExplicitSequenceLength() throws IOException {
        assertArrayEquals(EXPL_SEQ_LEN,
            writeDataset(DicomEncoding.IVR_LE, true,
                    DicomOutputStream.LengthEncoding.EXPLICIT,
                    DicomOutputStream.LengthEncoding.UNDEFINED,
                    sequences()));
    }

    @Test
    void withBulkDataURI() throws IOException {
        String baseURL = DicomInputStreamTest.resource("waveform_overlay_pixeldata.dcm").toString();
        DicomObject data = new DicomObject();
        DicomSequence seq = data.newDicomSequence(Tag.WaveformSequence);
        DicomObject item = new DicomObject();
        seq.addItem(item);
        item.setBulkData(Tag.WaveformData, VR.OW, baseURL + "#offset=32&length=256", null);
        data.setBulkData(Tag.OverlayData, VR.OW, baseURL + "#offset=300&length=256", null);
        data.setBulkData(Tag.PixelData, VR.OB, baseURL + "#offset=568", null);
        data.setNull(Tag.DataSetTrailingPadding, VR.OB);
        assertArrayEquals(resourceAsBytes("waveform_overlay_pixeldata.dcm"),
                writeDataset(DicomEncoding.EVR_LE, false,
                    DicomOutputStream.LengthEncoding.EXPLICIT,
                    DicomOutputStream.LengthEncoding.EXPLICIT,
                    data));
    }

    private byte[] writeDataset(DicomEncoding encoding, boolean includeGroupLength,
                              DicomOutputStream.LengthEncoding seqLengthEncoding,
                              DicomOutputStream.LengthEncoding itemLengthEncoding,
                              DicomObject dataset)
            throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(bout)
                .withEncoding(encoding)
                .withIncludeGroupLength(includeGroupLength)
                .withSequenceLengthEncoding(seqLengthEncoding)
                .withItemLengthEncoding(itemLengthEncoding)
        ) {
            dos.writeDataSet(dataset);
        }
        return bout.toByteArray();
    }

    static byte[] resourceAsBytes(String name) throws IOException {
        URL url = DicomInputStreamTest.resource(name);
        byte[] b = new byte[(int) Files.size(Paths.get(URI.create(url.toString())))];
        try (InputStream in = url.openStream()) {
            in.read(b);
        }
        return b;
    }
}
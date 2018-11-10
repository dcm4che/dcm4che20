package org.dcm4che.io;

import org.dcm4che.data.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class DicomReaderTest {

    private static final byte[] IVR_LE = {0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] EVR_LE = {8, 0, 0, 0, 'U', 'L', 4, 0, 0, 0, 0, 0};
    private static final byte[] EVR_BE = {0, 8, 0, 0, 'U', 'L', 0, 4, 0, 0, 0, 0};
    private static final byte[] WF_SEQ_EVR_LE = {
            0, 84, 0, 1, 'S', 'Q', 0, 0, 8, 0, 0, 0,
            -2, -1, 0, -32, 0, 0, 0, 0
    };
    private static final byte[] WF_SEQ_IVR_LE = {
            0, 84, 0, 1, -1, -1, -1, -1,
            -2, -1, 0, -32, -1, -1, -1, -1,
            -2, -1, 13, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };
    private static final byte[] UN_SEQ_IVR_LE = {
            9, 0, 0, 16, 'U', 'N', 0, 0, -1, -1, -1, -1,
            -2, -1, 0, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };
    private static final byte[] UN_SEQ_EVR_LE = {
            9, 0, 0, 16, 'U', 'N', 0, 0, -1, -1, -1, -1,
            -2, -1, 0, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };
    private static final byte[] C_ECHO_RQ = {
            0, 0, 0, 0, 4, 0, 0, 0, 56, 0, 0, 0,
            0, 0, 2, 0, 18, 0, 0, 0,
            49, 46, 50, 46, 56, 52, 48, 46, 49, 48, 48, 48, 56, 46, 49, 46, 49, 0,
            0, 0, 0, 1, 2, 0, 0, 0, 48, 0,
            0, 0, 16, 1, 2, 0, 0, 0, 1, 0,
            0, 0, 0, 8, 2, 0, 0, 0, 1, 1
    };
    private static final byte[] PER_FRAME_FUNCTIONAL_GROUPS_SEQ_IVR_LE = {
            0, 82, 48, -110, 40, 0, 0, 0,
            -2, -1, 0, -32, 32, 0, 0, 0,
            24, 0, 20, -111, 24, 0, 0, 0,
            -2, -1, 0, -32, 16, 0, 0, 0,
            24, 0, -126, -112, 8, 0, 0, 0, -1, -1, -1, -1, 102, 102, -10, 63
    };
    private static final byte[] PER_FRAME_FUNCTIONAL_GROUPS_SEQ_EVR_LE = {
            0, 82, 48, -110, 83, 81, 0, 0, -1, -1, -1, -1,
            -2, -1, 0, -32, -1, -1, -1, -1,
            24, 0, 20, -111, 83, 81, 0, 0, -1, -1, -1, -1,
            -2, -1, 0, -32, -1, -1, -1, -1,
            24, 0, -126, -112, 70, 68, 8, 0, -1, -1, -1, -1, 102, 102, -10, 63,
            -2, -1, 13, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0,
            -2, -1, 13, -32, 0, 0, 0, 0,
            -2, -1, -35, -32, 0, 0, 0, 0
    };

    @Test
    void readDataSetIVR_LE() throws IOException {
        assertEquals(DicomEncoding.IVR_LE, readDataSet(IVR_LE));
    }

    @Test
    void readDataSetEVR_LE() throws IOException {
        assertEquals(DicomEncoding.EVR_LE, readDataSet(EVR_LE));
    }

    @Test
    void readDataSetEVR_BE() throws IOException {
        assertEquals(DicomEncoding.EVR_BE, readDataSet(EVR_BE));
    }

    @Test
    void readDataSetDEFL() throws IOException {
        assertEquals(DicomEncoding.DEFL_EVR_LE, readDataSet("preamble_fmi_defl.dcm"));
    }

    @Test
    void readCommandSet() throws IOException {
        DicomObject cmd;
        try (InputStream in = new ByteArrayInputStream(C_ECHO_RQ)) {
            cmd = new DicomReader(in).readCommandSet();
        }
        assertNotNull(cmd);
        assertEquals(48, cmd.getInt(Tag.CommandField, -1));
        assertEquals(UID.VerificationSOPClass, cmd.getString(Tag.AffectedSOPClassUID));
    }

    @Test
    void parseSequenceEVR_LE() throws IOException {
        parseSequence(WF_SEQ_EVR_LE, DicomEncoding.EVR_LE, Tag.WaveformSequence);
    }

    @Test
    void parseSequenceIVR_LE() throws IOException {
        parseSequence(WF_SEQ_IVR_LE, DicomEncoding.IVR_LE, Tag.WaveformSequence);
    }

    @Test
    void parseSequenceUN_IVR_LE() throws IOException {
        parseSequence(UN_SEQ_IVR_LE, DicomEncoding.EVR_LE, 0x00091000);
    }

    @Test
    void parseSequenceUN_EVR_LE() throws IOException {
        parseSequence(UN_SEQ_EVR_LE, DicomEncoding.EVR_LE, 0x00091000);
    }

    @Test
    void parsePerFrameFunctionalGroupsSequenceLazyIVR_LE() throws IOException {
        parsePerFrameFunctionalGroupsSequenceLazy(PER_FRAME_FUNCTIONAL_GROUPS_SEQ_IVR_LE, DicomEncoding.IVR_LE);
    }

    @Test
    void parsePerFrameFunctionalGroupsSequenceLazyEVR_LE() throws IOException {
        parsePerFrameFunctionalGroupsSequenceLazy(PER_FRAME_FUNCTIONAL_GROUPS_SEQ_EVR_LE, DicomEncoding.EVR_LE);
    }

    @Test
    void parseDataFragments() throws IOException {
        DicomElement el = parse(resourceAsStream("pixeldata.dcm"), DicomEncoding.EVR_LE).get(Tag.PixelData);
        assertTrue(el instanceof DataFragments);
        assertEquals(VR.OB, el.vr());
        DataFragment dataFragment = ((DataFragments) el).getDataFragment(1);
        assertNotNull(dataFragment);
        assertEquals(256, dataFragment.valueLength());
    }

    @Test
    void withoutBulkData() throws IOException {
        DicomObject data = parseWithoutBulkData();
        DicomElement seq = data.get(Tag.WaveformSequence);
        assertTrue(seq instanceof DicomSequence);
        DicomObject item = ((DicomSequence) seq).getItem(0);
        assertNotNull(item);
        assertNull(item.get(Tag.WaveformData));
        assertNull(data.get(Tag.OverlayData));
        assertNull(data.get(Tag.PixelData));
        assertNotNull(data.get(Tag.DataSetTrailingPadding));
    }

    @Test
    void withBulkDataURI() throws IOException {
        URL srcURL = resource("waveform_overlay_pixeldata.dcm");
        Path sourcePath = Paths.get(URI.create(srcURL.toString()));
        DicomObject data = parseWithBulkDataURI(sourcePath);
        DicomElement seq = data.get(Tag.WaveformSequence);
        assertTrue(seq instanceof DicomSequence);
        DicomObject item = ((DicomSequence) seq).getItem(0);
        assertNotNull(item);
        DicomElement waveformData = item.get(Tag.WaveformData);
        assertTrue(waveformData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) waveformData).bulkDataURI()
                .endsWith("waveform_overlay_pixeldata.dcm#offset=32&length=256"));
        DicomElement overlayData = data.get(Tag.OverlayData);
        assertTrue(overlayData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) overlayData).bulkDataURI()
                .endsWith("waveform_overlay_pixeldata.dcm#offset=300&length=256"));
        DicomElement pixelData = data.get(Tag.PixelData);
        assertTrue(pixelData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) pixelData).bulkDataURI()
                .endsWith("waveform_overlay_pixeldata.dcm#offset=568"));
        assertNotNull(data.get(Tag.DataSetTrailingPadding));
    }

    @Test
    void spoolBulkData() throws IOException {
        Path spoolPath = Paths.get("target/bulkdata.blk");
        Files.deleteIfExists(spoolPath);
        DicomObject data = parseSpoolBulkData(spoolPath);
        DicomElement seg = data.get(Tag.WaveformSequence);
        assertTrue(seg instanceof DicomSequence);
        DicomObject item = ((DicomSequence) seg).getItem(0);
        assertNotNull(item);
        DicomElement waveformData = item.get(Tag.WaveformData);
        assertTrue(waveformData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) waveformData).bulkDataURI()
                .endsWith("target/bulkdata.blk#length=256"));
        DicomElement overlayData = data.get(Tag.OverlayData);
        assertTrue(overlayData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) overlayData).bulkDataURI()
                .endsWith("target/bulkdata.blk#offset=256&length=256"));
        DicomElement pixelData = data.get(Tag.PixelData);
        assertTrue(pixelData instanceof BulkDataElement);
        assertTrue(((BulkDataElement) pixelData).bulkDataURI()
                .endsWith("target/bulkdata.blk#offset=512"));
        assertNotNull(data.get(Tag.DataSetTrailingPadding));
        assertEquals(792, Files.size(spoolPath));
    }

    static DicomEncoding readDataSet(String name) throws IOException {
        try (InputStream in = resourceAsStream(name)) {
            DicomReader reader = new DicomReader(in);
            reader.readDataSet();
            return reader.getEncoding();
        }
    }

    static DicomEncoding readDataSet(byte[] b) throws IOException {
        try (InputStream in = new ByteArrayInputStream(b)) {
            DicomReader reader = new DicomReader(in);
            reader.readDataSet();
            return reader.getEncoding();
        }
    }

    static DicomObject parse(InputStream in, DicomEncoding encoding) throws IOException {
        try (DicomReader reader = new DicomReader(in).withEncoding(encoding)) {
            return reader.readDataSet();
        }
    }

    static DicomObject parseLazy(byte[] b, DicomEncoding encoding, int seqTag) throws IOException {
        try (DicomReader reader = new DicomReader(new ByteArrayInputStream(b))
                .withEncoding(encoding)
                .withParseItemsLazy(seqTag)) {
            return reader.readDataSet();
        }
    }

    static void parseSequence(byte[] b, DicomEncoding encoding, int tag) throws IOException {
        DicomElement el = parse(new ByteArrayInputStream(b), encoding).get(tag);
        assertTrue(el instanceof DicomSequence);
        assertEquals(VR.SQ, el.vr());
        DicomObject item = ((DicomSequence) el).getItem(0);
        assertNotNull(item);
    }

    static void parsePerFrameFunctionalGroupsSequenceLazy(byte[] b, DicomEncoding encoding) throws IOException {
        DicomElement functionalGroupSeq = parseLazy(b, encoding, Tag.PerFrameFunctionalGroupsSequence)
                .get(Tag.PerFrameFunctionalGroupsSequence);
        assertTrue(functionalGroupSeq instanceof DicomSequence);
        DicomObject functionalGroup = ((DicomSequence) functionalGroupSeq).getItem(0);
        assertNotNull(functionalGroup);
        DicomElement mrEchoSeq = functionalGroup.get(Tag.MREchoSequence);
        assertTrue(mrEchoSeq instanceof DicomSequence);
        DicomObject mrEcho = ((DicomSequence) mrEchoSeq).getItem(0);
        assertNotNull(mrEcho);
        assertEquals(1.4000005722045896, mrEcho.getDouble(Tag.EffectiveEchoTime, 0.0));
    }

    static DicomObject parseWithoutBulkData() throws IOException {
        try (DicomReader reader = new DicomReader(resourceAsStream("waveform_overlay_pixeldata.dcm"))
                .withEncoding(DicomEncoding.EVR_LE)
                .withBulkData(DicomReader::isBulkData)) {
            return reader.readDataSet();
        }
    }

    static DicomObject parseWithBulkDataURI(Path sourcePath) throws IOException {
        try (DicomReader reader = new DicomReader(Files.newInputStream(sourcePath))
                .withEncoding(DicomEncoding.EVR_LE)
                .withBulkData(DicomReader::isBulkData)
                .withBulkDataURI(sourcePath)) {
            return reader.readDataSet();
        }
    }

    static DicomObject parseSpoolBulkData(Path spoolPath) throws IOException {
        try (DicomReader reader = new DicomReader(resourceAsStream("waveform_overlay_pixeldata.dcm"))
                .withEncoding(DicomEncoding.EVR_LE)
                .withBulkData(DicomReader::isBulkData)
                .spoolBulkData(() -> spoolPath)) {
            return reader.readDataSet();
        }
    }

    static URL resource(String name) {
        return Thread.currentThread().getContextClassLoader().getResource(name);
    }

    static InputStream resourceAsStream(String name) throws IOException {
        return resource(name).openStream();
    }
}
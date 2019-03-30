package org.dcm4che.data;

import org.dcm4che.util.OptionalFloat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class VRTest {
    private static final int[] BYTES = {1, 127, -128, -1};
    private static final int[] INTS = {1, 32767, -32768, -1};
    private static final int[] UINTS = {1, 32767, 32768, 65535};
    private static final float[] FLOATS = {Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MIN_VALUE};
    private static final String[] BYTE_STRS = {"1", "127", "-128", "-1"};
    private static final String[] INT_STRS = {"1", "32767", "-32768", "-1"};
    private static final String[] UINT_STRS = {"1", "32767", "32768", "65535"};
    private static final String[] TAG_STRS = {"00000001", "00007FFF", "FFFF8000", "FFFFFFFF"};
    private static final String[] FLOAT_STRS = {"1.4E-45", "3.4028235E38", "-3.4028235E38", "-1.4E-45"};
    private static final String[] DOUBLE_STRS = {"1.401298464324817E-45", "3.4028234663852886E38",
            "-3.4028234663852886E38", "-1.401298464324817E-45"};
    private static final byte[] AT_LE = {114, 0, 96, 0, 16, 0, 0, 0,
            0, 0, 1, 0, 0, 0, -1, 127, -1, -1, 0, -128, -1, -1, -1, -1};
    private static final byte[] AT_BE = {0, 114, 0, 96, 'A', 'T', 0, 16,
            0, 0, 0, 1, 0, 0, 127, -1, -1, -1, -128, 0, -1, -1, -1, -1};
    private static final byte[] FD_LE = {114, 0, 116, 0, 32, 0, 0, 0,
            0, 0, 0, 0, 0, 0, -16, 63, 0, 0, 0, 0, -64, -1, -33, 64,
            0, 0, 0, 0, 0, 0, -32, -64, 0, 0, 0, 0, 0, 0, -16, -65};
    private static final byte[] FD_BE = {0, 114, 0, 116, 'F', 'D', 0, 32,
            63, -16, 0, 0, 0, 0, 0, 0, 64, -33, -1, -64, 0, 0, 0, 0,
            -64, -32, 0, 0, 0, 0, 0, 0, -65, -16, 0, 0, 0, 0, 0, 0};
    private static final byte[] FD_LE2 = {114, 0, 116, 0, 32, 0, 0, 0,
            0, 0, 0, 0, 0, 0, -96, 54, 0, 0, 0, -32, -1, -1, -17, 71,
            0, 0, 0, -32, -1, -1, -17, -57, 0, 0, 0, 0, 0, 0, -96, -74};
    private static final byte[] FD_BE2 = {0, 114, 0, 116, 'F', 'D', 0, 32,
            54, -96, 0, 0, 0, 0, 0, 0, 71, -17, -1, -1, -32, 0, 0, 0,
            -57, -17, -1, -1, -32, 0, 0, 0, -74, -96, 0, 0, 0, 0, 0, 0};
    private static final byte[] FL_LE = {114, 0, 118, 0, 16, 0, 0, 0,
            0, 0, -128, 63, 0, -2, -1, 70, 0, 0, 0, -57, 0, 0, -128, -65};
    private static final byte[] FL_BE = {0, 114, 0, 118, 'F', 'L', 0, 16,
            63, -128, 0, 0, 70, -1, -2, 0, -57, 0, 0, 0, -65, -128, 0, 0};
    private static final byte[] FL_LE2 = {114, 0, 118, 0, 16, 0, 0, 0,
            1, 0, 0, 0, -1, -1, 127, 127, -1, -1, 127, -1, 1, 0, 0, -128};
    private static final byte[] FL_BE2 = {0, 114, 0, 118, 'F', 'L', 0, 16,
            0, 0, 0, 1, 127, 127, -1, -1, -1, 127, -1, -1, -128, 0, 0, 1};
    private static final byte[] OB_LE = {114, 0, 101, 0, 4, 0, 0, 0, 1, 127, -128, -1};
    private static final byte[] OB_BE = {0, 114, 0, 101, 'O', 'B', 0, 0, 0, 0, 0, 4, 1, 127, -128, -1};
    private static final byte[] OD_LE = {114, 0, 115, 0, 32, 0, 0, 0,
            0, 0, 0, 0, 0, 0, -16, 63, 0, 0, 0, 0, -64, -1, -33, 64,
            0, 0, 0, 0, 0, 0, -32, -64, 0, 0, 0, 0, 0, 0, -16, -65};
    private static final byte[] OD_BE = {0, 114, 0, 115, 'O', 'D', 0, 0, 0, 0, 0, 32,
            63, -16, 0, 0, 0, 0, 0, 0, 64, -33, -1, -64, 0, 0, 0, 0,
            -64, -32, 0, 0, 0, 0, 0, 0, -65, -16, 0, 0, 0, 0, 0, 0};
    private static final byte[] OD_LE2 = {114, 0, 115, 0, 32, 0, 0, 0,
            0, 0, 0, 0, 0, 0, -96, 54, 0, 0, 0, -32, -1, -1, -17, 71,
            0, 0, 0, -32, -1, -1, -17, -57, 0, 0, 0, 0, 0, 0, -96, -74};
    private static final byte[] OD_BE2 = {0, 114, 0, 115, 'O', 'D', 0, 0, 0, 0, 0, 32,
            54, -96, 0, 0, 0, 0, 0, 0, 71, -17, -1, -1, -32, 0, 0, 0,
            -57, -17, -1, -1, -32, 0, 0, 0, -74, -96, 0, 0, 0, 0, 0, 0};
    private static final byte[] OF_LE = {114, 0, 103, 0, 16, 0, 0, 0,
            0, 0, -128, 63, 0, -2, -1, 70, 0, 0, 0, -57, 0, 0, -128, -65};
    private static final byte[] OF_BE = {0, 114, 0, 103, 'O', 'F', 0, 0, 0, 0, 0, 16,
            63, -128, 0, 0, 70, -1, -2, 0, -57, 0, 0, 0, -65, -128, 0, 0};
    private static final byte[] OF_LE2 = {114, 0, 103, 0, 16, 0, 0, 0,
            1, 0, 0, 0, -1, -1, 127, 127, -1, -1, 127, -1, 1, 0, 0, -128};
    private static final byte[] OF_BE2 = {0, 114, 0, 103, 'O', 'F', 0, 0, 0, 0, 0, 16,
            0, 0, 0, 1, 127, 127, -1, -1, -1, 127, -1, -1, -128, 0, 0, 1};
    private static final byte[] OL_LE = {114, 0, 117, 0, 16, 0, 0, 0,
            1, 0, 0, 0, -1, 127, 0, 0, 0, -128, -1, -1, -1, -1, -1, -1};
    private static final byte[] OL_BE = {0, 114, 0, 117, 'O', 'L', 0, 0, 0, 0, 0, 16,
            0, 0, 0, 1, 0, 0, 127, -1, -1, -1, -128, 0, -1, -1, -1, -1};
    private static final byte[] OW_LE = {114, 0, 105, 0, 8, 0, 0, 0, 1, 0, -1, 127, 0, -128, -1, -1};
    private static final byte[] OW_BE = {0, 114, 0, 105, 'O', 'W', 0, 0, 0, 0, 0, 8, 0, 1, 127, -1, -128, 0, -1, -1};
    private static final byte[] SL_LE = {114, 0, 124, 0, 16, 0, 0, 0,
            1, 0, 0, 0, -1, 127, 0, 0, 0, -128, -1, -1, -1, -1, -1, -1};
    private static final byte[] SL_BE = {0, 114, 0, 124, 'S', 'L', 0, 16,
            0, 0, 0, 1, 0, 0, 127, -1, -1, -1, -128, 0, -1, -1, -1, -1};
    private static final byte[] SS_LE = {114, 0, 126, 0, 8, 0, 0, 0, 1, 0, -1, 127, 0, -128, -1, -1};
    private static final byte[] SS_BE = {0, 114, 0, 126, 'S', 'S', 0, 8, 0, 1, 127, -1, -128, 0, -1, -1};
    private static final byte[] UL_LE = {114, 0, 120, 0, 16, 0, 0, 0,
            1, 0, 0, 0, -1, 127, 0, 0, 0, -128, 0, 0, -1, -1, 0, 0};
    private static final byte[] UL_BE = {0, 114, 0, 120, 'U', 'L', 0, 16,
            0, 0, 0, 1, 0, 0, 127, -1, 0, 0, -128, 0, 0, 0, -1, -1};
    private static final byte[] UN_LE = {114, 0, 109, 0, 4, 0, 0, 0, 1, 127, -128, -1};
    private static final byte[] UN_BE = {0, 114, 0, 109, 'U', 'N', 0, 0, 0, 0, 0, 4, 1, 127, -128, -1};
    private static final byte[] US_LE = {114, 0, 122, 0, 8, 0, 0, 0, 1, 0, -1, 127, 0, -128, -1, -1};
    private static final byte[] US_BE = {0, 114, 0, 122, 'U', 'S', 0, 8, 0, 1, 127, -1, -128, 0, -1, -1};

    @Test
    void AT() throws IOException {
        testBinaryVR(Tag.SelectorATValue, VR.AT, TAG_STRS, INTS, AT_LE, AT_BE);
    }

    @Test
    void FD() throws IOException {
        testBinaryVR(Tag.SelectorFDValue, VR.FD, DOUBLE_STRS, INTS, FD_LE, FD_BE, FLOATS, FD_LE2, FD_BE2);
    }

    @Test
    void FL() throws IOException {
        testBinaryVR(Tag.SelectorFLValue, VR.FL, FLOAT_STRS, INTS, FL_LE, FL_BE, FLOATS, FL_LE2, FL_BE2);
    }

    @Test
    void OB() throws IOException {
        testBinaryVR(Tag.SelectorOBValue, VR.OB, BYTE_STRS, BYTES, OB_LE, OB_BE);
    }

    @Test
    void OD() throws IOException {
        testBinaryVR(Tag.SelectorODValue, VR.OD, DOUBLE_STRS, INTS, OD_LE, OD_BE, FLOATS, OD_LE2, OD_BE2);
    }

    @Test
    void OF() throws IOException {
        testBinaryVR(Tag.SelectorOFValue, VR.OF, FLOAT_STRS, INTS, OF_LE, OF_BE, FLOATS, OF_LE2, OF_BE2);
    }

    @Test
    void OL() throws IOException {
        testBinaryVR(Tag.SelectorOLValue, VR.OL, INT_STRS, INTS, OL_LE, OL_BE);
    }

    @Test
    void OW() throws IOException {
        testBinaryVR(Tag.SelectorOWValue, VR.OW, INT_STRS, INTS, OW_LE, OW_BE);
    }

    @Test
    void SL() throws IOException {
        testBinaryVR(Tag.SelectorSLValue, VR.SL, INT_STRS, INTS, SL_LE, SL_BE);
    }

    @Test
    void SS() throws IOException {
        testBinaryVR(Tag.SelectorSSValue, VR.SS, INT_STRS, INTS, SS_LE, SS_BE);
    }

    @Test
    void UL() throws IOException {
        testBinaryVR(Tag.SelectorULValue, VR.UL, UINT_STRS, UINTS, UL_LE, UL_BE);
    }

    @Test
    void UN() throws IOException {
        testBinaryVR(Tag.SelectorUNValue, VR.UN, BYTE_STRS, BYTES, UN_LE, UN_BE);
    }

    @Test
    void US() throws IOException {
        testBinaryVR(Tag.SelectorUSValue, VR.US, UINT_STRS, UINTS, US_LE, US_BE);
    }

    private static void testBinaryVR(int tag, VR vr, String[] strs, int[] vals, byte[] bLE, byte[] bBE)
            throws IOException {
        testBinaryVR(tag, vr, strs, vals, bLE, bBE, toFloats(vals), bLE, bBE);
    }

    private static void testBinaryVR(int tag, VR vr, String[] strs, int[] vals, byte[] bLE, byte[] bBE,
                                     float[] floats, byte[] bLE2, byte[] bBE2)
            throws IOException {
        double[] doubles = toDoubles(floats);
        DicomObject dcmObj = new DicomObject();
        dcmObj.setInt(tag, vr, vals);
        assertArrayEquals(vals, dcmObj.getInts(tag));
        assertEquals(OptionalInt.of(vals[2]), dcmObj.getInt(tag, 2));
        assertArrayEquals(bLE, toBytes(dcmObj, DicomEncoding.IVR_LE));
        dcmObj.setFloat(tag, vr, floats);
        assertArrayEquals(floats, dcmObj.getFloats(tag));
        assertEquals(OptionalFloat.of(floats[2]), dcmObj.getFloat(tag, 2));
        assertArrayEquals(bLE2, toBytes(dcmObj, DicomEncoding.IVR_LE));
        dcmObj.setDouble(tag, vr, doubles);
        assertArrayEquals(doubles, dcmObj.getDoubles(tag));
        assertEquals(OptionalDouble.of(doubles[2]), dcmObj.getDouble(tag, 2));
        assertArrayEquals(bLE2, toBytes(dcmObj, DicomEncoding.IVR_LE));
        dcmObj.setString(tag, vr, strs);
        assertArrayEquals(strs, dcmObj.getStrings(tag));
        assertEquals(Optional.of(strs[2]), dcmObj.getString(tag, 2));
        assertArrayEquals(bLE2, toBytes(dcmObj, DicomEncoding.IVR_LE));
        assertArrayEquals(bBE2, toBytes(dcmObj, DicomEncoding.EVR_BE));
        dcmObj = parseDicomObject(bLE, DicomEncoding.IVR_LE);
        assertArrayEquals(bLE, toBytes(dcmObj, DicomEncoding.IVR_LE));
        assertArrayEquals(bBE, toBytes(dcmObj, DicomEncoding.EVR_BE));
        assertArrayEquals(vals, dcmObj.getInts(tag));
        assertEquals(OptionalInt.of(vals[2]), dcmObj.getInt(tag, 2));
        if (bLE2 != bLE) {
            dcmObj = parseDicomObject(bLE2, DicomEncoding.IVR_LE);
        }
        assertArrayEquals(floats, dcmObj.getFloats(tag));
        assertEquals(OptionalFloat.of(floats[2]), dcmObj.getFloat(tag, 2));
        assertArrayEquals(doubles, dcmObj.getDoubles(tag));
        assertEquals(OptionalDouble.of(doubles[2]), dcmObj.getDouble(tag, 2));
        assertArrayEquals(strs, dcmObj.getStrings(tag));
        assertEquals(Optional.of(strs[2]), dcmObj.getString(tag, 2));
        dcmObj = parseDicomObject(bBE, DicomEncoding.EVR_BE);
        assertArrayEquals(bLE, toBytes(dcmObj, DicomEncoding.IVR_LE));
        assertArrayEquals(bBE, toBytes(dcmObj, DicomEncoding.EVR_BE));
        assertArrayEquals(vals, dcmObj.getInts(tag));
        assertEquals(OptionalInt.of(vals[2]), dcmObj.getInt(tag, 2));
        if (bBE2 != bBE) {
            dcmObj = parseDicomObject(bBE2, DicomEncoding.EVR_BE);
        }
        assertArrayEquals(floats, dcmObj.getFloats(tag));
        assertEquals(OptionalFloat.of(floats[2]), dcmObj.getFloat(tag, 2));
        assertArrayEquals(doubles, dcmObj.getDoubles(tag));
        assertEquals(OptionalDouble.of(doubles[2]), dcmObj.getDouble(tag, 2));
        assertArrayEquals(strs, dcmObj.getStrings(tag));
        assertEquals(Optional.of(strs[2]), dcmObj.getString(tag, 2));
    }

    private static float[] toFloats(int[] vals) {
        float[] floats = new float[vals.length];
        for (int i = 0; i < vals.length; i++) {
            floats[i] = vals[i];
        }
        return floats;
    }

    private static double[] toDoubles(float[] vals) {
        double[] doubles = new double[vals.length];
        for (int i = 0; i < vals.length; i++) {
            doubles[i] = vals[i];
        }
        return doubles;
    }

    private static byte[] toBytes(DicomObject dcmObj, DicomEncoding encoding) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DicomOutputStream(out).withEncoding(encoding).writeDataSet(dcmObj);
        return out.toByteArray();
    }

    private static DicomObject parseDicomObject(byte[] b, DicomEncoding encoding) throws IOException {
        return new DicomInputStream(new ByteArrayInputStream(b)).withEncoding(encoding).readDataSet();
    }
}
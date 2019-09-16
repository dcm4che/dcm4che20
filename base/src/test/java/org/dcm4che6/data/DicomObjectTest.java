package org.dcm4che6.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Oct 2018
 */
class DicomObjectTest {

    private static final String PRIVATE_CREATOR_A = "PRIVATE CREATOR A";
    private static final String PRIVATE_CREATOR_B = "PRIVATE CREATOR B";
    private static final String BULK_DATA_URI = "http://BulkData/URI";

    @Test
    void getPrivate() {
        DicomObject dataset = DicomObject.newDicomObject();
        dataset.setString(0x00090010, VR.LO, PRIVATE_CREATOR_A);
        dataset.setString(0x00090011, VR.LO, PRIVATE_CREATOR_B);
        dataset.setString(0x00091010, VR.SH, "A");
        dataset.setInt(0x00091020, VR.US, 0xA);
        dataset.setFloat(0x00091030, VR.FL, 0.1111f);
        dataset.setDouble(0x00091040, VR.FD, 0.11111111);
        dataset.setString(0x00091110, VR.SH, "B");
        dataset.setInt(0x00091120, VR.US, 0XB);
        dataset.setFloat(0x00091130, VR.FL, 0.2222f);
        dataset.setDouble(0x00091140, VR.FD, 0.22222222);
        assertEquals(0.22222222, dataset.getDouble(PRIVATE_CREATOR_B, 0x00090040).orElseGet(Assertions::fail));
        assertEquals(0.2222f, dataset.getFloat(PRIVATE_CREATOR_B, 0x00090030).orElseGet(Assertions::fail));
        assertEquals(0XB, dataset.getInt(PRIVATE_CREATOR_B, 0x00090020).orElseGet(Assertions::fail));
        assertEquals("B", dataset.getString(PRIVATE_CREATOR_B, 0x00090010).orElseGet(Assertions::fail));
        assertEquals(0.11111111, dataset.getDouble(PRIVATE_CREATOR_A, 0x00090040).orElseGet(Assertions::fail));
        assertEquals(0.1111f, dataset.getFloat(PRIVATE_CREATOR_A, 0x00090030).orElseGet(Assertions::fail));
        assertEquals(0XA, dataset.getInt(PRIVATE_CREATOR_A, 0x00090020).orElseGet(Assertions::fail));
        assertEquals("A", dataset.getString(PRIVATE_CREATOR_A, 0x00090010).orElseGet(Assertions::fail));
    }

    @Test
    void setPrivate() {
        DicomObject dataset = DicomObject.newDicomObject();
        dataset.setString(PRIVATE_CREATOR_A, 0x00090010, VR.SH, "A");
        dataset.setInt(PRIVATE_CREATOR_A, 0x00090020, VR.US, 0XA);
        dataset.setFloat(PRIVATE_CREATOR_A, 0x00090030, VR.FL, 0.1111f);
        dataset.setDouble(PRIVATE_CREATOR_A, 0x00090040, VR.FD, 0.11111111);
        dataset.setString(PRIVATE_CREATOR_B, 0x00090010, VR.SH, "B");
        dataset.setInt(PRIVATE_CREATOR_B, 0x00090020, VR.US, 0XB);
        dataset.setFloat(PRIVATE_CREATOR_B, 0x00090030, VR.FL, 0.2222f);
        dataset.setDouble(PRIVATE_CREATOR_B, 0x00090040, VR.FD, 0.22222222);
        assertEquals(PRIVATE_CREATOR_A, dataset.getString(0x00090010).orElseGet(Assertions::fail));
        assertEquals(PRIVATE_CREATOR_B, dataset.getString(0x00090011).orElseGet(Assertions::fail));
        assertEquals("A", dataset.getString(0x00091010).orElseGet(Assertions::fail));
        assertEquals(0xA, dataset.getInt(0x00091020).orElseGet(Assertions::fail));
        assertEquals(0.1111f, dataset.getFloat(0x00091030).orElseGet(Assertions::fail));
        assertEquals(0.11111111, dataset.getDouble(0x00091040).orElseGet(Assertions::fail));
        assertEquals("B", dataset.getString(0x00091110).orElseGet(Assertions::fail));
        assertEquals(0xB, dataset.getInt(0x00091120).orElseGet(Assertions::fail));
        assertEquals(0.2222f, dataset.getFloat(0x00091130).orElseGet(Assertions::fail));
        assertEquals(0.22222222, dataset.getDouble(0x00091140).orElseGet(Assertions::fail));
    }

    @Test
    void serializeBulkData() {
        DicomObject data = DicomObject.newDicomObject();
        DicomElement seq = data.newDicomSequence(Tag.WaveformSequence);
        DicomObject item = DicomObject.newDicomObject();
        seq.addItem(item);
        item.setBulkData(Tag.WaveformData, VR.OW, BULK_DATA_URI, null);
        data = deserialize(serialize(data));
        DicomElement elm = data.get(Tag.WaveformSequence).orElseGet(Assertions::fail);
        item = elm.getItem(0);
        assertNotNull(item);
        DicomElement waveformData = item.get(Tag.WaveformData).orElseGet(Assertions::fail);
        assertEquals(BULK_DATA_URI, waveformData.bulkDataURI());
    }

    private static byte[] serialize(DicomObject dcmobj) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)){
            oos.writeObject(dcmobj);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static DicomObject deserialize(byte[] b) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b))) {
            return (DicomObject) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
package org.dcm4che.internal;

import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.data.VR;
import org.dcm4che.internal.MemoryCache;
import org.dcm4che.internal.ToggleByteOrder;
import org.dcm4che.io.ByteOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class MemoryCacheTest {

    private static final byte[] BYTES = {-1, 1, -2, 2, -3, 3, -4, 4, -5, 5};
    private static final byte[] SWAPPED = {1, -1, 2, -2, 3, -3, 4, -4, 5, -5};
    private static final int POS_BYTES = 255;
    private static final int POS_PN = 1023;

    InputStream createTestInputStream() throws IOException {
        byte[] buf = new byte[POS_PN + 2];
        System.arraycopy(BYTES, 0, buf, 0, BYTES.length);
        System.arraycopy(BYTES, 0, buf, POS_BYTES, BYTES.length);
        buf[POS_PN] = 'P';
        buf[POS_PN + 1] = 'N';
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(out);
        deflater.write(buf);
        deflater.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Test
    void test() throws IOException {
        MemoryCache cache = new MemoryCache();
        InputStream in = cache.inflate(0, createTestInputStream());
        assertEquals(POS_BYTES + BYTES.length, cache.loadFromStream(POS_BYTES + BYTES.length, in));
        assertEquals((short) 0x01ff, cache.shortAt(0, ByteOrder.LITTLE_ENDIAN));
        assertEquals((short) 0xff01, cache.shortAt(POS_BYTES, ByteOrder.BIG_ENDIAN));
        assertEquals(0xff01, cache.ushortAt(0, ByteOrder.BIG_ENDIAN));
        assertEquals(0x02fe01ff, cache.intAt(0, ByteOrder.LITTLE_ENDIAN));
        assertEquals(0xff01fe02, cache.intAt(POS_BYTES, ByteOrder.BIG_ENDIAN));
        assertEquals(0x01ff02fe, cache.tagAt(0, ByteOrder.LITTLE_ENDIAN));
        assertEquals(0xff01fe02, cache.tagAt(POS_BYTES, ByteOrder.BIG_ENDIAN));
        assertEquals(0x04fc03fd02fe01ffL, cache.longAt(0, ByteOrder.LITTLE_ENDIAN));
        assertEquals(0xff01fe02fd03fc04L, cache.longAt(POS_BYTES, ByteOrder.BIG_ENDIAN));
        assertArrayEquals(BYTES, cache.bytesAt(POS_BYTES, BYTES.length));
        assertEquals(POS_PN + 2, cache.loadFromStream(Integer.MAX_VALUE, in));
        Assertions.assertEquals(VR.PN.code, cache.vrcode(POS_PN));
        assertEquals("P", cache.stringAt(POS_PN, 1, SpecificCharacterSet.ASCII));
        assertEquals("PN", cache.stringAt(POS_PN, 2, SpecificCharacterSet.ASCII));
        assertArrayEquals(BYTES, writeBytesTo(cache, 0, BYTES.length));
        assertArrayEquals(BYTES, writeBytesTo(cache, POS_BYTES, BYTES.length));
        byte[] buf = new byte[8];
        assertArrayEquals(SWAPPED, writeSwappedBytesTo(cache, 0, SWAPPED.length, ToggleByteOrder.SHORT, buf));
        assertArrayEquals(SWAPPED, writeSwappedBytesTo(cache, POS_BYTES, SWAPPED.length, ToggleByteOrder.SHORT, buf));
    }

    private byte[] writeBytesTo(MemoryCache cache, int pos, int len) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cache.writeBytesTo(pos, len, out);
        return out.toByteArray();
    }

    private byte[] writeSwappedBytesTo(MemoryCache cache, int pos, int len, ToggleByteOrder toggleByteOrder, byte[] buf)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cache.writeSwappedBytesTo(pos, len, out, toggleByteOrder, buf);
        return out.toByteArray();
    }

}
package org.dcm4che.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
class ByteOrderTest {
    private static final byte[] SHORT = {-1, 1};
    private static final byte[] INT = {-1, 1, -2, 2};
    private static final byte[] LONG = {-1, 1, -2, 2, -3, 3, -4, 4};

    @Test
    void bytesToShortLE() {
        assertEquals((short) 0x01ff, ByteOrder.LITTLE_ENDIAN.bytesToShort(SHORT, 0));
    }

    @Test
    void bytesToIntLE() {
        assertEquals(0x02fe01ff, ByteOrder.LITTLE_ENDIAN.bytesToInt(INT, 0));
    }

    @Test
    void bytesToTagLE() {
        assertEquals(0x01ff02fe, ByteOrder.LITTLE_ENDIAN.bytesToTag(INT, 0));
    }

    @Test
    void bytesToLongLE() {
        assertEquals(0x04fc03fd02fe01ffL, ByteOrder.LITTLE_ENDIAN.bytesToLong(LONG, 0));
    }

    @Test
    void shortToBytesLE() {
        byte[] dest = new byte[2];
        ByteOrder.LITTLE_ENDIAN.shortToBytes(0x01ff, dest, 0);
        assertArrayEquals(SHORT, dest);
    }

    @Test
    void intToBytesLE() {
        byte[] dest = new byte[4];
        ByteOrder.LITTLE_ENDIAN.intToBytes(0x02fe01ff, dest, 0);
        assertArrayEquals(INT, dest);
    }

    @Test
    void tagToBytesLE() {
        byte[] dest = new byte[4];
        ByteOrder.LITTLE_ENDIAN.tagToBytes(0x01ff02fe, dest, 0);
        assertArrayEquals(INT, dest);
    }

    @Test
    void longToBytesLE() {
        byte[] dest = new byte[8];
        ByteOrder.LITTLE_ENDIAN.longToBytes(0x04fc03fd02fe01ffL, dest, 0);
        assertArrayEquals(LONG, dest);
    }

    @Test
    void bytesToShortBE() {
        assertEquals((short) 0xff01, ByteOrder.BIG_ENDIAN.bytesToShort(SHORT, 0));
    }

    @Test
    void bytesToIntBE() {
        assertEquals(0xff01fe02, ByteOrder.BIG_ENDIAN.bytesToInt(INT, 0));
    }

    @Test
    void bytesToTagBE() {
        assertEquals(0xff01fe02, ByteOrder.BIG_ENDIAN.bytesToTag(INT, 0));
    }

    @Test
    void bytesToLongBE() {
        assertEquals(0xff01fe02fd03fc04L, ByteOrder.BIG_ENDIAN.bytesToLong(LONG, 0));
    }

    @Test
    void shortToBytesBE() {
        byte[] dest = new byte[2];
        ByteOrder.BIG_ENDIAN.shortToBytes(0xff01, dest, 0);
        assertArrayEquals(SHORT, dest);
    }

    @Test
    void intToBytesBE() {
        byte[] dest = new byte[4];
        ByteOrder.BIG_ENDIAN.intToBytes(0xff01fe02, dest, 0);
        assertArrayEquals(INT, dest);
    }

    @Test
    void tagToBytesBE() {
        byte[] dest = new byte[4];
        ByteOrder.BIG_ENDIAN.tagToBytes(0xff01fe02, dest, 0);
        assertArrayEquals(INT, dest);
    }

    @Test
    void longToBytesBE() {
        byte[] dest = new byte[8];
        ByteOrder.BIG_ENDIAN.longToBytes(0xff01fe02fd03fc04L, dest, 0);
        assertArrayEquals(LONG, dest);
    }

}
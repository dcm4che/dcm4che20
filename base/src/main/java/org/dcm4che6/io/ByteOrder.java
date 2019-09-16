package org.dcm4che6.io;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
public enum ByteOrder {
    LITTLE_ENDIAN {

        @Override
        short bytesToShort(int b0, int b1) {
            return (short) ((b1 << 8) | b0);
        }

        @Override
        int bytesToInt(int b0, int b1, int b2, int b3) {
            return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        }

        @Override
        int bytesToTag(int b0, int b1, int b2, int b3) {
            return bytesToInt(b2, b3, b0, b1);
        }

        @Override
        long bytesToLong(int b0, int b1, int b2, int b3, int b4, int b5, int b6, int b7) {
            return ((long) bytesToInt(b4, b5, b6, b7) << 32) | (bytesToInt(b0, b1, b2, b3) & 0xffffffffL);
        }

        @Override
        public void shortToBytes(int val, byte[] dest, int destPos) {
            dest[destPos] = (byte) val;
            dest[destPos + 1] = (byte) (val >> 8);
        }

        @Override
        public void intToBytes(int val, byte[] dest, int destPos) {
            dest[destPos] = (byte) val;
            dest[destPos + 1] = (byte) (val >> 8);
            dest[destPos + 2] = (byte) (val >> 16);
            dest[destPos + 3] = (byte) (val >> 24);
        }

        @Override
        public void tagToBytes(int val, byte[] dest, int destPos) {
            dest[destPos] = (byte) (val >> 16);
            dest[destPos + 1] = (byte) (val >> 24);
            dest[destPos + 2] = (byte) val;
            dest[destPos + 3] = (byte) (val >> 8);
        }

        @Override
        public void longToBytes(long val, byte[] dest, int destPos) {
            intToBytes((int) val, dest, destPos);
            intToBytes((int) (val >> 32), dest, destPos + 4);
        }
    },
    BIG_ENDIAN {

        @Override
        short bytesToShort(int b0, int b1) {
            return LITTLE_ENDIAN.bytesToShort(b1, b0);
        }

        @Override
        int bytesToInt(int b0, int b1, int b2, int b3) {
            return LITTLE_ENDIAN.bytesToInt(b3, b2, b1, b0);
        }

        @Override
        int bytesToTag(int b0, int b1, int b2, int b3) {
            return LITTLE_ENDIAN.bytesToInt(b3, b2, b1, b0);
        }

        @Override
        long bytesToLong(int b0, int b1, int b2, int b3, int b4, int b5, int b6, int b7) {
            return LITTLE_ENDIAN.bytesToLong(b7, b6, b5, b4, b3, b2, b1, b0);
        }

        @Override
        public void shortToBytes(int val, byte[] dest, int destPos) {
            dest[destPos] = (byte) (val >> 8);
            dest[destPos + 1] = (byte) val;
        }

        @Override
        public void intToBytes(int val, byte[] dest, int destPos) {
            dest[destPos] = (byte) (val >> 24);
            dest[destPos + 1] = (byte) (val >> 16);
            dest[destPos + 2] = (byte) (val >> 8);
            dest[destPos + 3] = (byte) val;
        }

        @Override
        public void tagToBytes(int val, byte[] dest, int destPos) {
            intToBytes(val, dest, destPos);
        }

        @Override
        public void longToBytes(long val, byte[] dest, int destPos) {
            intToBytes((int) (val >> 32), dest, destPos);
            intToBytes((int) val, dest, destPos + 4);
        }
    };

    abstract short bytesToShort(int b1, int b0);

    abstract int bytesToInt(int b3, int b2, int b1, int b0);

    abstract int bytesToTag(int b3, int b2, int b1, int b0);

    abstract long bytesToLong(int b7, int b6, int b5, int b4, int b3, int b2, int b1, int b0);

    public short bytesToShort(byte b1, byte b0) {
        return bytesToShort(b1 & 0xff, b0 & 0xff);
    }

    public int bytesToInt(byte b3, byte b2, byte b1, byte b0) {
        return bytesToInt(b3 & 0xff, b2 & 0xff, b1 & 0xff, b0 & 0xff);
    }

    public int bytesToTag(byte b3, byte b2, byte b1, byte b0) {
        return bytesToTag(b3 & 0xff, b2 & 0xff, b1 & 0xff, b0 & 0xff);
    }

    public long bytesToLong(byte b7, byte b6, byte b5, byte b4, byte b3, byte b2, byte b1, byte b0) {
        return bytesToLong(b7 & 0xff, b6 & 0xff, b5 & 0xff, b4 & 0xff,
                b3 & 0xff, b2 & 0xff, b1 & 0xff, b0 & 0xff);
    }

    public short bytesToShort(byte[] src, int srcPos) {
        return bytesToShort(src[srcPos], src[srcPos + 1]);
    }

    public int bytesToUShort(byte[] src, int srcPos) {
        return bytesToShort(src, srcPos) & 0xffff;
    }

    public int bytesToInt(byte[] src, int srcPos) {
        return bytesToInt(src[srcPos], src[srcPos + 1], src[srcPos + 2], src[srcPos + 3]);
    }

    public long bytesToUInt(byte[] src, int srcPos) {
        return bytesToInt(src, srcPos) & 0xffffffffL;
    }

    public int bytesToTag(byte[] src, int srcPos) {
        return bytesToTag(src[srcPos], src[srcPos + 1], src[srcPos + 2], src[srcPos + 3]);
    }

    public long bytesToLong(byte[] src, int srcPos) {
        return bytesToLong(src[srcPos], src[srcPos + 1], src[srcPos + 2], src[srcPos + 3],
                src[srcPos + 4], src[srcPos + 5], src[srcPos + 6], src[srcPos + 7]);
    }

    public abstract void shortToBytes(int val, byte[] dest, int destPos);

    public abstract void intToBytes(int val, byte[] dest, int destPos);

    public abstract void tagToBytes(int val, byte[] dest, int destPos);

    public abstract void longToBytes(long val, byte[] dest, int destPos);
}

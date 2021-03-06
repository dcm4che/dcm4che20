package org.dcm4che6.internal;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2018
 */
public enum ToggleByteOrder {
    SHORT {
        @Override
        public int swapBytes(byte[] b, int len) {
            len &= -2;
            for (int i = 0; i < len; ) {
                ToggleByteOrder.swapBytes(b, i++, i++);
            }
            return len;
        }
    },
    INT4 {
        @Override
        public int swapBytes(byte[] b, int len) {
            len &= -4;
            for (int i = 0; i < len; i += 2) {
                ToggleByteOrder.swapBytes(b, i++, i + 2);
                ToggleByteOrder.swapBytes(b, i++, i);
            }
            return len;
        }
    },
    INT8 {
        @Override
        public int swapBytes(byte[] b, int len) {
            len &= -8;
            for (int i = 0; i < len; i += 4) {
                ToggleByteOrder.swapBytes(b, i++, i + 6);
                ToggleByteOrder.swapBytes(b, i++, i + 4);
                ToggleByteOrder.swapBytes(b, i++, i + 2);
                ToggleByteOrder.swapBytes(b, i++, i);
            }
            return len;
        }
    };

    public abstract int swapBytes(byte[] b, int len);

    private static void swapBytes(byte[] b, int i, int j) {
        byte tmp = b[i];
        b[i] = b[j];
        b[j] = tmp;
    }
}

package org.dcm4che6.net;

import java.nio.ByteBuffer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
class ByteBufferUtils {
    static String getASCII(ByteBuffer buffer, int length, byte[] b64) {
        buffer.get(b64, 0, length);
        return new String(b64, 0, 0, length);
    }

    static void putLengthASCII(ByteBuffer buffer, String s, byte[] b64) {
        int length = s.length();
        s.getBytes(0, length, b64, 0);
        buffer.putShort((short) length);
        buffer.put(b64, 0, length);
    }

    static byte[] getBytes(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

}

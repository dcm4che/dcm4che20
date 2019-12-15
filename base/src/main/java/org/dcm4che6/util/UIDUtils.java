package org.dcm4che6.util;

import org.dcm4che6.data.UID;
import org.dcm4che6.io.ByteOrder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class UIDUtils {

    /**
     * UID root for UUIDs (Universally Unique Identifiers) generated in
     * accordance with Rec. ITU-T X.667 | ISO/IEC 9834-8.
     * @see &lt;a href="http://www.oid-info.com/get/2.25">OID repository {joint-iso-itu-t(2) uuid(25)}$lt;/a>
     */
    private static final String UUID_ROOT = "2.25";

    public static String randomUID() {
        return toUID(UUID_ROOT, UUID.randomUUID());
    }

    public static String nameUIDFromBytes(byte[] name) {
        return toUID(UUID_ROOT, UUID.nameUUIDFromBytes(name));
    }

    public static String nameUIDFromString(String name) {
        return nameUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static String toUID(String root, UUID uuid) {
        byte[] b17 = new byte[17];
        ByteOrder.BIG_ENDIAN.longToBytes(uuid.getMostSignificantBits(), b17, 1);
        ByteOrder.BIG_ENDIAN.longToBytes(uuid.getLeastSignificantBits(), b17, 9);
        String uuidStr = new BigInteger(b17).toString();
        int rootlen = root.length();
        int uuidlen = uuidStr.length();
        char[] cs = new char[rootlen + uuidlen + 1];
        root.getChars(0, rootlen, cs, 0);
        cs[rootlen] = '.';
        uuidStr.getChars(0, uuidlen, cs, rootlen + 1);
        return new String(cs);
    }

    public static StringBuilder promptTo(String uid, StringBuilder sb) {
        return sb.append(uid).append(" - ").append(UID.nameOf(uid));
    }
}

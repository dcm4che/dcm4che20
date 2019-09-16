package org.dcm4che6.util;

import org.dcm4che6.data.Tag;

import java.util.OptionalInt;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
public class TagUtils {
    private static char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final byte INV_HEX_DIGITS[] = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            0,  1,  2,  3,  4,  5,  6,  7,  8,  9, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15
    };

    public static String shortToHexString(int n) {
        char[] s = {
                HEX_DIGITS[(n >>> 12) & 0xF],
                HEX_DIGITS[(n >>> 8) & 0xF],
                HEX_DIGITS[(n >>> 4) & 0xF],
                HEX_DIGITS[(n >>> 0) & 0xF] };
        return new String(s);
    }

    public static String toHexString(int tag) {
        char[] s = {
                HEX_DIGITS[(tag >>> 28)],
                HEX_DIGITS[(tag >>> 24) & 0xF],
                HEX_DIGITS[(tag >>> 20) & 0xF],
                HEX_DIGITS[(tag >>> 16) & 0xF],
                HEX_DIGITS[(tag >>> 12) & 0xF],
                HEX_DIGITS[(tag >>> 8) & 0xF],
                HEX_DIGITS[(tag >>> 4) & 0xF],
                HEX_DIGITS[(tag >>> 0) & 0xF] };
        return new String(s);
    }

    public static String[] toHexStrings(int[] vals) {
        int n = vals.length;
        String[] ss = new String[n];
        for (int i = 0; i < n; i++)
            ss[i] = toHexString(vals[i]);

        return ss;
    }

    public static String toHexString(byte[] b) {
        char[] s = new char[b.length << 1];
        for (int i = 0, j = 0; i < b.length; i++) {
            s[j++] = HEX_DIGITS[(b[i] >>> 4) & 0xF];
            s[j++] = HEX_DIGITS[b[i] & 0xF];
        }
        return new String(s);
    }

    public static int intFromHexString(String s) {
        char[] chars = s.toCharArray();
        int val = 0;
        for (int i = 0; i < chars.length; i++)
            val = (val << 4) | hexDigitOrThrow(chars[i], s);
        return val;
    }

    private static byte hexDigitOrThrow(char c, String s) {
        try {
            byte b = INV_HEX_DIGITS[c];
            if (b >= 0) return b;
        } catch (IndexOutOfBoundsException e) {}
        throw new NumberFormatException(s);
    }

    public static String toString(int tag) {
        char[] s = {
                '(',
                HEX_DIGITS[(tag >>> 28)],
                HEX_DIGITS[(tag >>> 24) & 0xF],
                HEX_DIGITS[(tag >>> 20) & 0xF],
                HEX_DIGITS[(tag >>> 16) & 0xF],
                ',',
                HEX_DIGITS[(tag >>> 12) & 0xF],
                HEX_DIGITS[(tag >>> 8) & 0xF],
                HEX_DIGITS[(tag >>> 4) & 0xF],
                HEX_DIGITS[(tag >>> 0) & 0xF],
                ')'};
        return new String(s);
    }

    public static int groupNumber(int tag) {
        return tag >>> 16;
    }

    public static int elementNumber(int tag) {
        return tag & 0xFFFF;
    }

    public static boolean isGroupLength(int tag) {
        return elementNumber(tag) == 0;
    }

    public static boolean isPrivateCreator(int tag) {
        return (tag & 0x00010000) != 0
                && (tag & 0x0000FF00) == 0
                && (tag & 0x000000F0) != 0;
    }

    public static boolean isPrivateGroup(int tag) {
        return (tag & 0x00010000) != 0;
    }

    public static boolean isPrivateTag(int tag) {
        return (tag & 0x00010000) != 0
                && (tag & 0x0000FF00) != 0;
    }

    public static int toTag(int groupNumber, int elementNumber) {
        return groupNumber << 16 | elementNumber;
    }

    public static int toPrivateTag(int creatorTag, int elementNumber) {
        return (creatorTag & 0xffff0000)
                | ((creatorTag & 0xff) << 8
                | (elementNumber & 0xff));
    }

    public static int creatorTagOf(int tag) {
        return (tag & 0xffff0000) | ((tag >>> 8) & 0xff);
    }

    public static int groupLengthTagOf(int tag) {
        return tag & 0xffff0000;
    }

    public static boolean isItem(int tag) {
        return tag == Tag.Item
                || tag == Tag.ItemDelimitationItem
                || tag == Tag.SequenceDelimitationItem;
    }

    public static boolean isFileMetaInformation(int tag) {
        return (tag & 0xffff0000) == 0x00020000;
    }

    public static int normalizeRepeatingGroup(int tag) {
        int gg000000 = tag & 0xffe00000;
        return (gg000000 == 0x50000000
                || gg000000 == 0x60000000)
                ? tag & 0xffe0ffff
                : tag;
    }

    public static int forName(String name) {
        try {
            return intFromHexString(name);
        } catch (NumberFormatException nfe) {
            try {
                return Tag.class.getField(name).getInt(null);
            } catch (Exception e) {
                throw new IllegalArgumentException(name);
            }
        }
    }
}

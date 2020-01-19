package org.dcm4che6.codec;

import java.nio.ByteBuffer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jan 2020
 */
public class MP4FileType {
    public static final int qt = 0x71742020;
    public static final int isom = 0x69736f6d;
    public static final MP4FileType ISOM_QT = new MP4FileType(isom, 0, isom, qt);

    private final int[] brands;

    public MP4FileType(int majorBrand, int minorVersion, int... compatibleBrands) {
        this.brands = new int[2 + compatibleBrands.length];
        brands[0] = majorBrand;
        brands[1] = minorVersion;
        System.arraycopy(compatibleBrands, 0, brands, 2, compatibleBrands.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        append4CC(sb.append("ftyp["), brands[0]);
        sb.append('.').append(brands[1]);
        for (int i = 2; i < brands.length; i++) {
            append4CC(sb.append(", "), brands[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public byte[] toBytes() {
        ByteBuffer bb = ByteBuffer.allocate(size());
        bb.putInt(bb.remaining());
        bb.putInt(0x66747970);
        for (int brand : brands) {
            bb.putInt(brand);
        }
        return bb.array();
    }

    private static void append4CC(StringBuilder sb, int brand) {
        sb.append((char)((brand >>> 24) & 0xFF));
        sb.append((char)((brand >>> 16) & 0xFF));
        sb.append((char)((brand >>> 8) & 0xFF));
        sb.append((char)((brand >>> 0) & 0xFF));
    }

    public int size() {
        return 8 + 4 * brands.length;
    }

    public int majorBrand() {
        return brands[0];
    }

    public int minorVersion() {
        return brands[1];
    }

    public int[] compatibleBrands() {
        int[] compatibleBrands = new int[brands.length - 2];
        System.arraycopy(brands, 2, brands, 0, compatibleBrands.length);
        return compatibleBrands;
    }
}

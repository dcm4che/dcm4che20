package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public enum DicomEncoding {
    IVR_LE(false, ByteOrder.LITTLE_ENDIAN, false),
    EVR_LE(true, ByteOrder.LITTLE_ENDIAN, false),
    EVR_BE(true, ByteOrder.BIG_ENDIAN, false),
    DEFL_EVR_LE(true, ByteOrder.LITTLE_ENDIAN, true),
    SERIALIZE(true, ByteOrder.LITTLE_ENDIAN, false);

    public final boolean explicitVR;
    public final ByteOrder byteOrder;
    public final boolean deflated;

    DicomEncoding(boolean explicitVR, ByteOrder byteOrder, boolean deflated) {
        this.explicitVR = explicitVR;
        this.byteOrder = byteOrder;
        this.deflated = deflated;
    }

    public static DicomEncoding of(String tsuid) {
        switch (tsuid) {
            case UID.ImplicitVRLittleEndian:
                return IVR_LE;
            case UID.ExplicitVRBigEndianRetired:
                return EVR_BE;
            case UID.DeflatedExplicitVRLittleEndian:
            case UID.JPIPReferencedDeflate:
                return DEFL_EVR_LE;
        }
        return EVR_LE;
    }
}

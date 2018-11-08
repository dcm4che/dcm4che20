package org.dcm4che.io;

import org.dcm4che.data.UID;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public enum DicomEncoding {
    IVR_LE(false, ByteOrder.LITTLE_ENDIAN, false, -1),
    EVR_LE(true, ByteOrder.LITTLE_ENDIAN, false, -1),
    EVR_BE(true, ByteOrder.BIG_ENDIAN, false, -1),
    DEFL_EVR_LE(true, ByteOrder.LITTLE_ENDIAN, true, -1),
    SERIALIZE(true, ByteOrder.LITTLE_ENDIAN, false, 0x7fff);

    public final boolean explicitVR;
    public final ByteOrder byteOrder;
    public final boolean deflated;
    public final int vrCodeMask;

    DicomEncoding(boolean explicitVR, ByteOrder byteOrder, boolean deflated, int vrCodeMask) {
        this.explicitVR = explicitVR;
        this.byteOrder = byteOrder;
        this.deflated = deflated;
        this.vrCodeMask = vrCodeMask;
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

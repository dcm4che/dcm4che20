package org.dcm4che.data;

import org.dcm4che.internal.BinaryVR;
import org.dcm4che.internal.SequenceVR;
import org.dcm4che.internal.StringVR;
import org.dcm4che.internal.VRType;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public enum VR {
    AE(0x4145, true, StringVR.ASCII, 0x20, JSONType.STRING),
    AS(0x4153, true, StringVR.ASCII, 0x20, JSONType.STRING),
    AT(0x4154, true, BinaryVR.AT, 0, JSONType.STRING),
    CS(0x4353, true, StringVR.ASCII, 0x20, JSONType.STRING),
    DA(0x4441, true, StringVR.ASCII, 0x20, JSONType.STRING),
    DS(0x4453, true, StringVR.DS, 0x20, JSONType.DOUBLE),
    DT(0x4454, true, StringVR.ASCII, 0x20, JSONType.STRING),
    FD(0x4644, true, BinaryVR.FD, 0, JSONType.DOUBLE),
    FL(0x464c, true, BinaryVR.FL, 0, JSONType.DOUBLE),
    IS(0x4953, true, StringVR.IS, 0x20, JSONType.INT),
    LO(0x4c4f, true, StringVR.STRING, 0x20, JSONType.STRING),
    LT(0x4c54, true, StringVR.TEXT, 0x20, JSONType.STRING),
    OB(0x4f42, false, BinaryVR.OB, 0, JSONType.BASE64),
    OD(0x4f44, false, BinaryVR.FD, 0, JSONType.BASE64),
    OF(0x4f46, false, BinaryVR.FL, 0, JSONType.BASE64),
    OL(0x4f4c, false, BinaryVR.SL, 0, JSONType.BASE64),
    OW(0x4f57, false, BinaryVR.SS, 0, JSONType.BASE64),
    PN(0x504e, true, StringVR.PN, 0x20, JSONType.PN),
    SH(0x5348, true, StringVR.STRING, 0x20, JSONType.STRING),
    SL(0x534c, true, BinaryVR.SL, 0, JSONType.INT),
    SQ(0x5351, false, SequenceVR.SQ, 0, JSONType.SQ),
    SS(0x5353, true, BinaryVR.SS, 0, JSONType.INT),
    ST(0x5354, true, StringVR.TEXT, 0x20, JSONType.STRING),
    TM(0x544d, true, StringVR.ASCII, 0x20, JSONType.STRING),
    UC(0x5543, false, StringVR.UC, 0x20, JSONType.STRING),
    UI(0x5549, true, StringVR.ASCII, 0, JSONType.STRING),
    UL(0x554c, true, BinaryVR.UL, 0, JSONType.UINT),
    UN(0x554e, false, BinaryVR.OB, 0, JSONType.BASE64),
    UR(0x5552, false, StringVR.UR, 0x20, JSONType.STRING),
    US(0x5553, true, BinaryVR.US, 0x20, JSONType.INT),
    UT(0x5554, false, StringVR.TEXT, 0, JSONType.STRING),
    NONE(-1, true, null, 0, null);

    public enum JSONType { BASE64, SQ, PN, STRING, INT, UINT, DOUBLE }
    public final int code;
    public final boolean shortValueLength;
    public final VRType type;
    public final int paddingByte;
    public final JSONType jsonType;

    VR(int code, boolean shortValueLength, VRType type, int paddingByte, JSONType jsonType) {
        this.code = code;
        this.shortValueLength = shortValueLength;
        this.type = type;
        this.paddingByte = paddingByte;
        this.jsonType = jsonType;
    }

    public static VR of(int code) {
        switch (code) {
            case 0x4145: return AE;
            case 0x4153: return AS;
            case 0x4154: return AT;
            case 0x4353: return CS;
            case 0x4441: return DA;
            case 0x4453: return DS;
            case 0x4454: return DT;
            case 0x4644: return FD;
            case 0x464c: return FL;
            case 0x4953: return IS;
            case 0x4c4f: return LO;
            case 0x4c54: return LT;
            case 0x4f42: return OB;
            case 0x4f44: return OD;
            case 0x4f46: return OF;
            case 0x4f4c: return OL;
            case 0x4f57: return OW;
            case 0x504e: return PN;
            case 0x5348: return SH;
            case 0x534c: return SL;
            case 0x5351: return SQ;
            case 0x5353: return SS;
            case 0x5354: return ST;
            case 0x544d: return TM;
            case 0x5543: return UC;
            case 0x5549: return UI;
            case 0x554c: return UL;
            case 0x554e: return UN;
            case 0x5552: return UR;
            case 0x5553: return US;
            case 0x5554: return UT;
        }
        throw new IllegalArgumentException(String.format("Unknown VR code: %04XH", code));
    }

}

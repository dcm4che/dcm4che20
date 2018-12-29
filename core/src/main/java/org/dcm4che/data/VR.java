package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public enum VR {
    AE(0x4145, true, StringVR.ASCII, 0x20),
    AS(0x4153, true, StringVR.ASCII, 0x20),
    AT(0x4154, true, BinaryVR.AT, 0),
    CS(0x4353, true, StringVR.ASCII, 0x20),
    DA(0x4441, true, StringVR.ASCII, 0x20),
    DS(0x4453, true, StringVR.ASCII, 0x20),
    DT(0x4454, true, StringVR.ASCII, 0x20),
    FD(0x4644, true, BinaryVR.FD, 0),
    FL(0x464c, true, BinaryVR.FL, 0),
    IS(0x4953, true, StringVR.ASCII, 0x20),
    LO(0x4c4f, true, StringVR.STRING, 0x20),
    LT(0x4c54, true, StringVR.TEXT, 0x20),
    OB(0x4f42, false, BinaryVR.OB, 0),
    OD(0x4f44, false, BinaryVR.FD, 0),
    OF(0x4f46, false, BinaryVR.FL, 0),
    OL(0x4f4c, false, BinaryVR.SL, 0),
    OW(0x4f57, false, BinaryVR.SS, 0),
    PN(0x504e, true, StringVR.PN, 0x20),
    SH(0x5348, true, StringVR.STRING, 0x20),
    SL(0x534c, true, BinaryVR.SL, 0),
    SQ(0x5351, false, SequenceVR.SQ, 0),
    SS(0x5353, true, BinaryVR.SS, 0),
    ST(0x5354, true, StringVR.TEXT, 0x20),
    TM(0x544d, true, StringVR.ASCII, 0x20),
    UC(0x5543, false, StringVR.UC, 0x20),
    UI(0x5549, true, StringVR.ASCII, 0),
    UL(0x554c, true, BinaryVR.UL, 0),
    UN(0x554e, false, BinaryVR.OB, 0),
    UR(0x5552, false, StringVR.UR, 0x20),
    US(0x5553, true, BinaryVR.US, 0x20),
    UT(0x5554, false, StringVR.TEXT, 0),
    NONE(-1, true, null, 0);

    public final int code;
    public final boolean shortValueLength;
    final VRType type;
    final int paddingByte;

    VR(int code, boolean shortValueLength, VRType type, int paddingByte) {
        this.code = code;
        this.shortValueLength = shortValueLength;
        this.type = type;
        this.paddingByte = paddingByte;
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

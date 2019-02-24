package org.dcm4che.data;

import org.dcm4che.util.StringUtils;
import org.dcm4che.util.TagUtils;

import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
enum BinaryVR implements VRType {
    AT(4, ToggleByteOrder.SHORT,
            BinaryVR::tagToBytes, BinaryVR::bytesToTag, DicomInput::tagAt,
            BinaryVR::tagToBytes, BinaryVR::bytesToTag, DicomInput::tagAt,
            BinaryVR::tagToBytes, BinaryVR::bytesToTag, DicomInput::tagAt,
            BinaryVR::parseTag, BinaryVR::tagToString, BinaryVR::tagToString),
    FD(8, ToggleByteOrder.INT8,
            BinaryVR::doubleToBytes, BinaryVR::bytesToDoubleAsInt, BinaryVR::dicomInputToDoubleAsInt,
            BinaryVR::doubleToBytes, BinaryVR::bytesToDoubleAsFloat, BinaryVR::dicomInputToDoubleAsFloat,
            BinaryVR::doubleToBytes, BinaryVR::bytesToDouble, BinaryVR::dicomInputToDouble,
            BinaryVR::parseDouble, BinaryVR::doubleToString, BinaryVR::doubleToString),
    FL(4, ToggleByteOrder.INT4,
            BinaryVR::floatToBytes, BinaryVR::bytesToFloatAsInt, BinaryVR::dicomInputToFloatAsInt,
            BinaryVR::floatToBytes, BinaryVR::bytesToFloat, BinaryVR::dicomInputToFloat,
            BinaryVR::floatToBytes, BinaryVR::bytesToFloat, BinaryVR::dicomInputToFloat,
            BinaryVR::parseFloat, BinaryVR::floatToString, BinaryVR::floatToString),
    OB(1, null,
            BinaryVR::intToByte, BinaryVR::byteAt, DicomInput::byteAt,
            BinaryVR::floatToByte, BinaryVR::byteAt, DicomInput::byteAt,
            BinaryVR::doubleToByte, BinaryVR::byteAt, DicomInput::byteAt,
            BinaryVR::parseByte, BinaryVR::byteToString, BinaryVR::byteToString),
    SL(4, ToggleByteOrder.INT4,
            BinaryVR::intToBytes, BinaryVR::bytesToInt, DicomInput::intAt,
            BinaryVR::intToBytes, BinaryVR::bytesToInt, DicomInput::intAt,
            BinaryVR::intToBytes, BinaryVR::bytesToInt, DicomInput::intAt,
            BinaryVR::parseInt, BinaryVR::intToString, BinaryVR::intToString),
    SS(2, ToggleByteOrder.SHORT,
            BinaryVR::shortToBytes, BinaryVR::bytesToShort, DicomInput::shortAt,
            BinaryVR::shortToBytes, BinaryVR::bytesToShort, DicomInput::shortAt,
            BinaryVR::shortToBytes, BinaryVR::bytesToShort, DicomInput::shortAt,
            BinaryVR::parseShort, BinaryVR::shortToString, BinaryVR::shortToString),
    UL(4, ToggleByteOrder.INT4,
            BinaryVR::intToBytes, BinaryVR::bytesToInt, DicomInput::intAt,
            BinaryVR::intToBytes, BinaryVR::bytesToUInt, DicomInput::uintAt,
            BinaryVR::intToBytes, BinaryVR::bytesToUInt, DicomInput::uintAt,
            BinaryVR::parseUInt, BinaryVR::uintToString, BinaryVR::uintToString),
    US(2, ToggleByteOrder.SHORT,
            BinaryVR::shortToBytes, BinaryVR::bytesToUShort, DicomInput::ushortAt,
            BinaryVR::shortToBytes, BinaryVR::bytesToUShort, DicomInput::ushortAt,
            BinaryVR::shortToBytes, BinaryVR::bytesToUShort, DicomInput::ushortAt,
            BinaryVR::parseShort, BinaryVR::ushortToString, BinaryVR::ushortToString);

    private final int bytes;
    private final ToggleByteOrder toggleByteOrder;
    private final IntToBytes intToBytes;
    private final BytesToInt bytesToInt;
    private final DicomInputToInt dicomInputToInt;
    private final FloatToBytes floatToBytes;
    private final BytesToFloat bytesToFloat;
    private final DicomInputToFloat dicomInputToFloat;
    private final DoubleToBytes doubleToBytes;
    private final BytesToDouble bytesToDouble;
    private final DicomInputToDouble dicomInputToDouble;
    private final StringToBytes stringToBytes;
    private final BytesToString bytesToString;
    private final DicomInputToString dicomInputToString;

    BinaryVR(int bytes, ToggleByteOrder toggleByteOrder,
             IntToBytes intToBytes, BytesToInt bytesToInt, DicomInputToInt dicomInputToInt,
             FloatToBytes floatToBytes, BytesToFloat bytesToFloat, DicomInputToFloat dicomInputToFloat,
             DoubleToBytes doubleToBytes, BytesToDouble bytesToDouble, DicomInputToDouble dicomInputToDouble,
             StringToBytes stringToBytes, BytesToString bytesToString, DicomInputToString dicomInputToString) {
        this.bytes = bytes;
        this.toggleByteOrder = toggleByteOrder;
        this.intToBytes = intToBytes;
        this.dicomInputToInt = dicomInputToInt;
        this.bytesToInt = bytesToInt;
        this.floatToBytes = floatToBytes;
        this.bytesToFloat = bytesToFloat;
        this.dicomInputToFloat = dicomInputToFloat;
        this.doubleToBytes = doubleToBytes;
        this.bytesToDouble = bytesToDouble;
        this.dicomInputToDouble = dicomInputToDouble;
        this.stringToBytes = stringToBytes;
        this.bytesToString = bytesToString;
        this.dicomInputToString = dicomInputToString;
    }

    @Override
    public ToggleByteOrder toggleByteOrder() {
        return toggleByteOrder;
    }

    @Override
    public int intValue(DicomInput input, long valpos, int vallen, int index, int defaultValue) {
        return (vallen / bytes) > index
                ? dicomInputToInt.applyAsInt(input, valpos + (index * bytes))
                : defaultValue;
    }

    @Override
    public int[] intValues(DicomInput input, long valpos, int vallen) {
        int[] a = new int[vallen / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = dicomInputToInt.applyAsInt(input, valpos + (i * bytes));
        }
        return a;
    }

    @Override
    public int intValue(byte[] value, int index, int defaultValue) {
        return (value.length / bytes) > index
                ? bytesToInt.applyAsInt(value, index * bytes)
                : defaultValue;
    }

    @Override
    public int[] intValues(byte[] value) {
        int[] a = new int[value.length / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = bytesToInt.applyAsInt(value, i * bytes);
        }
        return a;
    }

    @Override
    public float floatValue(DicomInput input, long valpos, int vallen, int index, float defaultValue) {
        return (vallen / bytes) > index
                ? dicomInputToFloat.applyAsFloat(input, valpos + (index * bytes))
                : defaultValue;
    }

    @Override
    public float[] floatValues(DicomInput input, long valpos, int vallen) {
        float[] a = new float[vallen / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = dicomInputToFloat.applyAsFloat(input, valpos + (i * bytes));
        }
        return a;
    }

    @Override
    public float floatValue(byte[] value, int index, float defaultValue) {
        return (value.length / bytes) > index
                ? bytesToFloat.applyAsFloat(value, index * bytes)
                : defaultValue;
    }

    @Override
    public float[] floatValues(byte[] value) {
        float[] a = new float[value.length / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = bytesToFloat.applyAsFloat(value, i * bytes);
        }
        return a;
    }

    @Override
    public double doubleValue(DicomInput input, long valpos, int vallen, int index, double defaultValue) {
        return (vallen / bytes) > index
                ? dicomInputToDouble.applyAsDouble(input, valpos + (index * bytes))
                : defaultValue;
    }

    @Override
    public double[] doubleValues(DicomInput input, long valpos, int vallen) {
        double[] a = new double[vallen / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = dicomInputToDouble.applyAsDouble(input, valpos + (i * bytes));
        }
        return a;
    }

    @Override
    public double doubleValue(byte[] value, int index, double defaultValue) {
        return (value.length / bytes) > index
                ? bytesToDouble.applyAsDouble(value, index * bytes)
                : defaultValue;
    }

    @Override
    public double[] doubleValues(byte[] value) {
        double[] a = new double[value.length / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = bytesToDouble.applyAsDouble(value, i * bytes);
        }
        return a;
    }

    @Override
    public StringBuilder appendValue(DicomInput input, long valpos, int vallen, DicomObject dcmobj,
                                     StringBuilder appendTo, int maxLength) {
        int n = vallen / bytes;
        for (int index = 0; index < n; index++) {
            if (index > 0)
                appendTo.append('\\');
            appendTo.append(dicomInputToString.apply(input, valpos + (index * bytes)));
            if (appendTo.length() > maxLength) {
                appendTo.setLength(maxLength);
                break;
            }
        }
        return appendTo;
    }

    @Override
    public String stringValue(DicomInput input, long valpos, int vallen, int index, DicomObject dcmobj,
                              String defaultValue) {
        return (vallen / bytes) > index
                ? dicomInputToString.apply(input, valpos + (index * bytes))
                : defaultValue;
    }

    @Override
    public String[] stringValues(DicomInput input, long valpos, int vallen, DicomObject dcmobj) {
        String[] a = new String[vallen / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = dicomInputToString.apply(input, valpos + (i * bytes));
        }
        return a;
    }

    @Override
    public String stringValue(byte[] value, int index, String defaultValue) {
        return (value.length / bytes) > index
                ? bytesToString.apply(value, index * bytes)
                : defaultValue;
    }

    @Override
    public String[] stringValues(byte[] value) {
        String[] a = new String[value.length / bytes];
        for (int i = 0; i < a.length; i++) {
            a[i] = bytesToString.apply(value, i * bytes);
        }
        return a;
    }

    @Override
    public <E extends Throwable> void forEach(DicomElement dcmElm, StringValueConsumer<E> action) throws E {
        for (int i = 0, n = dcmElm.valueLength() / bytes; i < n;) {
            action.accept(dcmElm.stringValue(i, ""), ++i);
        }
    }

    @Override
    public void forEach(DicomElement dcmElm, IntConsumer action) {
        for (int i = 0, n = dcmElm.valueLength() / bytes; i < n; i++) {
            action.accept(dcmElm.intValue(i, 0));
        }
    }

    @Override
    public void forEach(DicomElement dcmElm, DoubleConsumer action) {
        for (int i = 0, n = dcmElm.valueLength() / bytes; i < n; i++) {
            action.accept(dcmElm.doubleValue(i, 0));
        }
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, byte[] val) {
        if (val.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        return new ByteArrayElement(dcmObj, tag, vr, val);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, int[] vals) {
        if (vals.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        byte[] b = new byte[vals.length * bytes];
        for (int i = 0; i < vals.length; i++) {
            intToBytes.accept(vals[i], b, i * bytes);
        }
        return new ByteArrayElement(dcmObj, tag, vr, b);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, float[] vals) {
        if (vals.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        byte[] b = new byte[vals.length * bytes];
        for (int i = 0; i < vals.length; i++) {
            floatToBytes.accept(vals[i], b, i * bytes);
        }
        return new ByteArrayElement(dcmObj, tag, vr, b);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, double[] vals) {
        if (vals.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        byte[] b = new byte[vals.length * bytes];
        for (int i = 0; i < vals.length; i++) {
            doubleToBytes.accept(vals[i], b, i * bytes);
        }
        return new ByteArrayElement(dcmObj, tag, vr, b);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String val) {
        return null;
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String[] vals) {
        if (vals.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        byte[] b = new byte[vals.length * bytes];
        for (int i = 0; i < vals.length; i++) {
            stringToBytes.accept(vals[i], b, i * bytes);
        }
        return new ByteArrayElement(dcmObj, tag, vr, b);
    }

    @FunctionalInterface
    private interface IntToBytes {
        void accept(int val, byte[] b, int off);
    }

    @FunctionalInterface
    private interface BytesToInt {
        int applyAsInt(byte[] b, int off);
    }

    @FunctionalInterface
    private interface DicomInputToInt {
        int applyAsInt(DicomInput input, long pos);
    }

    @FunctionalInterface
    private interface FloatToBytes {
        void accept(float val, byte[] b, int off);
    }

    @FunctionalInterface
    private interface BytesToFloat {
        float applyAsFloat(byte[] b, int off);
    }

    @FunctionalInterface
    private interface DicomInputToFloat {
        float applyAsFloat(DicomInput input, long pos);
    }

    @FunctionalInterface
    private interface DoubleToBytes {
        void accept(double val, byte[] b, int off);
    }

    @FunctionalInterface
    private interface BytesToDouble {
        double applyAsDouble(byte[] b, int off);
    }

    @FunctionalInterface
    private interface DicomInputToDouble {
        double applyAsDouble(DicomInput input, long pos);
    }

    @FunctionalInterface
    private interface StringToBytes {
        void accept(String val, byte[] b, int off);
    }

    @FunctionalInterface
    private interface BytesToString {
        String apply(byte[] b, int off);
    }

    @FunctionalInterface
    private interface DicomInputToString {
        String apply(DicomInput input, long pos);
    }

    private static byte byteAt(byte[] b, int off) {
        return b[off];
    }

    private static void intToByte(int val, byte[] b, int off) {
        b[off] = (byte) val;
    }

    private static void floatToByte(float val, byte[] b, int off) {
        b[off] = (byte) val;
    }

    private static void doubleToByte(double val, byte[] b, int off) {
        b[off] = (byte) val;
    }

    private static void floatToBytes(float val, byte[] b, int off) {
        intToBytes(Float.floatToIntBits(val), b, off);
    }

    private static void floatToBytes(double val, byte[] b, int off) {
        floatToBytes((float) val, b, off);
    }

    private static void doubleToBytes(double val, byte[] b, int off) {
        ByteOrder.LITTLE_ENDIAN.longToBytes(Double.doubleToLongBits(val), b, off);
    }

    private static void tagToBytes(int val, byte[] b, int off) {
        ByteOrder.LITTLE_ENDIAN.tagToBytes(val, b, off);
    }

    private static void tagToBytes(float val, byte[] b, int off) {
        tagToBytes((int) val, b, off);
    }

    private static void tagToBytes(double val, byte[] b, int off) {
        tagToBytes((int) val, b, off);
    }

    private static void shortToBytes(int val, byte[] b, int off) {
        ByteOrder.LITTLE_ENDIAN.shortToBytes(val, b, off);
    }

    private static void shortToBytes(float val, byte[] b, int off) {
        shortToBytes((int) val, b, off);
    }

    private static void shortToBytes(double val, byte[] b, int off) {
        shortToBytes((int) val, b, off);
    }

    private static void intToBytes(int val, byte[] b, int off) {
        ByteOrder.LITTLE_ENDIAN.intToBytes(val, b, off);
    }

    private static void intToBytes(float val, byte[] b, int off) {
        intToBytes((int) val, b, off);
    }

    private static void intToBytes(double val, byte[] b, int off) {
        intToBytes((int) val, b, off);
    }

    private static byte parseByte(String val, byte[] b, int off) {
        return b[off] = Byte.parseByte(val);
    }

    private static void parseShort(String val, byte[] b, int off) {
        shortToBytes(Integer.parseInt(val), b, off);
    }

    private static void parseUInt(String val, byte[] b, int off) {
        intToBytes(Integer.parseUnsignedInt(val), b, off);
    }

    private static void parseInt(String val, byte[] b, int off) {
        intToBytes(Integer.parseInt(val), b, off);
    }

    private static void parseTag(String val, byte[] b, int off) {
        tagToBytes(Integer.parseUnsignedInt(val,16), b, off);
    }

    private static void parseFloat(String val, byte[] b, int off) {
        floatToBytes(Float.parseFloat(val), b, off);
    }

    private static void parseDouble(String val, byte[] b, int off) {
        doubleToBytes(Double.parseDouble(val), b, off);
    }

    private static int bytesToFloatAsInt(byte[] b, int off) {
        return (int) bytesToFloat(b, off);
    }

    private static String floatToString(byte[] b, int off) {
        return Float.toString(bytesToFloat(b, off));
    }

    private static int bytesToInt(byte[] b, int off) {
        return ByteOrder.LITTLE_ENDIAN.bytesToInt(b, off);
    }

    private static long bytesToUInt(byte[] b, int off) {
        return ByteOrder.LITTLE_ENDIAN.bytesToUInt(b, off);
    }

    private static int bytesToTag(byte[] b, int off) {
        return ByteOrder.LITTLE_ENDIAN.bytesToTag(b, off);
    }

    private static short bytesToShort(byte[] b, int off) {
        return ByteOrder.LITTLE_ENDIAN.bytesToShort(b, off);
    }

    private static int bytesToUShort(byte[] b, int off) {
        return ByteOrder.LITTLE_ENDIAN.bytesToUShort(b, off);
    }

    private static float bytesToFloat(byte[] b, int off) {
        return Float.intBitsToFloat(bytesToInt(b, off));
    }

    private static int bytesToDoubleAsInt(byte[] b, int off) {
        return (int) bytesToDouble(b, off);
    }

    private static float bytesToDoubleAsFloat(byte[] b, int off) {
        return (float) bytesToDouble(b, off);
    }

    private static String doubleToString(byte[] b, int off) {
        return Double.toString(bytesToDouble(b, off));
    }

    private static double bytesToDouble(byte[] b, int off) {
        return Double.longBitsToDouble(ByteOrder.LITTLE_ENDIAN.bytesToLong(b, off));
    }

    private static int dicomInputToDoubleAsInt(DicomInput input, long pos) {
        return (int) dicomInputToDouble(input, pos);
    }

    private static float dicomInputToDoubleAsFloat(DicomInput input, long pos) {
        return (float) dicomInputToDouble(input, pos);
    }

    private static String doubleToString(DicomInput input, long pos) {
        return StringUtils.trimDS(Double.toString(dicomInputToDouble(input, pos)));
    }

    private static double dicomInputToDouble(DicomInput input, long pos) {
        return Double.longBitsToDouble(input.longAt(pos));
    }

    private static int dicomInputToFloatAsInt(DicomInput input, long pos) {
        return (int) dicomInputToFloat(input, pos);
    }

    private static String floatToString(DicomInput input, long pos) {
        return StringUtils.trimDS(Float.toString(dicomInputToFloat(input, pos)));
    }

    private static float dicomInputToFloat(DicomInput input, long pos) {
        return Float.intBitsToFloat(input.intAt(pos));
    }

    private static String byteToString(byte[] b, int off) {
        return Integer.toString(b[off]);
    }

    private static String byteToString(DicomInput input, long pos) {
        return Integer.toString(input.byteAt(pos));
    }

    private static String shortToString(byte[] b, int off) {
        return Integer.toString(bytesToShort(b, off));
    }

    private static String shortToString(DicomInput input, long pos) {
        return Integer.toString(input.shortAt(pos));
    }

    private static String ushortToString(byte[] b, int off) {
        return Integer.toString(bytesToUShort(b, off));
    }

    private static String ushortToString(DicomInput input, long pos) {
        return Integer.toString(input.ushortAt(pos));
    }

    private static String intToString(byte[] b, int off) {
        return Integer.toString(bytesToInt(b, off));
    }

    private static String intToString(DicomInput input, long pos) {
        return Integer.toString(input.intAt(pos));
    }

    private static String uintToString(byte[] b, int off) {
        return Integer.toUnsignedString(bytesToInt(b, off));
    }

    private static String uintToString(DicomInput input, long pos) {
        return Integer.toUnsignedString(input.intAt(pos));
    }

    private static String tagToString(byte[] b, int off) {
        return TagUtils.toHexString(bytesToTag(b, off));
    }

    private static String tagToString(DicomInput input, long pos) {
        return TagUtils.toHexString(input.tagAt(pos));
    }

}

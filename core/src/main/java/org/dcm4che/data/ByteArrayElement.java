package org.dcm4che.data;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class ByteArrayElement extends BaseDicomElement {
    private final byte[] value;

    ByteArrayElement(DicomObject dicomObject, int tag, VR vr, byte[] value) {
        super(dicomObject, tag, vr);
        this.value = value;
    }

    @Override
    public int valueLength() {
        return (value.length + 1) & ~1;
    }

    @Override
    public int intValue(int index, int defaultValue) {
        return vr.type.intValue(value, index, defaultValue);
    }

    @Override
    public int[] intValues() {
        return vr.type.intValues(value);
    }

    @Override
    public float floatValue(int index, float defaultValue) {
        return vr.type.floatValue(value, index, defaultValue);
    }

    @Override
    public float[] floatValues() {
        return vr.type.floatValues(value);
    }

    @Override
    public double doubleValue(int index, double defaultValue) {
        return vr.type.doubleValue(value, index, defaultValue);
    }

    @Override
    public double[] doubleValues() {
        return vr.type.doubleValues(value);
    }

    @Override
    public String stringValue(int index, String defaultValue) {
        return vr.type.stringValue(value, index, defaultValue);
    }

    @Override
    public String[] stringValues() {
        return vr.type.stringValues(value);
    }

    @Override
    public void writeValueTo(DicomOutputStream dos) throws IOException {
        if (dos.getEncoding().byteOrder == ByteOrder.LITTLE_ENDIAN || vr.type.toggleByteOrder() == null) {
            dos.write(value, 0, value.length);
            if ((value.length & 1) != 0)
                dos.write(0);
        } else {
            byte[] b = dos.swapBuffer();
            System.arraycopy(value, 0, b, 0, value.length);
            vr.type.toggleByteOrder().swapBytes(b, value.length);
            dos.write(b, 0, value.length);
        }
    }

}

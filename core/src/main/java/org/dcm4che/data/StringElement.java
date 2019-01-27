package org.dcm4che.data;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class StringElement extends BaseDicomElement {

    private final String value;
    private byte[] encodedValue;

    StringElement(DicomObject parent, int tag, VR vr, String value) {
        super(parent, tag, vr);
        this.value = value;
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
    public int valueLength() {
        byte[] encodedValue = this.encodedValue;
        if (encodedValue == null)
            this.encodedValue = encodedValue = dicomObject.specificCharacterSet().encode(value, vr.type.delimiters());

        return (encodedValue.length + 1) & ~1;
    }

    @Override
    public void writeValueTo(DicomOutputStream dos) throws IOException {
        byte[] value = encodedValue;
        dos.write(value, 0, value.length);
        if ((value.length & 1) != 0)
            dos.write(vr.paddingByte);
    }

    @Override
    public void purgeEncodedValue() {
        encodedValue = null;
    }
}

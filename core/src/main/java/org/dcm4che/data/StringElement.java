package org.dcm4che.data;

import org.dcm4che.io.DicomWriter;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class StringElement extends BaseDicomElement {

    private final String value;
    private EncodedValue encodedValue;

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
        SpecificCharacterSet cs = dicomObject.specificCharacterSet();
        EncodedValue encodedValue = this.encodedValue;
        if (encodedValue == null || !encodedValue.specificCharacterSet.equals(cs))
            this.encodedValue = encodedValue = new EncodedValue(cs, cs.encode(value, vr.type.delimiters()));

        return (encodedValue.value.length + 1) & ~1;
    }

    @Override
    public void writeTo(DicomWriter dicomWriter) throws IOException {
        int vallen = valueLength();
        byte[] value = encodedValue.value;
        dicomWriter.writeHeader(tag, vr, vallen);
        dicomWriter.getOutputStream().write(value, 0, value.length);
        if ((value.length & 1) != 0)
            dicomWriter.getOutputStream().write(vr.paddingByte);
    }

    @Override
    public void purgeEncodedValue() {
        encodedValue = null;
    }

    private static class EncodedValue {
        final SpecificCharacterSet specificCharacterSet;
        final byte[] value;

        private EncodedValue(SpecificCharacterSet specificCharacterSet, byte[] value) {
            this.specificCharacterSet = specificCharacterSet;
            this.value = value;
        }
    }
}

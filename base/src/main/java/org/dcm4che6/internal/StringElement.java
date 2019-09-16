package org.dcm4che6.internal;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.OptionalFloat;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2018
 */
class StringElement extends DicomElementImpl {

    private final String value;
    private byte[] encodedValue;

    StringElement(DicomObject parent, int tag, VR vr, String value) {
        super(parent, tag, vr);
        this.value = value;
    }

    @Override
    public Optional<String> stringValue(int index) {
        return vr.type.stringValue(value, index);
    }

    @Override
    public String[] stringValues() {
        return vr.type.stringValues(value);
    }

    @Override
    public OptionalInt intValue(int index) {
        return vr.type.intValue(value, index);
    }

    @Override
    public int[] intValues() {
        return vr.type.intValues(value);
    }

    @Override
    public OptionalFloat floatValue(int index) {
        return vr.type.floatValue(value, index);
    }

    @Override
    public float[] floatValues() {
        return vr.type.floatValues(value);
    }

    @Override
    public OptionalDouble doubleValue(int index) {
        return vr.type.doubleValue(value, index);
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

package org.dcm4che6.internal;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.ByteOrder;
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
class ByteArrayElement extends DicomElementImpl {
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
    public Optional<String> stringValue(int index) {
        return vr.type.stringValue(value, index);
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

    @Override
    StringBuilder promptValueTo(StringBuilder appendTo, int maxLength) {
        appendTo.append(' ').append('[');
        if (vr.type.appendValue(value, appendTo, maxLength)
                .length() < maxLength) appendTo.append(']');
        return appendTo;
    }
}

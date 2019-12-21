package org.dcm4che6.internal;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.util.function.StringValueConsumer;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.OptionalFloat;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2018
 */
public interface VRType {
    default String delimiters() {
        throw new UnsupportedOperationException();
    }

    default StringBuilder appendValue(DicomInput dicomInput, long valuePos, int valueLen, DicomObject dcmobj,
                                      StringBuilder appendTo, int maxLength) {
        return appendTo;
    }

    default StringBuilder appendValue(byte[] value, StringBuilder appendTo, int maxLength) {
        return appendTo;
    }

    default Optional<String> stringValue(DicomInput input, long valuePos, int valueLen, int index,  DicomObject dcmobj) {
        return Optional.empty();
    }

    default String[] stringValues(DicomInput input, long valuePos, int valueLen, DicomObject dcmobj) {
        return DicomElement.EMPTY_STRINGS;
    }

    default Optional<String> stringValue(byte[] value, int index) {
        return Optional.empty();
    }

    default String[] stringValues(byte[] value) {
        return DicomElement.EMPTY_STRINGS;
    }

    default Optional<String> stringValue(String value, int index) {
        return Optional.empty();
    }

    default String[] stringValues(String value) {
        return DicomElement.EMPTY_STRINGS;
    }

    default <E extends Throwable> void forEachStringValue(DicomElement dcmElm, StringValueConsumer<E> action) throws E {}

    default void forEachIntValue(DicomElement dcmElm, IntConsumer action) {}

    default void forEachDoubleValue(DicomElement dcmElm, DoubleConsumer action) {}

    default OptionalInt intValue(DicomInput input, long valuePos, int valueLen, int index) {
        return OptionalInt.empty();
    }

    default int[] intValues(DicomInput input, long valuePos, int valueLen) {
        return DicomElement.EMPTY_INTS;
    }

    default OptionalInt intValue(byte[] value, int index) {
        return OptionalInt.empty();
    }

    default int[] intValues(byte[] value) {
        return DicomElement.EMPTY_INTS;
    }

    default OptionalInt intValue(String value, int index) {
        return OptionalInt.empty();
    }

    default int[] intValues(String value) {
        return DicomElement.EMPTY_INTS;
    }

    default OptionalFloat floatValue(DicomInput input, long valpos, int vallen, int index) {
        return OptionalFloat.empty();
    }

    default float[] floatValues(DicomInput input, long valpos, int vallen) {
        return DicomElement.EMPTY_FLOATS;
    }

    default OptionalFloat floatValue(byte[] value, int index) {
        return OptionalFloat.empty();
    }

    default float[] floatValues(byte[] value) {
        return DicomElement.EMPTY_FLOATS;
    }

    default OptionalFloat floatValue(String value, int index) {
        return OptionalFloat.empty();
    }

    default float[] floatValues(String value) {
        return DicomElement.EMPTY_FLOATS;
    }

    default OptionalDouble doubleValue(DicomInput input, long valpos, int vallen, int index) {
        return OptionalDouble.empty();
    }

    default double[] doubleValues(DicomInput input, long valpos, int vallen) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default OptionalDouble doubleValue(byte[] value, int index) {
        return OptionalDouble.empty();
    }

    default double[] doubleValues(byte[] value) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default OptionalDouble doubleValue(String value, int index) {
        return OptionalDouble.empty();
    }

    default double[] doubleValues(String value) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default ToggleByteOrder toggleByteOrder() {
        return null;
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr) {
        return new DicomElementImpl(dcmObj, tag, vr);
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, byte[] val) {
        throw new UnsupportedOperationException();
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, int[] vals) {
        throw new UnsupportedOperationException();
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, float[] vals) {
        throw new UnsupportedOperationException();
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, double[] vals) {
        throw new UnsupportedOperationException();
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String val) {
        throw new UnsupportedOperationException();
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String[] vals) {
        throw new UnsupportedOperationException();
    }
}

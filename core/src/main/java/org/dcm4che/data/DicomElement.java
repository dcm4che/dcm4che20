package org.dcm4che.data;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public interface DicomElement {
    String[] EMPTY_STRINGS = {};
    int[] EMPTY_INTS = {};
    float[] EMPTY_FLOATS = {};
    double[] EMPTY_DOUBLES = {};

    int tag();

    VR vr();

    DicomObject containedBy();

    int valueLength();

    default int valueLength(DicomOutputStream dos) { return valueLength(); }

    default boolean isEmpty() { return valueLength() == 0; }

    void writeValueTo(DicomOutputStream dos) throws IOException;

    default String stringValue(int index, String defaultValue) { return defaultValue; }

    default String[] stringValues() { return EMPTY_STRINGS; }

    default Iterator<String> iterateStringValues() {
        return vr().type.iterateStringValues(this);
    }

    default int intValue(int index, int defaultValue) { return defaultValue; }

    default int[] intValues() { return EMPTY_INTS; }

    default float floatValue(int index, float defaultValue) { return defaultValue; }

    default float[] floatValues() { return EMPTY_FLOATS; }

    default double doubleValue(int index, double defaultValue) { return defaultValue; }

    default double[] doubleValues() { return EMPTY_DOUBLES; }

    default void trimToSize() {}

    default void purgeEncodedValue() {}

    default long getStreamPosition() { return -1L; }

    StringBuilder promptTo(StringBuilder appendTo, int maxLength);
}

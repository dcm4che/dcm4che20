package org.dcm4che.data;

import org.dcm4che.util.OptionalFloat;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

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

    default Optional<String> stringValue(int index) { return Optional.empty(); }

    default String[] stringValues() { return EMPTY_STRINGS; }

    default OptionalInt intValue(int index) { return OptionalInt.empty(); }

    default int[] intValues() { return EMPTY_INTS; }

    default OptionalFloat floatValue(int index) { return OptionalFloat.empty(); }

    default float[] floatValues() { return EMPTY_FLOATS; }

    default OptionalDouble doubleValue(int index) { return OptionalDouble.empty(); }

    default double[] doubleValues() { return EMPTY_DOUBLES; }

    default void forEachStringValue(StringValueConsumer action) {
        vr().type.forEachStringValue(this, action);
    }

    default void forEachIntValue(IntConsumer action) {
        vr().type.forEachIntValue(this, action);
    }

    default void forEachDoubleValue(DoubleConsumer action) {
        vr().type.forEachDoubleValue(this, action);
    }

    default void trimToSize() {}

    default void purgeEncodedValue() {}

    default long getStreamPosition() { return -1L; }

    StringBuilder promptTo(StringBuilder appendTo, int maxLength);
}

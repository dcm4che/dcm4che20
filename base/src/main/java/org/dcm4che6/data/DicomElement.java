package org.dcm4che6.data;

import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.OptionalFloat;
import org.dcm4che6.util.function.ItemConsumer;
import org.dcm4che6.util.function.StringValueConsumer;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
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

    default int valueLength() { return 0; }

    default int valueLength(DicomOutputStream dos) { return valueLength(); }

    default boolean isEmpty() { return valueLength() == 0; }

    default int size() { return 0; }

    default void writeValueTo(DicomOutputStream dos) throws IOException {}

    default Optional<String> stringValue(int index) { return Optional.empty(); }

    default String[] stringValues() { return EMPTY_STRINGS; }

    default OptionalInt intValue(int index) { return OptionalInt.empty(); }

    default int[] intValues() { return EMPTY_INTS; }

    default OptionalFloat floatValue(int index) { return OptionalFloat.empty(); }

    default float[] floatValues() { return EMPTY_FLOATS; }

    default OptionalDouble doubleValue(int index) { return OptionalDouble.empty(); }

    default double[] doubleValues() { return EMPTY_DOUBLES; }

    default <E extends Throwable> void forEachStringValue(StringValueConsumer<E> action) throws E {
        vr().type.forEachStringValue(this, action);
    }

    default void forEachIntValue(IntConsumer action) {
        vr().type.forEachIntValue(this, action);
    }

    default void forEachDoubleValue(DoubleConsumer action) {
        vr().type.forEachDoubleValue(this, action);
    }

    default <E extends Throwable> void forEachItem(ItemConsumer<E> action) throws E {}

    default DicomObject addItem(DicomObject item) {
        throw new UnsupportedOperationException();
    }

    default DicomObject getItem(int index) {
        return null;
    }

    default Stream<DicomObject> itemStream() {
        return Stream.empty();
    }

    default void purgeParsedItems() {}

    default void addDataFragment(DataFragment item) {
        throw new UnsupportedOperationException();
    }

    default DataFragment getDataFragment(int index) {
        return null;
    }

    default Stream<DataFragment> fragmentStream() {
        return Stream.empty();
    }

    default String bulkDataURI() {
        return null;
    }

    default String bulkDataUUID() {
        return null;
    }

    default void trimToSize() {}

    default void purgeEncodedValue() {}

    default long getStreamPosition() { return -1L; }

    StringBuilder promptTo(StringBuilder appendTo, int maxLength);

    int promptItemsTo(StringBuilder appendTo, int maxWidth, int maxLines);
}

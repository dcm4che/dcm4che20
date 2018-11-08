package org.dcm4che.data;

import org.dcm4che.io.ToggleByteOrder;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
interface VRType {
    default String delimiters() {
        throw new UnsupportedOperationException();
    }

    default String stringValue(DicomInput input, long valuePos, int valueLen, int index, SpecificCharacterSet cs,
                               String defaultValue) {
        return defaultValue;
    }

    default String[] stringValues(DicomInput input, long valuePos, int valueLen, SpecificCharacterSet cs) {
        return DicomElement.EMPTY_STRINGS;
    }

    default String stringValue(byte[] value, int index, String defaultValue) {
        return defaultValue;
    }

    default String[] stringValues(byte[] value) {
        return DicomElement.EMPTY_STRINGS;
    }

    default String stringValue(String value, int index, String defaultValue) {
        return defaultValue;
    }

    default String[] stringValues(String value) {
        return DicomElement.EMPTY_STRINGS;
    }

    default int intValue(DicomInput input, long valuePos, int valueLen, int index, int defaultValue) {
        return defaultValue;
    }

    default int[] intValues(DicomInput input, long valuePos, int valueLen) {
        return DicomElement.EMPTY_INTS;
    }

    default int intValue(byte[] value, int index, int defaultValue) {
        return defaultValue;
    }

    default int[] intValues(byte[] value) {
        return DicomElement.EMPTY_INTS;
    }

    default int intValue(String value, int index, int defaultValue) {
        return defaultValue;
    }

    default int[] intValues(String value) {
        return DicomElement.EMPTY_INTS;
    }

    default float floatValue(DicomInput input, long valpos, int vallen, int index, float defaultValue) {
        return defaultValue;
    }

    default float[] floatValues(DicomInput input, long valpos, int vallen) {
        return DicomElement.EMPTY_FLOATS;
    }

    default float floatValue(byte[] value, int index, float defaultValue) {
        return defaultValue;
    }

    default float[] floatValues(byte[] value) {
        return DicomElement.EMPTY_FLOATS;
    }

    default float floatValue(String value, int index, float defaultValue) {
        return defaultValue;
    }

    default float[] floatValues(String value) {
        return DicomElement.EMPTY_FLOATS;
    }

    default double doubleValue(DicomInput input, long valpos, int vallen, int index, double defaultValue) {
        return defaultValue;
    }

    default double[] doubleValues(DicomInput input, long valpos, int vallen) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default double doubleValue(byte[] value, int index, double defaultValue) {
        return defaultValue;
    }

    default double[] doubleValues(byte[] value) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default double doubleValue(String value, int index, double defaultValue) {
        return defaultValue;
    }

    default double[] doubleValues(String value) {
        return DicomElement.EMPTY_DOUBLES;
    }

    default ToggleByteOrder toggleByteOrder() {
        return null;
    }

    default DicomElement elementOf(DicomObject dcmObj, int tag, VR vr) {
        return new BaseDicomElement(dcmObj, tag, vr);
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

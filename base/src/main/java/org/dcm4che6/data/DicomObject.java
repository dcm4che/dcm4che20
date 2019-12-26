package org.dcm4che6.data;

import org.dcm4che6.internal.DicomObjectImpl;
import org.dcm4che6.util.OptionalFloat;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
public interface DicomObject extends Iterable<DicomElement> {

    static DicomObject newDicomObject() {
        return new DicomObjectImpl();
    }

    static DicomObject createFileMetaInformation(String cuid, String iuid, String tsuid) {
        if (iuid == null || iuid.isEmpty())
            throw new IllegalArgumentException("Missing SOP Instance UID");
        if (cuid == null || cuid.isEmpty())
            throw new IllegalArgumentException("Missing SOP Class UID");
        if (tsuid == null || tsuid.isEmpty())
            throw new IllegalArgumentException("Missing Transfer Syntax UID");

        DicomObjectImpl fmi = new DicomObjectImpl();
        fmi.setBytes(Tag.FileMetaInformationVersion, VR.OB, new byte[]{0, 1});
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, cuid);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, iuid);
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, tsuid);
        fmi.setString(Tag.ImplementationClassUID, VR.UI, Implementation.CLASS_UID);
        fmi.setString(Tag.ImplementationVersionName, VR.SH, Implementation.VERSION_NAME);
        return fmi;
    }

    long getStreamPosition();

    int getItemLength();

    Optional<DicomElement> containedBy();

    Optional<DicomObject> getParent();

    boolean hasParent();

    int nestingLevel();

    boolean isEmpty();

    int size();

    Stream<DicomElement> elementStream();

    void trimToSize();

    void purgeEncodedValues();

    void purgeElements();

    SpecificCharacterSet specificCharacterSet();

    String toString(int maxWidth, int maxLines);

    Optional<DicomElement> get(String privateCreator, int tag);

    Optional<DicomElement> get(int tag);

    Optional<String> getString(int tag);

    String getStringOrElseThrow(int tag);

    Optional<String> getString(int tag, int index);

    Optional<String> getString(String privateCreator, int tag);

    Optional<String> getString(String privateCreator, int tag, int index);

    Optional<String[]> getStrings(int tag);

    Optional<String[]> getStrings(String privateCreator, int tag);

    OptionalInt getInt(int tag);

    int getIntOrElseThrow(int tag);

    OptionalInt getInt(int tag, int index);

    OptionalInt getInt(String privateCreator, int tag);

    OptionalInt getInt(String privateCreator, int tag, int index);

    Optional<int[]> getInts(int tag);

    Optional<int[]> getInts(String privateCreator, int tag);

    OptionalFloat getFloat(int tag);

    float getFloatOrElseThrow(int tag);

    OptionalFloat getFloat(int tag, int index);

    OptionalFloat getFloat(String privateCreator, int tag);

    OptionalFloat getFloat(String privateCreator, int tag, int index);

    Optional<float[]> getFloats(int tag);

    Optional<float[]> getFloats(String privateCreator, int tag);

    OptionalDouble getDouble(int tag);

    double getDoubleOrElseThrow(int tag);

    OptionalDouble getDouble(int tag, int index);

    OptionalDouble getDouble(String privateCreator, int tag);

    OptionalDouble getDouble(String privateCreator, int tag, int index);

    Optional<double[]> getDoubles(int tag);

    Optional<double[]> getDoubles(String privateCreator, int tag);

    DicomElement add(DicomElement el);

    DicomElement setNull(int tag, VR vr);

    DicomElement setNull(String privateCreator, int tag, VR vr);

    DicomElement setBytes(int tag, VR vr, byte[] val);

    DicomElement setBytes(String privateCreator, int tag, VR vr, byte[] val);

    DicomElement setInt(int tag, VR vr, int... vals);

    DicomElement setInt(String privateCreator, int tag, VR vr, int... vals);

    DicomElement setFloat(int tag, VR vr, float... vals);

    DicomElement setFloat(String privateCreator, int tag, VR vr, float... vals);

    DicomElement setDouble(int tag, VR vr, double... vals);

    DicomElement setDouble(String privateCreator, int tag, VR vr, double... vals);

    DicomElement setString(int tag, VR vr, String val);

    DicomElement setString(int tag, VR vr, String... vals);

    DicomElement setString(String privateCreator, int tag, VR vr, String val);

    DicomElement setString(String privateCreator, int tag, VR vr, String... vals);

    DicomElement setBulkData(int tag, VR vr, String uri, String uuid);

    DicomElement setBulkData(String privateCreator, int tag, VR vr, String uri, String uuid);

    DicomElement newDicomSequence(int tag);

    DicomElement newDicomSequence(String privateCreator, int tag);

    Optional<String> getPrivateCreator(int tag);

    StringBuilder appendNestingLevel(StringBuilder sb);

    DicomObject createFileMetaInformation(String tsuid);

}

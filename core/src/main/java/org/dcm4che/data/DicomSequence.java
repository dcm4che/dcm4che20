package org.dcm4che.data;

import org.dcm4che.io.DicomWriter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DicomSequence extends BaseDicomElement {
    private final ArrayList<DicomObject> items = new ArrayList<>();

    public DicomSequence(DicomObject dcmObj, int tag) {
        super(dcmObj, tag, VR.SQ);
    }

    @Override
    public void trimToSize() {
        items.trimToSize();
        items.forEach(DicomObject::trimToSize);
    }

    public void addItem(DicomObject item) {
        items.add(item);
    }

    public DicomObject getItem(int index) {
        return index < items.size() ? items.get(index) : null;
    }

    @Override
    public int calculateValueLength(DicomWriter writer) {
        return writer.getSequenceLengthEncoding().adjustLength.applyAsInt(
                items.isEmpty() ? 0 : items.stream().mapToInt(writer::calculateLengthOf).sum());
    }

    @Override
    public void writeTo(DicomWriter writer) throws IOException {
        boolean undefinedLength = writer.getSequenceLengthEncoding().undefined.test(items.size());
        writer.writeHeader(tag, vr, undefinedLength ? -1 : items.stream().mapToInt(writer::lengthOf).sum());
        for (DicomObject item : items) {
            item.writeItemTo(writer);
        }
        if (undefinedLength) {
            writer.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
        }
    }
}

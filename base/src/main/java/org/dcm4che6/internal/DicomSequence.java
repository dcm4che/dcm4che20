package org.dcm4che6.internal;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class DicomSequence extends DicomElementImpl {
    private final ArrayList<DicomObject> items = new ArrayList<>();
    private final long streamPosition;
    private final int valueLength;

    DicomSequence(DicomObject dcmObj, int tag) {
        this(dcmObj, tag, -1L, -1);
    }

    DicomSequence(DicomObject dcmObj, int tag, long streamPosition, int valueLength) {
        super(dcmObj, tag, VR.SQ);
        this.streamPosition = streamPosition;
        this.valueLength = valueLength;
    }

    @Override
    public void trimToSize() {
        items.trimToSize();
        items.forEach(DicomObject::trimToSize);
    }

    @Override
    public void addItem(DicomObject item) {
        items.add(((DicomObjectImpl) item).containedBy(this));
    }

    @Override
    public DicomObject getItem(int index) {
        return index < items.size() ? items.get(index) : null;
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int valueLength() {
        return valueLength;
    }

    int elementLength(DicomOutputStream dos) {
        return dos.getSequenceLengthEncoding().totalLength.applyAsInt(
                dos.getEncoding().headerLength(VR.SQ),
                itemStream().mapToInt(item ->
                            dos.getItemLengthEncoding().totalLength.applyAsInt(8,
                                    ((DicomObjectImpl) item).calculateItemLength(dos)))
                            .sum());
    }

    @Override
    public int valueLength(DicomOutputStream dos) {
        return dos.getSequenceLengthEncoding().undefined.test(size()) ? -1
                : itemStream().mapToInt(item ->
                        dos.getItemLengthEncoding().totalLength.applyAsInt(
                                8, ((DicomObjectImpl) item).calculatedItemLength()))
                    .sum();
    }

    @Override
    public long getStreamPosition() {
        return streamPosition;
    }

    @Override
    public Stream<DicomObject> itemStream() {
        return items.stream();
    }

    @Override
    public void purgeParsedItems() {
        items.forEach(DicomObject::purgeElements);
    }

    @Override
    public void purgeEncodedValue() {
        items.forEach(DicomObject::purgeEncodedValues);
    }

    @Override
    public void writeValueTo(DicomOutputStream dos) throws IOException {
        for (DicomObject item : items) {
            ((DicomObjectImpl) item).writeItemTo(dos);
        }
    }
}

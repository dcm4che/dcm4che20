package org.dcm4che.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DicomSequence extends BaseDicomElement implements Iterable<DicomObject> {
    private final ArrayList<DicomObject> items = new ArrayList<>();
    private long streamPosition = -1L;
    private int valueLength = 0;

    DicomSequence(DicomObject dcmObj, int tag) {
        super(dcmObj, tag, VR.SQ);
    }

    @Override
    public void trimToSize() {
        items.trimToSize();
        items.forEach(DicomObject::trimToSize);
    }

    public void addItem(DicomObject item) {
        items.add(item.containedBy(this));
    }

    public DicomObject getItem(int index) {
        return index < items.size() ? items.get(index) : null;
    }

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

    @Override
    public int valueLength(DicomOutputStream dos) {
        return dos.getSequenceLengthEncoding().undefined.test(size()) ? -1
                : itemStream().mapToInt(dos::lengthOf).sum();
    }

    DicomSequence valueLength(int valueLength) {
        this.valueLength = valueLength;
        return this;
    }

    DicomSequence streamPosition(long streamPosition) {
        this.streamPosition = streamPosition;
        return this;
    }

    @Override
    public long getStreamPosition() {
        return streamPosition;
    }

    @Override
    public Iterator<DicomObject> iterator() {
        return items.iterator();
    }

    public Stream<DicomObject> itemStream() {
        return items.stream();
    }

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
            dos.writeItem(item);
        }
    }
}

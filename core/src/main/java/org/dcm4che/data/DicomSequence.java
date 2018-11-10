package org.dcm4che.data;

import org.dcm4che.io.DicomWriter;

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

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public Iterator<DicomObject> iterator() {
        return items.iterator();
    }

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
    public void writeTo(DicomWriter writer) throws IOException {
        writer.writeSequence(this);
    }
}

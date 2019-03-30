package org.dcm4che.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DataFragments extends BaseDicomElement implements Iterable<DataFragment> {

    private final ArrayList<DataFragment> items = new ArrayList<>();
    private long streamPosition;

    DataFragments(DicomObject dicomObject, int tag, VR vr) {
        super(dicomObject, tag, vr);
    }

    DataFragments streamPosition(long streamPosition) {
        this.streamPosition = streamPosition;
        return this;
    }

    @Override
    public long getStreamPosition() {
        return streamPosition;
    }

    @Override
    public int valueLength() {
        return -1;
    }

    @Override
    public Iterator<DataFragment> iterator() {
        return items.iterator();
    }

    public Stream<DataFragment> fragmentStream() {
        return items.stream();
    }

    public int size() {
        return items.size();
    }

    @Override
    public void trimToSize() {
        items.trimToSize();
    }

    public void addDataFragment(DataFragment item) {
        items.add(item);
    }

    public DataFragment getDataFragment(int index) {
        return index < items.size() ? items.get(index) : null;
    }

    @Override
    public void writeValueTo(DicomOutputStream dos) throws IOException {
        for (DataFragment item : items) {
            dos.writeHeader(Tag.Item, VR.NONE, item.valueLength());
            item.writeTo(dos);
        }
    }
}

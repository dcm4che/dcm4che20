package org.dcm4che6.internal;

import org.dcm4che6.data.DataFragment;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
class DataFragments extends DicomElementImpl {

    private final ArrayList<DataFragment> items = new ArrayList<>();
    private final long streamPosition;

    DataFragments(DicomObject dicomObject, int tag, VR vr, long streamPosition) {
        super(dicomObject, tag, vr);
        this.streamPosition = streamPosition;
    }

    @Override
    public long getStreamPosition() {
        return streamPosition;
    }

    @Override
    public int valueLength() {
        return -1;
    }

    int elementLength(DicomOutputStream dos) {
        return fragmentStream().mapToInt(DataFragment::valueLength).sum() + size() * 8 + 20;
    }

    @Override
    public Stream<DataFragment> fragmentStream() {
        return items.stream();
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public void trimToSize() {
        items.trimToSize();
    }

    @Override
    public void addDataFragment(DataFragment item) {
        items.add(item);
    }

    @Override
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

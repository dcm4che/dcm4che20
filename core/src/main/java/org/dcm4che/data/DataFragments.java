package org.dcm4che.data;

import org.dcm4che.io.DicomWriter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DataFragments extends BaseDicomElement {
    private final ArrayList<DataFragment> items = new ArrayList<>();

    public DataFragments(DicomObject dicomObject, int tag, VR vr) {
        super(dicomObject, tag, vr);
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
    public int calculateValueLength(DicomWriter writer) {
        return items.stream().mapToInt(DataFragment::valueLength).sum() + items.size() * 8 + 8;
    }

    @Override
    public void writeTo(DicomWriter writer) throws IOException {
        writer.writeHeader(tag, vr, -1);
        for (DataFragment item : items) {
            writer.writeHeader(Tag.Item, VR.NONE, item.valueLength());
            item.writeTo(writer.getOutputStream());
        }
        writer.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
    }
}

package org.dcm4che.data;

import org.dcm4che.io.DicomReader;
import org.dcm4che.io.DicomWriter;
import org.dcm4che.util.TagUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomObject implements Iterable<DicomElement> {
    private DicomSequence dcmSeq;
    private volatile ArrayList<DicomElement> elements;
    private SpecificCharacterSet specificCharacterSet;
    private PrivateCreator privateCreator;
    private int itemLength;
    private DicomInput dicomInput;
    private long dicomInputPos;
    private int dicomInputLen;

    public DicomObject() {
        this(null);
    }

    public DicomObject(DicomSequence dcmSeq) {
        this.dcmSeq = dcmSeq;
        this.elements = new ArrayList<>();
    }

    DicomObject(DicomSequence dcmSeq, DicomInput dicomInput, long dicomInputPos, int dicomInputLen, boolean lazy) {
        this.dcmSeq = dcmSeq;
        this.dicomInput = dicomInput;
        this.dicomInputPos = dicomInputPos;
        this.dicomInputLen = dicomInputLen;
        if (!lazy)
            this.elements = new ArrayList<>();
    }

    public DicomSequence getDicomSequence() {
        return dcmSeq;
    }

    public DicomObject getParent() {
        return dcmSeq != null ? dcmSeq.getDicomObject() : null;
    }

    public boolean hasParent() {
        return dcmSeq != null;
    }

    public int nestingLevel() {
        int level = 0;
        for (DicomObject parent = getParent(); parent != null; parent = parent.getParent()) level++;
        return level;
    }

    public boolean isEmpty() {
        return elements().isEmpty();
    }

    public int size() {
        return elements().size();
    }

    @Override
    public Iterator<DicomElement> iterator() {
        return elements().iterator();
    }

    public Stream<DicomElement> stream() {
        return elements().stream();
    }

    public void trimToSize() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null) {
            elements.trimToSize();
            elements.forEach(DicomElement::trimToSize);
        }
    }

    public void purgeEncodedValues() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null)
            elements.forEach(DicomElement::purgeEncodedValue);
    }

    public void purgeParsedItems() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null)
            elements.forEach(DicomElement::purgeParsedItems);
    }

    void purgeElements() {
        elements = null;
    }

    ArrayList<DicomElement> elements() {
        ArrayList<DicomElement> localRef = this.elements;
        if (localRef == null) {
            synchronized (this) {
                localRef = this.elements;
                if (localRef == null) {
                    this.elements = localRef = new ArrayList<>();
                    try {
                        DicomReader.parse(this, dicomInput, dicomInputPos, dicomInputLen);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return localRef;
    }

    public SpecificCharacterSet specificCharacterSet() {
        return specificCharacterSet != null
                ? specificCharacterSet
                : dcmSeq != null
                ? dcmSeq.getDicomObject().specificCharacterSet()
                : SpecificCharacterSet.ASCII;
    }

    public DicomElement get(int tag) {
        int i = binarySearch(elements(), tag);
        if (i >= 0) {
            return elements.get(i);
        }
        return null;
    }

    public String getString(int tag) {
        return getString(tag, 0, null);
    }

    public String getString(int tag, String defaultValue) {
        return getString(tag, 0, defaultValue);
    }

    public String getString(int tag, int index, String defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.stringValue(index, defaultValue) : defaultValue;
    }

    public String[] getStrings(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.stringValues() : DicomElement.EMPTY_STRINGS;
    }

    public int getInt(int tag, int defaultValue) {
        return getInt(tag, 0, defaultValue);
    }

    public int getInt(int tag, int index, int defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.intValue(index, defaultValue) : defaultValue;
    }

    public int[] getInts(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.intValues() : DicomElement.EMPTY_INTS;
    }

    public float getFloat(int tag, float defaultValue) {
        return getFloat(tag, 0, defaultValue);
    }

    public float getFloat(int tag, int index, float defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.floatValue(index, defaultValue) : defaultValue;
    }

    public float[] getFloats(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.floatValues() : DicomElement.EMPTY_FLOATS;
    }

    public double getDouble(int tag, double defaultValue) {
        return getDouble(tag, 0, defaultValue);
    }

    public double getDouble(int tag, int index, double defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.doubleValue(index, defaultValue) : defaultValue;
    }

    public double[] getDoubles(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.doubleValues() : DicomElement.EMPTY_DOUBLES;
    }

    public DicomElement add(DicomElement el) {
        List<DicomElement> list = elements();
        if (list.isEmpty() || Integer.compareUnsigned(list.get(list.size()-1).tag(), el.tag()) < 0) {
            list.add(el);
            return null;
        }
        int i = binarySearch(list, el.tag());
        if (i < 0) {
            list.add(-(i + 1), el);
            return null;
        }
        return list.set(i, el);
    }

    public DicomElement setNull(int tag, VR vr) {
        return add(vr.type.elementOf(this, tag, vr));
    }

    public DicomElement setInt(int tag, VR vr, int... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setFloat(int tag, VR vr, float... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setDouble(int tag, VR vr, double... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setString(int tag, VR vr, String val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    public DicomElement setString(int tag, VR vr, String... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomSequence newDicomSequence(int tag) {
        DicomSequence seq = new DicomSequence(this, tag);
        add(seq);
        return seq;
    }

    private static int binarySearch(List<DicomElement> l, int tag) {
        if (l == null)
            return -1;

        int low = 0;
        int high = l.size()-1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = Integer.compareUnsigned(l.get(mid).tag(), tag);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // tag found
        }
        return -(low + 1);  // tag not found
    }

    public String getPrivateCreator(int tag) {
        return TagUtils.isPrivateTag(tag)
                ? privateCreator(TagUtils.creatorTagOf(tag)).value
                : null;
    }

    private PrivateCreator privateCreator(int tag) {
        if (privateCreator == null || privateCreator.tag != tag) {
            privateCreator = new PrivateCreator(tag, getString(tag));
        }
        return privateCreator;
    }

    private static class PrivateCreator {
        final int tag;
        final String value;

        private PrivateCreator(int tag, String value) {
            this.tag = tag;
            this.value = value;
        }

    }

    public void writeTo(DicomWriter writer) throws IOException {
        for (DicomElement element : elements) {
            if (writer.isIncludeGroupLength() || !TagUtils.isGroupLength(element.tag()))
                element.writeTo(writer);
        }
    }

    public void writeItemTo(DicomWriter dicomWriter) throws IOException {
        boolean undefinedLength = dicomWriter.getItemLengthEncoding().undefined.test(size());
        dicomWriter.writeHeader(Tag.Item, VR.NONE, undefinedLength ? -1 : itemLength);
        writeTo(dicomWriter);
        if (undefinedLength) {
            dicomWriter.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
        }
    }

    public int calculateItemLength(DicomWriter writer) {
        int len = 0;
        if (!elements.isEmpty()) {
            int groupLengthTag0 = groupLengthTag(0);
            if (writer.isIncludeGroupLength() && groupLengthTag0 != groupLengthTag(size() - 1)) {
                Map<Integer, Integer> groups = stream()
                        .collect(Collectors.groupingBy(
                                x -> TagUtils.groupNumber(x.tag()),
                                Collectors.filtering(x -> !TagUtils.isGroupLength(x.tag()),
                                        Collectors.summingInt(writer::calculateLengthOf))));
                for (Map.Entry<Integer, Integer> group : groups.entrySet()) {
                    int glen = group.getValue();
                    setInt(group.getKey() << 16, VR.UL, glen);
                    len += glen + 12;
                }
            } else {
                len = stream().filter(x -> !TagUtils.isGroupLength(x.tag()))
                        .collect(Collectors.summingInt(writer::calculateLengthOf));
                if (writer.isIncludeGroupLength()) {
                    setInt(groupLengthTag0, VR.UL, len);
                    len += 12;
                }
            }
        }
        return this.itemLength = len;
    }

    public int getItemLength() {
        return itemLength;
    }

    private int groupLengthTag(int index) {
        return TagUtils.groupLengthTagOf(elements.get(index).tag());
    }

}

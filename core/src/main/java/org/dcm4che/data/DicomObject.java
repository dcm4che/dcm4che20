package org.dcm4che.data;

import org.dcm4che.io.DicomEncoding;
import org.dcm4che.io.DicomReader;
import org.dcm4che.io.DicomWriter;
import org.dcm4che.util.TagUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomObject implements Iterable<DicomElement>, Externalizable {
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

    DicomObject(DicomSequence dcmSeq, DicomInput dicomInput, long dicomInputPos, int dicomInputLen,
                ArrayList<DicomElement> elements) {
        this.dcmSeq = dcmSeq;
        this.dicomInput = dicomInput;
        this.dicomInputPos = dicomInputPos;
        this.dicomInputLen = dicomInputLen;
        this.elements = elements;
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

    public Stream<DicomElement> elementStream() {
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

    public DicomElement firstElement() {
        return elements().get(0);
    }

    public DicomElement lastElement() {
        return elements().get(elements.size() - 1);
    }

    public DicomElement get(String privateCreator, int tag) {
        if (privateCreator != null && TagUtils.isPrivateGroup(tag)) {
            int creatorTag;
            if ((creatorTag = creatorTag(privateCreator, tag, false)) == 0) {
                return null;
            }
            tag = TagUtils.toPrivateTag(creatorTag, tag);
        }
        return get(tag);
    }

    private int creatorTag(String value, int tag, boolean reserve) {
        int gggg0000 = tag & 0xffff0000;
        if (privateCreator != null
                && ((privateCreator.tag & 0xffff0000) == gggg0000)
                && privateCreator.value.equals(value)) {
            return privateCreator.tag;
        }
        ArrayList<DicomElement> list = elements();
        int creatorTag = gggg0000 | 0x10;
        int i = binarySearch(list, creatorTag--);
        if (i < 0) i = -(i + 1);
        DicomElement el;
        while (i < list.size() && ((el = list.get(i)).tag() & 0xffffff00) == gggg0000) {
            creatorTag = el.tag();
            if (value.equals(el.stringValue(0, null))) {
                privateCreator = new PrivateCreator(creatorTag, value);
                return creatorTag;
            }
            i++;
        }
        if (!reserve)
            return 0;

        list.add(i, new StringElement(this, ++creatorTag, VR.LO, value));
        privateCreator = new PrivateCreator(creatorTag, value);
        return creatorTag;
    }

    public DicomElement get(int tag) {
        ArrayList<DicomElement> list = elements();
        int i = binarySearch(list, tag);
        if (i >= 0) {
            return list.get(i);
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

    public String getString(String privateCreator, int tag) {
        return getString(privateCreator, tag, 0, null);
    }

    public String getString(String privateCreator, int tag, String defaultValue) {
        return getString(privateCreator, tag, 0, defaultValue);
    }

    public String getString(String privateCreator, int tag, int index, String defaultValue) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.stringValue(index, defaultValue) : defaultValue;
    }

    public String[] getStrings(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.stringValues() : DicomElement.EMPTY_STRINGS;
    }

    public String[] getStrings(String privateCreator, int tag) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.stringValues() : DicomElement.EMPTY_STRINGS;
    }

    public int getInt(int tag, int defaultValue) {
        return getInt(tag, 0, defaultValue);
    }

    public int getInt(int tag, int index, int defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.intValue(index, defaultValue) : defaultValue;
    }

    public int getInt(String privateCreator, int tag, int defaultValue) {
        return getInt(privateCreator, tag, 0, defaultValue);
    }

    public int getInt(String privateCreator, int tag, int index, int defaultValue) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.intValue(index, defaultValue) : defaultValue;
    }

    public int[] getInts(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.intValues() : DicomElement.EMPTY_INTS;
    }

    public int[] getInts(String privateCreator, int tag) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.intValues() : DicomElement.EMPTY_INTS;
    }

    public float getFloat(int tag, float defaultValue) {
        return getFloat(tag, 0, defaultValue);
    }

    public float getFloat(int tag, int index, float defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.floatValue(index, defaultValue) : defaultValue;
    }

    public float getFloat(String privateCreator, int tag, float defaultValue) {
        return getFloat(privateCreator, tag, 0, defaultValue);
    }

    public float getFloat(String privateCreator, int tag, int index, float defaultValue) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.floatValue(index, defaultValue) : defaultValue;
    }

    public float[] getFloats(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.floatValues() : DicomElement.EMPTY_FLOATS;
    }

    public float[] getFloats(String privateCreator, int tag) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.floatValues() : DicomElement.EMPTY_FLOATS;
    }

    public double getDouble(int tag, double defaultValue) {
        return getDouble(tag, 0, defaultValue);
    }

    public double getDouble(int tag, int index, double defaultValue) {
        DicomElement el = get(tag);
        return el != null ? el.doubleValue(index, defaultValue) : defaultValue;
    }

    public double getDouble(String privateCreator, int tag, double defaultValue) {
        return getDouble(privateCreator, tag, 0, defaultValue);
    }

    public double getDouble(String privateCreator, int tag, int index, double defaultValue) {
        DicomElement el = get(privateCreator, tag);
        return el != null ? el.doubleValue(index, defaultValue) : defaultValue;
    }

    public double[] getDoubles(int tag) {
        DicomElement el = get(tag);
        return el != null ? el.doubleValues() : DicomElement.EMPTY_DOUBLES;
    }

    public double[] getDoubles(String privateCreator, int tag) {
        DicomElement el = get(privateCreator, tag);
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

    public DicomElement setNull(String privateCreator, int tag, VR vr) {
        return setNull(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr);
    }

    public DicomElement setBytes(int tag, VR vr, byte[] val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    public DicomElement setBytes(String privateCreator, int tag, VR vr, byte[] val) {
        return setBytes(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, val);
    }

    public DicomElement setInt(int tag, VR vr, int... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setInt(String privateCreator, int tag, VR vr, int... vals) {
        return setInt(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, vals);
    }

    public DicomElement setFloat(int tag, VR vr, float... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setFloat(String privateCreator, int tag, VR vr, float... vals) {
        return setFloat(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, vals);
    }

    public DicomElement setDouble(int tag, VR vr, double... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setDouble(String privateCreator, int tag, VR vr, double... vals) {
        return setDouble(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, vals);
    }

    public DicomElement setString(int tag, VR vr, String val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    public DicomElement setString(int tag, VR vr, String... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    public DicomElement setString(String privateCreator, int tag, VR vr, String val) {
        return setString(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, val);
    }

    public DicomElement setString(String privateCreator, int tag, VR vr, String... vals) {
        return setString(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, vals);
    }

    public DicomElement setBulkData(int tag, VR vr, String uri) {
        return add(new BulkDataElement(this, tag, vr, uri));
    }

    public DicomElement setBulkData(String privateCreator, int tag, VR vr, String uri) {
        return setBulkData(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag), vr, uri);
    }

    public DicomSequence newDicomSequence(int tag) {
        DicomSequence seq = new DicomSequence(this, tag);
        add(seq);
        return seq;
    }

    public DicomElement newDicomSequence(String privateCreator, int tag) {
        return newDicomSequence(TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true), tag));
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

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        DicomWriter writer = new DicomWriter(new OutputStream() {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void write(int i) throws IOException {
                out.write(i);
            }
        }).withEncoding(DicomEncoding.SERIALIZE);
        writer.writeDataSet(this);
        writer.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        new DicomReader(new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return in.read(b, off, len);
            }

            @Override
            public int read() throws IOException {
                return in.read();
            }
        }).withEncoding(DicomEncoding.SERIALIZE).readDataSet(this);
    }

    private static class PrivateCreator {
        final int tag;
        final String value;

        private PrivateCreator(int tag, String value) {
            this.tag = tag;
            this.value = value;
        }

    }

    public int getItemLength() {
        return itemLength;
    }

    public void setItemLength(int itemLength) {
        this.itemLength = itemLength;
    }

}

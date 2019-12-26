package org.dcm4che6.internal;

import org.dcm4che6.data.*;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.SpecificCharacterSet;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.OptionalFloat;
import org.dcm4che6.util.TagUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
public class DicomObjectImpl implements DicomObject, Externalizable {

    private static final int TO_STRING_LINES = 50;
    private static final int TO_STRING_WIDTH = 78;
    private final DicomInput dicomInput;
    private final long streamPosition;
    private final int itemLength;
    private volatile DicomSequence dcmSeq;
    private volatile ArrayList<DicomElement> elements;
    private volatile SpecificCharacterSet specificCharacterSet;
    private PrivateCreator lruPrivateCreator;
    private int calculatedItemLength;

    public DicomObjectImpl() {
        this(null, -1L, -1, new ArrayList<>());
    }

    DicomObjectImpl(DicomInput dicomInput, long streamPosition, int itemLength, ArrayList<DicomElement> elements) {
        this.dicomInput = dicomInput;
        this.streamPosition = streamPosition;
        this.itemLength = itemLength;
        this.elements = elements;
    }

    @Override
    public long getStreamPosition() {
        return streamPosition;
    }

    @Override
    public int getItemLength() {
        return itemLength;
    }

    public DicomObject containedBy(DicomSequence dcmSeq) {
        if (this.dcmSeq != null && dcmSeq != null)
            throw new IllegalStateException("Item already contained by " + dcmSeq);

        this.dcmSeq = dcmSeq;
        return this;
    }

    @Override
    public Optional<DicomElement> containedBy() {
        return Optional.ofNullable(dcmSeq);
    }

    @Override
    public Optional<DicomObject> getParent() {
        return dcmSeq != null ? Optional.of(dcmSeq.containedBy()) : Optional.empty();
    }

    @Override
    public boolean hasParent() {
        return dcmSeq != null;
    }

    @Override
    public int nestingLevel() {
        return dcmSeq != null ? dcmSeq.containedBy().nestingLevel() + 1 : 0;
    }

    @Override
    public boolean isEmpty() {
        return elements().isEmpty();
    }

    @Override
    public int size() {
        return elements().size();
    }

    @Override
    public Iterator<DicomElement> iterator() {
        return elements().iterator();
    }

    @Override
    public Stream<DicomElement> elementStream() {
        return elements().stream();
    }

    @Override
    public void trimToSize() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null) {
            elements.trimToSize();
            elements.forEach(DicomElement::trimToSize);
        }
    }

    @Override
    public void purgeEncodedValues() {
        ArrayList<DicomElement> elements = this.elements;
        if (elements != null)
            elements.forEach(DicomElement::purgeEncodedValue);
    }

    @Override
    public void purgeElements() {
        elements = null;
    }

    ArrayList<DicomElement> elements() {
        ArrayList<DicomElement> localRef = elements;
        if (localRef == null)
            synchronized (this) {
                if ((localRef = elements) != null)
                    return localRef;
                try {
                    elements = localRef = new ArrayList();
                    DicomParser.parse(this, dicomInput, streamPosition, itemLength);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        return localRef;
    }

    @Override
    public SpecificCharacterSet specificCharacterSet() {
        return specificCharacterSet != null
                ? specificCharacterSet
                : (specificCharacterSet = dcmSeq != null
                    ? dcmSeq.containedBy().specificCharacterSet()
                    : SpecificCharacterSet.getDefaultCharacterSet());
    }

    @Override
    public String toString() {
        return toString(TO_STRING_WIDTH, TO_STRING_LINES);
    }

    @Override
    public String toString(int maxWidth, int maxLines) {
        StringBuilder appendTo = new StringBuilder(512);
        if (promptTo(appendTo, maxWidth, maxLines) < 0)
            appendTo.append("...").append(System.lineSeparator());
        return appendTo.toString();
    }

    int promptTo(StringBuilder appendTo, int maxWidth, int maxLines) {
        Iterator<DicomElement> iter = iterator();
        while (iter.hasNext() && maxLines-- > 0) {
            int maxLength = appendTo.length() + maxWidth;
            DicomElement dcmElm = iter.next();
            dcmElm.promptTo(appendTo, maxLength).append(System.lineSeparator());
            maxLines = dcmElm.promptItemsTo(appendTo, maxWidth, maxLines);
        }
        return maxLines;
    }

    private DicomElement firstElement() {
        ArrayList<DicomElement> elements = elements();
        if (elements.isEmpty())
            throw new NoSuchElementException();

        return elements.get(0);
    }

    private DicomElement lastElement() {
        ArrayList<DicomElement> elements = elements();
        int size = elements.size();
        if (size == 0)
            throw new NoSuchElementException();

        return elements.get(size - 1);
    }

    @Override
    public Optional<DicomElement> get(String privateCreator, int tag) {
        return privateCreator != null && TagUtils.isPrivateGroup(tag)
                ? get(creatorTag(privateCreator, tag, false), tag)
                : get(tag);
    }

    private Optional<DicomElement> get(OptionalInt creatorTag, int tag) {
        return creatorTag.isPresent()
                ? get(TagUtils.toPrivateTag(creatorTag.getAsInt(), tag))
                : Optional.empty();
    }

    private int creatorTag(String privateCreator, int tag) {
        return privateCreator != null && TagUtils.isPrivateGroup(tag)
                ? TagUtils.toPrivateTag(creatorTag(privateCreator, tag, true).getAsInt(), tag)
                : tag;
    }

    private OptionalInt creatorTag(String value, int tag, boolean reserve) {
        int gggg0000 = tag & 0xffff0000;
        PrivateCreator privateCreator = lruPrivateCreator;
        if (privateCreator != null && privateCreator.match(value, gggg0000)
                && ((privateCreator.tag & 0xffff0000) == gggg0000)
                && lruPrivateCreator.value.equals(value)) {
            return OptionalInt.of(privateCreator.tag);
        }
        ArrayList<DicomElement> list = elements();
        int creatorTag = gggg0000 | 0x10;
        int i = binarySearch(list, creatorTag--);
        if (i < 0)
            i = -(i + 1);

        DicomElement el;
        while (i < list.size() && ((el = list.get(i)).tag() & 0xffffff00) == gggg0000) {
            creatorTag = el.tag();
            if (value.equals(el.stringValue(0).orElse(null))) {
                lruPrivateCreator = new PrivateCreator(el.tag(), el.stringValue(0));
                return OptionalInt.of(creatorTag);
            }
            i++;
        }
        if (!reserve)
            return OptionalInt.empty();

        list.add(i, new StringElement(this, ++creatorTag, VR.LO, value));
        lruPrivateCreator = new PrivateCreator(creatorTag, Optional.of(value));
        return OptionalInt.of(creatorTag);
    }

    @Override
    public Optional<DicomElement> get(int tag) {
        ArrayList<DicomElement> list = elements();
        int i = binarySearch(list, tag);
        if (i >= 0) {
            return Optional.of(list.get(i));
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getString(int tag) {
        return getString(tag, 0);
    }

    @Override
    public String getStringOrElseThrow(int tag) {
        return getString(tag).orElseThrow(() -> missing(tag));
    }

    @Override
    public Optional<String> getString(int tag, int index) {
        return get(tag).flatMap(el -> el.stringValue(index));
    }

    @Override
    public Optional<String> getString(String privateCreator, int tag) {
        return getString(privateCreator, tag, 0);
    }

    @Override
    public Optional<String> getString(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).flatMap(el -> el.stringValue(index));
    }

    @Override
    public Optional<String[]> getStrings(int tag) {
        return get(tag).map(DicomElement::stringValues);
    }

    @Override
    public Optional<String[]> getStrings(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::stringValues);
    }

    @Override
    public OptionalInt getInt(int tag) {
        return getInt(tag, 0);
    }

    @Override
    public int getIntOrElseThrow(int tag) {
        return getInt(tag).orElseThrow(() -> missing(tag));
    }

    @Override
    public OptionalInt getInt(int tag, int index) {
        return get(tag).map(el -> el.intValue(index)).orElse(OptionalInt.empty());
    }

    @Override
    public OptionalInt getInt(String privateCreator, int tag) {
        return getInt(privateCreator, tag, 0);
    }

    @Override
    public OptionalInt getInt(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.intValue(index)).orElse(OptionalInt.empty());
    }

    @Override
    public Optional<int[]> getInts(int tag) {
        return get(tag).map(DicomElement::intValues);
    }

    @Override
    public Optional<int[]> getInts(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::intValues);
    }

    @Override
    public OptionalFloat getFloat(int tag) {
        return getFloat(tag, 0);
    }

    @Override
    public float getFloatOrElseThrow(int tag) {
        return getFloat(tag).orElseThrow(() -> missing(tag));
    }

    @Override
    public OptionalFloat getFloat(int tag, int index) {
        return get(tag).map(el -> el.floatValue(index)).orElse(OptionalFloat.empty());
    }

    @Override
    public OptionalFloat getFloat(String privateCreator, int tag) {
        return getFloat(privateCreator, tag, 0);
    }

    @Override
    public OptionalFloat getFloat(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.floatValue(index)).orElse(OptionalFloat.empty());
    }

    @Override
    public Optional<float[]> getFloats(int tag) {
        return get(tag).map(DicomElement::floatValues);
    }

    @Override
    public Optional<float[]> getFloats(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::floatValues);
    }

    @Override
    public OptionalDouble getDouble(int tag) {
        return getDouble(tag, 0);
    }

    @Override
    public OptionalDouble getDouble(int tag, int index) {
        return get(tag).map(el -> el.doubleValue(index)).orElse(OptionalDouble.empty());
    }

    @Override
    public double getDoubleOrElseThrow(int tag) {
        return getDouble(tag, 0).orElseThrow(() -> missing(tag));
    }

    @Override
    public OptionalDouble getDouble(String privateCreator, int tag) {
        return getDouble(privateCreator, tag, 0);
    }

    @Override
    public OptionalDouble getDouble(String privateCreator, int tag, int index) {
        return get(privateCreator, tag).map(el -> el.doubleValue(index)).orElse(OptionalDouble.empty());
    }

    @Override
    public Optional<double[]> getDoubles(int tag) {
        return get(tag).map(DicomElement::doubleValues);
    }

    @Override
    public Optional<double[]> getDoubles(String privateCreator, int tag) {
        return get(privateCreator, tag).map(DicomElement::doubleValues);
    }

    @Override
    public DicomElement add(DicomElement el) {
        if (el.tag() == Tag.SpecificCharacterSet)
            specificCharacterSet = SpecificCharacterSet.valueOf(el.stringValues());

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

    @Override
    public DicomElement setNull(int tag, VR vr) {
        if (tag == Tag.SpecificCharacterSet)
            specificCharacterSet = SpecificCharacterSet.getDefaultCharacterSet();

        return add(vr.type.elementOf(this, tag, vr));
    }

    @Override
    public DicomElement setNull(String privateCreator, int tag, VR vr) {
        return setNull(creatorTag(privateCreator, tag), vr);
    }

    @Override
    public DicomElement setBytes(int tag, VR vr, byte[] val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    @Override
    public DicomElement setBytes(String privateCreator, int tag, VR vr, byte[] val) {
        return setBytes(creatorTag(privateCreator, tag), vr, val);
    }

    @Override
    public DicomElement setInt(int tag, VR vr, int... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    @Override
    public DicomElement setInt(String privateCreator, int tag, VR vr, int... vals) {
        return setInt(creatorTag(privateCreator, tag), vr, vals);
    }

    @Override
    public DicomElement setFloat(int tag, VR vr, float... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    @Override
    public DicomElement setFloat(String privateCreator, int tag, VR vr, float... vals) {
        return setFloat(creatorTag(privateCreator, tag), vr, vals);
    }

    @Override
    public DicomElement setDouble(int tag, VR vr, double... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    @Override
    public DicomElement setDouble(String privateCreator, int tag, VR vr, double... vals) {
        return setDouble(creatorTag(privateCreator, tag), vr, vals);
    }

    @Override
    public DicomElement setString(int tag, VR vr, String val) {
        return add(vr.type.elementOf(this, tag, vr, val));
    }

    @Override
    public DicomElement setString(int tag, VR vr, String... vals) {
        return add(vr.type.elementOf(this, tag, vr, vals));
    }

    @Override
    public DicomElement setString(String privateCreator, int tag, VR vr, String val) {
        return setString(creatorTag(privateCreator, tag), vr, val);
    }

    @Override
    public DicomElement setString(String privateCreator, int tag, VR vr, String... vals) {
        return setString(creatorTag(privateCreator, tag), vr, vals);
    }

    @Override
    public DicomElement setBulkData(int tag, VR vr, String uri, String uuid) {
        return add(new BulkDataElement(this, tag, vr, uri, uuid));
    }

    @Override
    public DicomElement setBulkData(String privateCreator, int tag, VR vr, String uri, String uuid) {
        return setBulkData(creatorTag(privateCreator, tag), vr, uri, uuid);
    }

    @Override
    public DicomElement newDicomSequence(int tag) {
        DicomSequence seq = new DicomSequence(this, tag);
        add(seq);
        return seq;
    }

    @Override
    public DicomElement newDicomSequence(String privateCreator, int tag) {
        return newDicomSequence(creatorTag(privateCreator, tag));
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

    @Override
    public Optional<String> getPrivateCreator(int tag) {
        return TagUtils.isPrivateTag(tag)
                ? privateCreator(TagUtils.creatorTagOf(tag)).value
                : Optional.empty();
    }

    private PrivateCreator privateCreator(int tag) {
        PrivateCreator privateCreator = lruPrivateCreator;
        if (privateCreator == null || privateCreator.tag != tag) {
            lruPrivateCreator = privateCreator = new PrivateCreator(tag, getString(tag));
        }
        return privateCreator;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        DicomOutputStream writer = new DicomOutputStream(new OutputStream() {
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
    public void readExternal(ObjectInput in) throws IOException {
        new DicomInputStream(new InputStream() {
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

    @Override
    public StringBuilder appendNestingLevel(StringBuilder sb) {
        if (dcmSeq != null)
            dcmSeq.containedBy().appendNestingLevel(sb).append('>');
        return sb;
    }

    @Override
    public DicomObject createFileMetaInformation(String tsuid) {
        return DicomObject.createFileMetaInformation(
                getStringOrElseThrow(Tag.SOPClassUID), getStringOrElseThrow(Tag.SOPInstanceUID),
                tsuid);
    }

    int calculatedItemLength() {
        return calculatedItemLength;
    }

    public int calculateItemLength(DicomOutputStream dos) {
        int len = 0;
        if (!isEmpty()) {
            int groupLengthTag = TagUtils.groupLengthTagOf(firstElement().tag());
            Collector<DicomElement, ?, Integer> summingElementLengths =
                    Collectors.summingInt(el -> ((DicomElementImpl) el).elementLength(dos));
            if (dos.isIncludeGroupLength() && groupLengthTag != TagUtils.groupLengthTagOf(lastElement().tag())) {
                Map<Integer, Integer> groups = elementStream()
                        .collect(Collectors.groupingBy(
                                x -> TagUtils.groupNumber(x.tag()),
                                Collectors.filtering(x -> !TagUtils.isGroupLength(x.tag()), summingElementLengths)));
                for (Map.Entry<Integer, Integer> group : groups.entrySet()) {
                    int glen = group.getValue();
                    setInt(group.getKey() << 16, VR.UL, glen);
                    len += glen + 12;
                }
            } else {
                len = elementStream().filter(x -> !TagUtils.isGroupLength(x.tag())).collect(summingElementLengths);
                if (dos.isIncludeGroupLength()) {
                    setInt(groupLengthTag, VR.UL, len);
                    len += 12;
                }
            }
        }
        this.calculatedItemLength = len;
        return len;
    }

    public void writeTo(DicomOutputStream dos) throws IOException {
        for (DicomElement element : elements()) {
            int tag = element.tag();
            if (dos.isIncludeGroupLength() || !TagUtils.isGroupLength(tag)) {
                int valueLength = element.valueLength(dos);
                dos.writeHeader(tag, element.vr(), valueLength);
                element.writeValueTo(dos);
                if (valueLength == -1) {
                    dos.writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
                }
            }
        }
    }

    void writeItemTo(DicomOutputStream dos) throws IOException {
        boolean undefinedLength = dos.getItemLengthEncoding().undefined.test(size());
        dos.writeHeader(Tag.Item, VR.NONE, undefinedLength ? -1 : calculatedItemLength);
        writeTo(dos);
        if (undefinedLength) {
            dos.writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
        }
    }

    private static class PrivateCreator {
        final int tag;
        final Optional<String> value;

        PrivateCreator(int tag, Optional<String> value) {
            this.tag = tag;
            this.value = value;
        }

        boolean match(String otherValue, int gggg0000) {
            return value.filter(otherValue::equals).isPresent()
                    && (tag & 0xffff0000) == gggg0000;
        }
    }

    private static NoSuchElementException missing(int tag) {
        return new NoSuchElementException("Missing "
                + StandardElementDictionary.INSTANCE.keywordOf(tag) + ' '
                + TagUtils.toString(tag));
    }
}

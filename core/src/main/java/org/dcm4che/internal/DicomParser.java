package org.dcm4che.internal;

import org.dcm4che.data.*;
import org.dcm4che.io.*;
import org.dcm4che.util.TagUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomParser implements DicomInputHandler {
    private final MemoryCache cache;
    private DicomInputStream dis;
    private InputStream in;
    private DicomInput input;
    private int limit = -1;
    private long pos;
    private int tag;
    private int vrCode;
    private VR vr;
    private int headerLength;
    private int valueLength;
    private DicomInputHandler handler = this;
    private Predicate<DicomElement> parseItemsPredicate = x -> true;
    private Predicate<DicomElement> bulkDataPredicate = x -> false;
    private Function<DicomInputStream, String> bulkDataURIProducer;
    private Supplier<Path> bulkDataSpoolPathSupplier;
    private Path bulkDataSpoolPath;
    private OutputStream bulkDataSpoolStream;
    private long bulkDataSpoolStreamPos;

    public DicomParser(DicomInputStream dis, InputStream in) {
        this.cache = new MemoryCache();
        this.dis = dis;
        this.in = in;
    }

    private DicomParser(DicomInput input, long pos) {
        this.input = input;
        this.pos = pos;
        this.cache = input.cache;
    }

    static void parse(DicomObject dcmObj, DicomInput input, long pos, int length) throws IOException {
        new DicomParser(input, pos).parse(dcmObj, length);
    }

    public DicomEncoding getEncoding() {
        return input != null ? input.encoding : null;
    }

    public void setEncoding(DicomEncoding encoding) throws IOException {
        input = new DicomInput(cache, encoding);
        if (input.encoding.deflated) {
            in = cache.inflate(pos, in);
        }
    }

    public void setLimit(int limit) {
        if (limit <= 0)
            throw new IllegalArgumentException("limit: " + limit);

        this.limit = limit;
    }

    public void setParseItems(Predicate<DicomElement> parseItemsPredicate) {
        this.parseItemsPredicate = Objects.requireNonNull(parseItemsPredicate);
    }

    public void setBulkData(Predicate<DicomElement> bulkDataPredicate) {
        this.bulkDataPredicate = Objects.requireNonNull(bulkDataPredicate);
    }

    public void setBulkDataURIProducer(Function<DicomInputStream, String> bulkDataURIProducer) {
        this.bulkDataURIProducer = Objects.requireNonNull(bulkDataURIProducer);
    }

    public void setBulkDataSpoolPathSupplier(Supplier<Path> bulkDataSpoolPathSupplier) {
        this.bulkDataSpoolPathSupplier = Objects.requireNonNull(bulkDataSpoolPathSupplier);
    }

    public void setInputHandler(DicomInputHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    public DicomInputHandler getInputHandler() {
        return handler;
    }

    public long getStreamPosition() {
        return pos;
    }

    public int read() throws IOException {
        if (cache.loadFromStream(pos + 1, in) == pos)
            return -1;

        return cache.byteAt(pos++);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        int read = (int) (cache.loadFromStream(pos + len, in) - pos);
        if (read == 0)
            return -1;

        cache.copyBytesTo(pos, b, off, read);
        pos += read;
        return read;
    }

    public void close() throws IOException {
        try {
            if (bulkDataSpoolStream != null)
                bulkDataSpoolStream.close();
        } finally {
            if (in != null)
                in.close();
        }
    }

    public DicomObject readFileMetaInformation() throws IOException {
        if (pos != 0)
            throw new IllegalStateException("Stream position: " + pos);

        long read = cache.loadFromStream(132, in);
        byte[] b = cache.firstBlock();
        if (read != 132 || b[128] != 'D' || b[129] != 'I' || b[130] != 'C' || b[131] != 'M')
            return null;

        DicomObject dcmObj = new DicomObjectImpl();
        pos = 132;
        input = new DicomInput(cache, DicomEncoding.EVR_LE);
        readHeader(dcmObj, false);
        DicomElement groupLength = input.dicomElement(dcmObj, tag, vr, pos, valueLength);
        handler.startElement(dis, groupLength, false);
        handler.endElement(dis, groupLength, false);
        pos += valueLength;
        parse(dcmObj, groupLength.intValue(0).orElseThrow(
                () -> new DicomParseException("Missing Group Length in File Meta Information")));
        String tsuid = dcmObj.getString(Tag.TransferSyntaxUID).orElseThrow(
                () -> new DicomParseException("Missing Transfer Syntax UID in File Meta Information"));
        setEncoding(DicomEncoding.of(tsuid));
        return dcmObj;
    }

    public DicomObject readCommandSet() throws IOException {
        if (pos != 0)
            throw new IllegalStateException("Stream position: " + pos);

        if (input != null)
            throw new IllegalStateException("encoding already initialized: " + input.encoding);

        input = new DicomInput(cache, DicomEncoding.IVR_LE);
        DicomObject dcmObj = new DicomObjectImpl();
        parse(dcmObj, limit);
        return dcmObj;
    }

    public DicomObject readDataSet() throws IOException {
        DicomObject dcmObj = new DicomObjectImpl();
        readDataSet(dcmObj);
        return dcmObj;
    }

    public boolean readDataSet(DicomObject dcmObj) throws IOException {
        Objects.requireNonNull(dcmObj);
        if (input == null) {
            guessEncoding(dcmObj);
        }
        return parse(dcmObj, limit);
    }

    public StringBuilder promptFilePreambleTo(StringBuilder appendTo, int maxLength) {
        appendTo.append('[');
        if (BinaryVR.OB.appendValue(input, 0, 128, null, appendTo, maxLength).length() < maxLength)
            appendTo.append(']');
        return appendTo;
    }

    public StringBuilder promptTo(DicomElement dcmElm, StringBuilder appendTo, int maxLength)
            throws IOException {
        if (vr != VR.SQ && valueLength > 0) // ensure to read enough bytes for promptTo from input stream
            cache.loadFromStream(pos + Math.min(valueLength, maxLength << 1), in);
        dcmElm.promptTo(appendTo, maxLength);
        if (vr != VR.SQ && valueLength > 1024) // avoid cache allocation for large attribute values
            cache.skipBytes(pos, valueLength, in, null);
        return appendTo;
    }

    public StringBuilder promptTo(DataFragment dataFragment, StringBuilder appendTo, int maxLength)
            throws IOException {
        if (valueLength > 0) // ensure to read enough bytes for promptTo from input stream
            cache.loadFromStream(pos + Math.min(valueLength, maxLength), in);
        dataFragment.promptTo(appendTo, maxLength);
        if (valueLength > 0) // avoid cache allocation for data fragments
            skipBytes(-headerLength, headerLength + valueLength, null);
        return appendTo;
    }

    public void loadValueFromStream() throws IOException {
        cache.loadFromStream(pos + valueLength, in);
    }

    public void writeValueTo(DicomElement dcmElm, DicomOutputStream dos) throws IOException {
        cache.loadFromStream(pos + valueLength, in);
        dcmElm.writeValueTo(dos);
    }

    private void guessEncoding(DicomObject dcmObj) throws IOException {
        if (readFileMetaInformation() == null) {
            input = new DicomInput(cache, !probeExplicitVR(4)
                    ? DicomEncoding.IVR_LE
                    : (cache.byteAt(1) == 0
                        ? DicomEncoding.EVR_LE
                        : DicomEncoding.EVR_BE));
            readHeader0(dcmObj);
            if (valueLength > 64)
                throw new DicomParseException("Not a DICOM stream");
        }
    }

    private boolean probeExplicitVR(long pos) {
        try {
            VR.of(cache.vrcode(pos));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean readHeader(DicomObject dcmObj, boolean expectEOF) throws IOException {
        if (cache.loadFromStream(pos + 12, in) == pos && expectEOF)
            return false;

        this.headerLength = readHeader0(dcmObj);
        this.pos += headerLength;
        return true;
    }

    private int readHeader0(DicomObject dcmObj) throws EOFException {
        if (pos + 8 > cache.length()) {
            throw new EOFException();
        }
        vrCode = 0;
        switch (tag = input.tagAt(pos)) {
            case Tag.Item:
            case Tag.ItemDelimitationItem:
            case Tag.SequenceDelimitationItem:
                vr = VR.NONE;
                valueLength = input.intAt(pos + 4);
                return 8;
        }
        if (!input.encoding.explicitVR) {
            vr = lookupVR(dcmObj);
            valueLength = input.intAt(pos + 4);
            return 8;
        }
        vrCode = cache.vrcode(pos + 4);
        VR encodedVR = VR.of(vrCode);
        if (encodedVR.shortValueLength) {
            valueLength = input.ushortAt(pos + 6);
            vr = encodedVR;
            return 8;
        }
        if (pos + 12 > cache.length()) {
            throw new EOFException();
        }
        valueLength = input.intAt(pos + 8);
        vr = encodedVR != VR.UN ? encodedVR : valueLength == -1 ? VR.SQ : lookupVR(dcmObj);
        return 12;
    }

    private VR lookupVR(DicomObject dcmObj) {
        return ElementDictionary.vrOf(tag, dcmObj != null ? dcmObj.getPrivateCreator(tag) : Optional.empty());
    }

    private boolean parse(DicomObject dcmObj, int length) throws IOException {
        boolean undefinedLength = length == -1;
        boolean expectEOF = undefinedLength && !dcmObj.hasParent();
        long endPos = pos + length;
        while ((undefinedLength || pos < endPos)
                && readHeader(dcmObj, expectEOF)
                && !(undefinedLength && isDelimitationItem(Tag.ItemDelimitationItem))) {
            if (valueLength == BulkDataElement.MAGIC_LEN) {
                deserializeBulkDataElement(dcmObj);
            } else if (vr == VR.SQ) {
                if (!parseItems(new DicomSequence(dcmObj, tag,
                        pos - (input.encoding.explicitVR ? 12 : 8), valueLength)))
                    return false;
            } else if (valueLength == -1) {
                if (!parseDataFragments(
                        new DataFragments(dcmObj, tag, vr, pos - (input.encoding.explicitVR ? 12 : 8))))
                    return false;
            } else {
                if (!parseCommonElement(input.dicomElement(dcmObj, tag, vr, pos, valueLength)))
                    return false;
            }
        }
        return true;
    }

    private void deserializeBulkDataElement(DicomObject dcmObj) throws IOException {
        cache.loadFromStream(pos + 2, in);
        valueLength = input.ushortAt(pos);
        pos += 2;
        cache.loadFromStream(pos + valueLength, in);
        dcmObj.add(new BulkDataElement(
                dcmObj, tag, vr, input.stringAt(pos, valueLength, SpecificCharacterSet.UTF_8), null));
        pos += valueLength;
    }

    private boolean skipItem(int length) throws IOException {
        if (length != -1) {
            pos += length;
        } else {
            while (readHeader(null, false)
                    && !isDelimitationItem(Tag.ItemDelimitationItem)) {
                if (valueLength != -1) {
                    pos += valueLength;
                } else if (vrCode == VR.UN.code && !probeExplicitVR(pos + 12)) {
                    skipSequenceWithUndefLengthIVR_LE();
                } else {
                    skipSequenceWithUndefLength();
                }
            }
        }
        return true;
    }

    private boolean parseCommonElement(DicomElement dcmElm) throws IOException {
        boolean bulkData = bulkDataPredicate.test(dcmElm);
        if (!handler.startElement(dis, dcmElm, bulkData))
            return false;

        if (bulkData)
            skipBulkData();

        pos += valueLength;
        return handler.endElement(dis, dcmElm, bulkData);
    }

    private boolean parseItems(DicomSequence dcmElm) throws IOException {
        return handler.startElement(dis, dcmElm, false)
                && parseItems0(dcmElm)
                && handler.endElement(dis, dcmElm, false);
    }

    private boolean parseItems0(DicomSequence dcmElm) throws IOException {
        return vrCode == VR.UN.code && !probeExplicitVR(pos + 12)
            ? parseItemsIVR_LE(dcmElm, valueLength)
            : parseItems(dcmElm, valueLength);
    }

    private boolean parseItemsIVR_LE(DicomSequence dcmElm, int length) throws IOException {
        DicomInput input0 = input;
        input = new DicomInput(cache, DicomEncoding.IVR_LE);
        try {
            return parseItems(dcmElm, length);
        } finally {
            input = input0;
        }
    }

    private boolean parseItems(DicomSequence dcmSeq, int length)
            throws IOException {
        boolean undefinedLength = length == -1;
        long endPos = pos + length;
        while ((undefinedLength || pos < endPos)
                && readHeader(null, false)
                && !(undefinedLength && isDelimitationItem(Tag.SequenceDelimitationItem))) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            if (parseItemsPredicate.test(dcmSeq)
                    ? !parseItem(dcmSeq, new DicomObjectImpl(input, pos, valueLength, new ArrayList<>()))
                    : !skipItem(dcmSeq, new DicomObjectImpl(input, pos, valueLength, null)))
                return false;
        }
        return true;
    }

    private void skipSequenceWithUndefLengthIVR_LE() throws IOException {
        DicomInput input0 = input;
        input = new DicomInput(cache, DicomEncoding.IVR_LE);
        try {
            skipSequenceWithUndefLength();
        } finally {
            input = input0;
        }
    }

    private void skipSequenceWithUndefLength() throws IOException {
        while (readHeader(null, false)
                && !isDelimitationItem(Tag.SequenceDelimitationItem)) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            skipItem(valueLength);
        }
    }

    private boolean parseItem(DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return handler.startItem(dis, dcmSeq, dcmObj)
                && parse(dcmObj, valueLength)
                && handler.endItem(dis, dcmSeq, dcmObj);
    }

    private boolean skipItem(DicomSequence dcmSeq, DicomObject dcmObj) throws IOException {
        return handler.startItem(dis, dcmSeq, dcmObj)
                && skipItem(valueLength)
                && handler.endItem(dis, dcmSeq, dcmObj);
    }

    private boolean parseDataFragments(DataFragments fragments) throws IOException {
        boolean bulkData = bulkDataPredicate.test(fragments);
        if (!handler.startElement(dis, fragments, bulkData))
            return false;

        while (readHeader(null, false)
                && !isDelimitationItem(Tag.SequenceDelimitationItem)) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            if (bulkData) {
                skipBytes(-headerLength, headerLength + valueLength, bulkDataSpoolStream);
                bulkDataSpoolStreamPos += headerLength + valueLength;
            } else if (fragments != null
                    && !handler.dataFragment(dis, fragments, input.dataFragment(fragments, pos, valueLength))) {
                return false;
            }

            pos += valueLength;
        }
        if (bulkData) {
            skipBytes(-headerLength, headerLength, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += headerLength;
        }
        return handler.endElement(dis, fragments, bulkData);
    }

    public void skipBytes(int off, int length, OutputStream out) throws IOException {
        cache.skipBytes(pos + off, length, in, out);
    }

    private boolean isDelimitationItem(int delimitationItemTag) throws DicomParseException {
        if (tag != delimitationItemTag)
            return false;

        if (valueLength != 0)
            throw new DicomParseException();

        return true;
    }

    public String bulkDataSpoolPathURI() {
        if (bulkDataSpoolPath == null) {
            bulkDataSpoolPath = bulkDataSpoolPathSupplier.get();
            try {
                bulkDataSpoolStream = Files.newOutputStream(bulkDataSpoolPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return bulkDataURI(bulkDataSpoolPath.toUri().toString(), bulkDataSpoolStreamPos, valueLength);
    }

    public String bulkDataURI(Path sourcePath) {
        return bulkDataURI(sourcePath.toUri().toString(), pos, valueLength);
    }

    private String bulkDataURI(String url, long pos, int valueLength) {
        StringBuilder sb = new StringBuilder();
        char ch = '#';
        sb.append(url);
        if (pos != 0) {
            sb.append(ch).append("offset=").append(pos);
            ch = '&';
        }
        if (valueLength != -1) {
            sb.append(ch).append("length=").append(valueLength);
            ch = '&';
        }
        if (input.encoding.byteOrder == ByteOrder.BIG_ENDIAN) {
            sb.append(ch).append("endian=big");
        }
        return sb.toString();
    }

    @Override
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) {
        if (bulkData) {
            if (bulkDataURIProducer == null)
                return true;

            dcmElm = new BulkDataElement(dcmElm.containedBy(), tag, vr, bulkDataURIProducer.apply(this.dis), null);
        }
        dcmElm.containedBy().add(dcmElm);
        return true;
    }

    private void skipBulkData() throws IOException {
        skipBytes(-headerLength, headerLength, null);
        if (valueLength > 0) {
            skipBytes(0, valueLength, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += valueLength;
        }
    }

    public String bulkDataURI() {
        return bulkDataURIProducer != null ? bulkDataURIProducer.apply(dis) : null;
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) {
        dcmSeq.addItem(dcmObj);
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DicomElement fragments, DataFragment dataFragment) {
        fragments.addDataFragment(dataFragment);
        return true;
    }
}

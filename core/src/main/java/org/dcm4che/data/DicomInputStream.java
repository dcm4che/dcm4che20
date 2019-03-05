package org.dcm4che.data;

import org.dcm4che.util.TagUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomInputStream extends InputStream implements DicomInputHandler {
    private final MemoryCache cache;
    private InputStream in;
    private DicomInput input;
    private int limit = -1;
    private long pos;
    private int tag;
    private int vrCode;
    private VR vr;
    private int headerLength;
    private int valueLength;
    private DicomObject fmi;
    private DicomInputHandler handler = this;
    private Predicate<DicomElement> parseItemsPredicate = x -> true;
    private Predicate<DicomElement> bulkDataPredicate = x -> false;
    private URIProducer bulkDataURIProducer;
    private PathSupplier bulkDataSpoolPathSupplier;
    private Path bulkDataSpoolPath;
    private OutputStream bulkDataSpoolStream;
    private long bulkDataSpoolStreamPos;

    public DicomInputStream(InputStream in) {
        this.cache = new MemoryCache();
        this.in = in;
    }

    private DicomInputStream(DicomInput input, long pos) {
        this.input = input;
        this.pos = pos;
        this.cache = input.cache;
    }

    public DicomEncoding getEncoding() {
        return input != null ? input.encoding : null;
    }

    public DicomInputStream withEncoding(DicomEncoding encoding) throws IOException {
        input = new DicomInput(cache, encoding);
        if (input.encoding.deflated) {
            in = cache.inflate(pos, in);
        }
        return this;
    }

    public DicomInputStream withLimit(int limit) throws IOException {
        if (limit <= 0)
            throw new IllegalArgumentException("limit: " + limit);

        this.limit = limit;
        return this;
    }

    public DicomInputStream withParseItems(Predicate<DicomElement> parseItemsPredicate) {
        this.parseItemsPredicate = Objects.requireNonNull(parseItemsPredicate);
        return this;
    }

    public DicomInputStream withParseItemsLazy(int seqTag) {
        return withParseItems(x -> x.tag() != seqTag);
    }

    public DicomInputStream withBulkData(Predicate<DicomElement> bulkDataPredicate) {
        this.bulkDataPredicate = Objects.requireNonNull(bulkDataPredicate);
        return this;
    }

    public DicomInputStream withBulkDataURIProducer(URIProducer bulkDataURIProducer) {
        this.bulkDataURIProducer = Objects.requireNonNull(bulkDataURIProducer);
        return this;
    }

    public DicomInputStream withBulkDataURI(Path sourcePath) {
        return withBulkDataURIProducer(x -> x.bulkDataURI(sourcePath.toUri().toString(), x.pos, x.valueLength));
    }

    public DicomInputStream withInputHandler(DicomInputHandler handler) {
        this.handler = Objects.requireNonNull(handler);
        return this;
    }

    public DicomInputHandler getInputHandler() {
        return handler;
    }

    public DicomInputStream spoolBulkDataTo(Path path) {
        return spoolBulkData(() -> path);
    }

    public DicomInputStream spoolBulkData(PathSupplier bulkDataSpoolPathSupplier) {
        this.bulkDataSpoolPathSupplier = Objects.requireNonNull(bulkDataSpoolPathSupplier);
        this.bulkDataURIProducer = DicomInputStream::bulkDataSpoolPathURI;
        return this;
    }

    public long getStreamPosition() {
        return pos;
    }

    @Override
    public int read() throws IOException {
        if (cache.loadFromStream(pos + 1, in) == pos)
            return -1;

        return cache.byteAt(pos++);
    }

    @Override
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

    @Override
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

        DicomObject dcmObj = new DicomObject();
        pos = 132;
        input = new DicomInput(cache, DicomEncoding.EVR_LE);
        readHeader(dcmObj, false);
        DicomElement groupLength = input.dicomElement(dcmObj, tag, vr, pos, valueLength);
        handler.startElement(this, groupLength, false);
        handler.endElement(this, groupLength, false);
        pos += valueLength;
        parse(dcmObj, groupLength.intValue(0, -1));
        String tsuid = dcmObj.getString(Tag.TransferSyntaxUID);
        if (tsuid == null)
            throw new DicomParseException("Missing Transfer Syntax UID in File Meta Information");

        withEncoding(DicomEncoding.of(tsuid));
        fmi = dcmObj;
        return fmi;
    }

    public DicomObject getFileMetaInformation() {
        return fmi;
    }

    public DicomObject readCommandSet() throws IOException {
        if (pos != 0)
            throw new IllegalStateException("Stream position: " + pos);

        if (input != null)
            throw new IllegalStateException("encoding already initialized: " + input.encoding);

        input = new DicomInput(cache, DicomEncoding.IVR_LE);
        DicomObject dcmObj = new DicomObject();
        parse(dcmObj, limit);
        return dcmObj;
    }

    public DicomObject readDataSet() throws IOException {
        DicomObject dcmObj = new DicomObject();
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
        return ElementDictionary.vrOf(tag, dcmObj != null ? dcmObj.getPrivateCreator(tag) : null);
    }

    static void parse(DicomObject dcmObj, DicomInput input, long pos, int length) throws IOException {
        new DicomInputStream(input, pos).parse(dcmObj, length);
    }

    private boolean parse(DicomObject dcmObj, int length) throws IOException {
        boolean undefinedLength = length == -1;
        boolean expectEOF = undefinedLength && dcmObj.containedBy() == null;
        long endPos = pos + length;
        while ((undefinedLength || pos < endPos)
                && readHeader(dcmObj, expectEOF)
                && !(undefinedLength && isDelimitationItem(Tag.ItemDelimitationItem))) {
            if (valueLength == BulkDataElement.MAGIC_LEN) {
                deserializeBulkDataElement(dcmObj);
            } else if (vr == VR.SQ) {
                if (!parseItems(new DicomSequence(dcmObj, tag)
                        .streamPosition(pos - (input.encoding.explicitVR ? 12 : 8))
                        .valueLength(valueLength)))
                    return false;
            } else if (valueLength == -1) {
                if (!parseDataFragments(new DataFragments(dcmObj, tag, vr)
                        .streamPosition(pos - (input.encoding.explicitVR ? 12 : 8))))
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
        if (!handler.startElement(this, dcmElm, bulkData))
            return false;

        pos += valueLength;
        return handler.endElement(this, dcmElm, bulkData);
    }

    private boolean parseItems(DicomSequence dcmElm) throws IOException {
        return handler.startElement(this, dcmElm, false)
                && parseItems0(dcmElm)
                && handler.endElement(this, dcmElm, false);
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

    private boolean parseItems(DicomSequence dcmElm, int length)
            throws IOException {
        boolean undefinedLength = length == -1;
        long endPos = pos + length;
        while ((undefinedLength || pos < endPos)
                && readHeader(null, false)
                && !(undefinedLength && isDelimitationItem(Tag.SequenceDelimitationItem))) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            if (parseItemsPredicate.test(dcmElm)
                ? !parseItem(input.item(dcmElm, pos, valueLength, new ArrayList<>()))
                : !skipItem(input.item(dcmElm, pos, valueLength, null)))
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

    private boolean parseItem(DicomObject dcmObj) throws IOException {
        return handler.startItem(this, dcmObj)
                && parse(dcmObj, valueLength)
                && handler.endItem(this, dcmObj);
    }

    private boolean skipItem(DicomObject dcmObj) throws IOException {
        return handler.startItem(this, dcmObj)
                && skipItem(valueLength)
                && handler.endItem(this, dcmObj);
    }

    private boolean parseDataFragments(DataFragments fragments) throws IOException {
        boolean bulkData = bulkDataPredicate.test(fragments);
        if (!handler.startElement(this, fragments, bulkData))
            return false;

        while (readHeader(null, false)
                && !isDelimitationItem(Tag.SequenceDelimitationItem)) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            if (bulkData) {
                skipBytes(-headerLength, headerLength + valueLength, bulkDataSpoolStream);
                bulkDataSpoolStreamPos += headerLength + valueLength;
            } else if (fragments != null && !handler.dataFragment(this, input.dataFragment(fragments, pos, valueLength))) {
                return false;
            }

            pos += valueLength;
        }
        if (bulkData) {
            skipBytes(-headerLength, headerLength, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += headerLength;
        }
        return handler.endElement(this, fragments, bulkData);
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

    private String bulkDataSpoolPathURI() throws IOException {
        if (bulkDataSpoolPath == null) {
            bulkDataSpoolPath = bulkDataSpoolPathSupplier.get();
            bulkDataSpoolStream = Files.newOutputStream(bulkDataSpoolPath);
        }
        return bulkDataURI(bulkDataSpoolPath.toUri().toString(), bulkDataSpoolStreamPos, valueLength);
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
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        if (bulkData) {
            String bulkDataURI = bulkDataURI();
            if (bulkDataURI != null) {
                dcmElm = new BulkDataElement(dcmElm.containedBy(), tag, vr, bulkDataURI, null);
                dcmElm.containedBy().add(dcmElm);
            }
            skipBulkData();
            return true;
        }
        dcmElm.containedBy().add(dcmElm);
        return true;
    }

    public String bulkDataURI() throws IOException {
        return bulkDataURIProducer != null ? bulkDataURIProducer.apply(this) : null;
    }

    public void skipBulkData() throws IOException {
        skipBytes(-headerLength, headerLength, null);
        if (valueLength > 0) {
            skipBytes(0, valueLength, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += valueLength;
        }
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomObject dcmObj) {
        dcmObj.containedBy().addItem(dcmObj);
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DataFragment dataFragment) {
        dataFragment.containedBy().addDataFragment(dataFragment);
        return true;
    }

    public static boolean isBulkData(DicomElement el) {
        switch (el.tag()) {
            case Tag.PixelData:
            case Tag.FloatPixelData:
            case Tag.DoubleFloatPixelData:
            case Tag.SpectroscopyData:
            case Tag.EncapsulatedDocument:
                return !el.containedBy().hasParent();
            case Tag.WaveformData:
                return isWaveformSequenceItem(el.containedBy());
        }
        switch (el.tag() & 0xFF00FFFF) {
            case Tag.AudioSampleData:
            case Tag.CurveData:
            case Tag.OverlayData:
                return !el.containedBy().hasParent();
        }
        return false;
    }

    private static boolean isWaveformSequenceItem(DicomObject item) {
        DicomSequence seq = item.containedBy();
        return seq != null && seq.tag() == Tag.WaveformSequence && !seq.containedBy().hasParent();
    }

    @FunctionalInterface
    public interface URIProducer {
        String apply(DicomInputStream reader) throws IOException;
    }

    @FunctionalInterface
    public interface PathSupplier {
        Path get() throws IOException;
    }
}

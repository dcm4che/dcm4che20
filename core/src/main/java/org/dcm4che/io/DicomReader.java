package org.dcm4che.io;

import org.dcm4che.data.*;
import org.dcm4che.util.TagUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomReader implements DicomInputHandler, Closeable {
    private final MemoryCache cache;
    private InputStream in;
    private DicomInput input;
    private boolean lazy;
    private int limit = -1;
    private long pos;
    private int tag;
    private VR encodedVR;
    private VR vr;
    private int headerLength;
    private int valueLength;
    private DicomObject fmi;
    private DicomInputHandler handler = this;
    private Predicate<DicomElement> bulkDataPredicate = x -> false;
    private URIProducer bulkDataURIProducer;
    private PathSupplier bulkDataSpoolPathSupplier;
    private Path sourcePath;
    private Path bulkDataSpoolPath;
    private OutputStream bulkDataSpoolStream;
    private long bulkDataSpoolStreamPos;

    public DicomReader(InputStream in) {
        this.cache = new MemoryCache();
        this.in = in;
    }

    private DicomReader(DicomInput input, long pos) {
        this.input = input;
        this.pos = pos;
        this.cache = input.cache;
    }

    public DicomEncoding getEncoding() {
        return input != null ? input.encoding : null;
    }

    public DicomReader withEncoding(DicomEncoding encoding) throws IOException {
        input = new DicomInput(cache, encoding);
        if (input.encoding.deflated) {
            in = cache.inflate(pos, in);
        }
        return this;
    }

    public boolean isLazy() {
        return lazy;
    }

    public DicomReader withLazy(boolean lazy) {
        this.lazy = lazy;
        return this;
    }

    public DicomReader withLimit(int limit) throws IOException {
        if (limit <= 0)
            throw new IllegalArgumentException("limit: " + limit);

        this.limit = limit;
        return this;
    }

    public DicomReader withBulkData(Predicate<DicomElement> bulkDataPredicate) {
        this.bulkDataPredicate = Objects.requireNonNull(bulkDataPredicate);
        return this;
    }

    public DicomReader withBulkDataURIProducer(URIProducer bulkDataURIProducer) {
        this.bulkDataURIProducer = Objects.requireNonNull(bulkDataURIProducer);
        return this;
    }

    public DicomReader withBulkDataURI(Path sourcePath) {
        this.sourcePath = Objects.requireNonNull(sourcePath);
        this.bulkDataURIProducer = DicomReader::bulkDataSourcePathURI;
        return this;
    }

    public DicomReader spoolBulkData(PathSupplier bulkDataSpoolPathSupplier) {
        this.bulkDataSpoolPathSupplier = Objects.requireNonNull(bulkDataSpoolPathSupplier);
        this.bulkDataURIProducer = DicomReader::bulkDataSpoolPathURI;
        return this;
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
        parse(dcmObj, readInt());
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
        switch (tag = input.tagAt(pos)) {
            case Tag.Item:
            case Tag.ItemDelimitationItem:
            case Tag.SequenceDelimitationItem:
                encodedVR = null;
                vr = VR.NONE;
                valueLength = input.intAt(pos + 4);
                return 8;
        }
        if (!input.encoding.explicitVR) {
            encodedVR = null;
            vr = lookupVR(dcmObj);
            valueLength = input.intAt(pos + 4);
            return 8;
        }
        encodedVR = VR.of(cache.vrcode(pos + 4));
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

    private int readInt() throws IOException {
        if (cache.loadFromStream(pos + 4, in) < pos+4)
            throw new EOFException();

        int value = input.intAt(pos);
        pos += 4;
        return value;
    }

    public static void parse(DicomObject dcmObj, DicomInput input, long pos, int length) throws IOException {
        new DicomReader(input, pos).parse(dcmObj, length);
    }

    private boolean parse(DicomObject dcmObj, int length) throws IOException {
        boolean undefinedLength = length == -1;
        boolean expectEOF = undefinedLength && dcmObj.getDicomSequence() == null;
        long endPos = pos + length;
        while ((undefinedLength || pos < endPos)
                && readHeader(dcmObj, expectEOF)
                && !(undefinedLength && isDelimitationItem(Tag.ItemDelimitationItem))) {
            if (vr == VR.SQ) {
                if (!parseItems(new DicomSequence(dcmObj, tag)))
                    return false;
            } else if (valueLength == -1) {
                if (!parseDataFragments(new DataFragments(dcmObj, tag, vr)))
                    return false;
            } else {
                if (!parseCommonElement(input.dicomElement(dcmObj, tag, vr, pos, valueLength)))
                    return false;
            }
        }
        return true;
    }

    private boolean skipItem(int length) throws IOException {
        if (length != -1) {
            pos += length;
        } else {
            while (readHeader(null, false)
                    && !isDelimitationItem(Tag.ItemDelimitationItem)) {
                if (valueLength != -1) {
                    pos += valueLength;
                } else if (encodedVR == VR.UN && !probeExplicitVR(pos + 12)) {
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
        if (bulkData && bulkDataURIProducer != null) {
            dcmElm = new BulkDataElement(dcmElm.getDicomObject(), tag, vr, bulkDataURIProducer.apply(this));
        }
        if (!handler.startElement(dcmElm, !(bulkData && bulkDataURIProducer == null)))
            return false;

        if (bulkData) {
            cache.skipBytes(pos - headerLength, headerLength, in, null);
            cache.skipBytes(pos, valueLength, in, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += valueLength;
        }
        pos += valueLength;
        return handler.endElement(dcmElm);
    }

    private boolean parseItems(DicomSequence dcmElm) throws IOException {
        return handler.startElement(dcmElm, true)
                && parseItems0(dcmElm)
                && handler.endElement(dcmElm);
    }

    private boolean parseItems0(DicomSequence dcmElm) throws IOException {
        return encodedVR == VR.UN && !probeExplicitVR(pos + 12)
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

            if (!parseItem(input.item(dcmElm, pos, valueLength, lazy)))
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
        return handler.startItem(dcmObj)
                && lazy ? skipItem(valueLength) : parse(dcmObj, valueLength)
                && handler.endItem(dcmObj);
    }

    private boolean parseDataFragments(DataFragments fragments) throws IOException {
        boolean bulkData = bulkDataPredicate.test(fragments);
        DicomElement dcmElm = bulkData && bulkDataURIProducer != null
                ? new BulkDataElement(fragments.getDicomObject(), tag, vr, bulkDataURIProducer.apply(this))
                : fragments;
        if (!handler.startElement(dcmElm, !(bulkData && bulkDataURIProducer == null)))
            return false;

        if (bulkData) {
            cache.skipBytes(pos - headerLength, headerLength, in, null);
        }
        return parseDataFragments(fragments, bulkData) && handler.endElement(dcmElm);
    }

    private boolean parseDataFragments(DataFragments dcmElm, boolean bulkData) throws IOException {
        while (readHeader(null, false)
                && !isDelimitationItem(Tag.SequenceDelimitationItem)) {
            if (tag != Tag.Item)
                throw new DicomParseException("Expected (FFFE,E000) but " + TagUtils.toString(tag));

            if (bulkData) {
                cache.skipBytes(pos - headerLength, headerLength + valueLength, in, bulkDataSpoolStream);
                bulkDataSpoolStreamPos += headerLength + valueLength;
            } else if (dcmElm != null && !handler.dataFragment(input.dataFragment(dcmElm, pos, valueLength))) {
                return false;
            }

            pos += valueLength;
        }
        if (bulkData) {
            cache.skipBytes(pos - headerLength, headerLength, in, bulkDataSpoolStream);
            bulkDataSpoolStreamPos += headerLength;
        }
        return true;
    }

    private boolean isDelimitationItem(int delimitationItemTag) throws DicomParseException {
        if (tag != delimitationItemTag)
            return false;

        if (valueLength != 0)
            throw new DicomParseException();

        return true;
    }

    private String bulkDataSourcePathURI() {
        return bulkDataURI(sourcePath.toUri().toString(), pos, valueLength);
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
        sb.append(url).append("#offset=").append(pos).append("&length=").append(valueLength);
        if (input.encoding.byteOrder == ByteOrder.BIG_ENDIAN)
            sb.append("&bigEndian=true");
        return sb.toString();
    }

    @Override
    public boolean startElement(DicomElement dcmElm, boolean include) {
        if (include)
            dcmElm.getDicomObject().add(dcmElm);
        return true;
    }

    @Override
    public boolean startItem(DicomObject dcmObj) {
        dcmObj.getDicomSequence().addItem(dcmObj);
        return true;
    }

    @Override
    public boolean dataFragment(DataFragment dataFragment) {
        dataFragment.getDataFragments().addDataFragment(dataFragment);
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            if (bulkDataSpoolStream != null)
                bulkDataSpoolStream.close();
        } finally {
            in.close();
        }
    }

    public static boolean isBulkData(DicomElement el) {
        switch (el.tag()) {
            case Tag.PixelData:
            case Tag.FloatPixelData:
            case Tag.DoubleFloatPixelData:
            case Tag.SpectroscopyData:
            case Tag.EncapsulatedDocument:
                return !el.getDicomObject().hasParent();
            case Tag.WaveformData:
                return isWaveformSequenceItem(el.getDicomObject());
        }
        switch (el.tag() & 0xFF00FFFF) {
            case Tag.AudioSampleData:
            case Tag.CurveData:
            case Tag.OverlayData:
                return !el.getDicomObject().hasParent();
        }
        return false;
    }

    private static boolean isWaveformSequenceItem(DicomObject item) {
        DicomSequence seq = item.getDicomSequence();
        return seq != null && seq.tag() == Tag.WaveformSequence && !seq.getDicomObject().hasParent();
    }

    @FunctionalInterface
    public interface URIProducer {
        String apply(DicomReader reader) throws IOException;
    }

    @FunctionalInterface
    public interface PathSupplier {
        Path get() throws IOException;
    }
}

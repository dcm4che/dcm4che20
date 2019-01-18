package org.dcm4che.data;

import org.dcm4che.util.TagUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DicomOutputStream implements Closeable {

    private static final int BUFFER_LENGTH = 0x2000;
    private OutputStream out;

    private DicomEncoding encoding;
    private boolean includeGroupLength;
    private LengthEncoding itemLengthEncoding = LengthEncoding.UNDEFINED_OR_ZERO;
    private LengthEncoding sequenceLengthEncoding = LengthEncoding.UNDEFINED_OR_ZERO;
    private final byte[] header = new byte[12];
    private byte[] swapBuffer;

    public DicomOutputStream(OutputStream out) {
        this.out = Objects.requireNonNull(out);
    }

    public DicomEncoding getEncoding() {
        return encoding;
    }

    public DicomOutputStream withEncoding(DicomEncoding encoding) {
        this.encoding = Objects.requireNonNull(encoding);
        if (encoding.deflated) {
            out = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
        }
        return this;
    }

    public boolean isIncludeGroupLength() {
        return includeGroupLength;
    }

    public DicomOutputStream withIncludeGroupLength(boolean includeGroupLength) {
        this.includeGroupLength = includeGroupLength;
        return this;
    }

    public LengthEncoding getItemLengthEncoding() {
        return itemLengthEncoding;
    }

    public DicomOutputStream withItemLengthEncoding(LengthEncoding itemLengthEncoding) {
        this.itemLengthEncoding = Objects.requireNonNull(itemLengthEncoding);
        return this;
    }

    public LengthEncoding getSequenceLengthEncoding() {
        return sequenceLengthEncoding;
    }

    public DicomOutputStream withSequenceLengthEncoding(LengthEncoding sequenceLengthEncoding) {
        this.sequenceLengthEncoding = Objects.requireNonNull(sequenceLengthEncoding);
        return this;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public void writeHeader(int tag, VR vr, int length) throws IOException {
        int headerLength = 8;
        byte[] header = this.header;
        ByteOrder byteOrder = encoding.byteOrder;
        byteOrder.tagToBytes(tag, header, 0);
        if (vr == VR.NONE || !encoding.explicitVR) {
            byteOrder.intToBytes(length, header, 4);
        } else {
            header[4] = (byte) (vr.code >>> 8);
            header[5] = (byte) vr.code;
            if (vr.shortValueLength) {
                byteOrder.shortToBytes(length, header, 6);
            } else {
                header[6] = 0;
                header[7] = 0;
                byteOrder.intToBytes(length, header, 8);
                headerLength = 12;
            }
        }
        out.write(header, 0, headerLength);
    }

    public DicomOutputStream writeFileMetaInformation(DicomObject fmi) throws IOException {
        if (encoding != null)
            throw new IllegalStateException("encoding already initialized: " + encoding);

        String tsuid = fmi.getString(Tag.TransferSyntaxUID);
        if (tsuid == null)
            throw new IllegalArgumentException("Missing Transfer Syntax UID in File Meta Information");

        byte[] b = new byte[132];
        b[128] = 'D';
        b[129] = 'I';
        b[130] = 'C';
        b[131] = 'M';
        out.write(b);
        encoding = DicomEncoding.EVR_LE;
        boolean includeGroupLength0 = includeGroupLength;
        try {
            includeGroupLength = true;
            calculateItemLength(fmi);
            write(fmi);
        } finally {
            includeGroupLength = includeGroupLength0;
        }
        withEncoding(DicomEncoding.of(tsuid));
        return this;
    }

    public void writeDataSet(DicomObject dcmobj) throws IOException {
        if (encoding == null)
            throw new IllegalStateException("encoding not initialized");

        Objects.requireNonNull(dcmobj);
        if (includeGroupLength || itemLengthEncoding.explicit || sequenceLengthEncoding.explicit) {
            calculateItemLength(dcmobj);
        }
        write(dcmobj);
        if (out instanceof DeflaterOutputStream) {
            ((DeflaterOutputStream) out).finish();
        }
    }

    public void writeCommandSet(DicomObject dcmobj) throws IOException {
        if (encoding != null)
            throw new IllegalStateException("encoding already initialized: " + encoding);

        Objects.requireNonNull(dcmobj);
        encoding = DicomEncoding.IVR_LE;
        includeGroupLength = true;
        calculateItemLength(dcmobj);
        write(dcmobj);
    }

    private void write(DicomObject dcmObj) throws IOException {
        for (DicomElement element : dcmObj) {
            if (includeGroupLength || !TagUtils.isGroupLength(element.tag()))
                element.writeTo(this);
        }
    }

    OutputStream getOutputStream() {
        return out;
    }

    byte[] swapBuffer() {
        if (swapBuffer == null) {
            swapBuffer = new byte[BUFFER_LENGTH];
        }
        return swapBuffer;
    }

    void writeSequence(DicomSequence seq) throws IOException {
        boolean undefinedLength = sequenceLengthEncoding.undefined.test(seq.size());
        writeHeader(seq.tag(), seq.vr(), undefinedLength ? -1 : seq.itemStream().mapToInt(this::lengthOf).sum());
        for (DicomObject item : seq) {
            writeItem(item);
        }
        if (undefinedLength) {
            writeHeader(Tag.SequenceDelimitationItem, VR.NONE, 0);
        }
    }

    private void writeItem(DicomObject dcmobj) throws IOException {
        boolean undefinedLength = itemLengthEncoding.undefined.test(dcmobj.size());
        writeHeader(Tag.Item, VR.NONE, undefinedLength ? -1 : dcmobj.getItemLength());
        write(dcmobj);
        if (undefinedLength) {
            writeHeader(Tag.ItemDelimitationItem, VR.NONE, 0);
        }
    }

    void serialize(BulkDataElement bulkData) throws IOException {
        byte[] header = this.header;
        ByteOrder byteOrder = encoding.byteOrder;
        byteOrder.tagToBytes(bulkData.tag(), header, 0);
        int vrCode = bulkData.vr().code;
        header[4] = (byte) ((vrCode | 0x8000) >>> 8);
        header[5] = (byte) vrCode;
        byte[] encodedURI = SpecificCharacterSet.UTF_8.encode(bulkData.bulkDataURI(), null);
        byteOrder.shortToBytes(encodedURI.length, header, 6);
        out.write(header, 0, 8);
        out.write(encodedURI);
    }

    private int calculateLengthOf(DicomElement el) {
        int len = (!encoding.explicitVR || el.vr().shortValueLength ? 8 : 12);
        if (el instanceof DicomSequence) {
            DicomSequence seq = (DicomSequence) el;
            len += sequenceLengthEncoding.adjustLength.applyAsInt(
                    seq.isEmpty() ? 0 : seq.itemStream().mapToInt(this::calculateLengthOf).sum());
        } else if (el instanceof DataFragments) {
            DataFragments dataFragments = (DataFragments) el;
            len += dataFragments.fragmentStream().mapToInt(DataFragment::valueLength).sum()
                    + dataFragments.size() * 8 + 8;
        } else {
            len += el.valueLength();
        }
        return len;
    }

    private int calculateLengthOf(DicomObject item) {
        return 8 + itemLengthEncoding.adjustLength.applyAsInt(calculateItemLength(item));
    }

    private int calculateItemLength(DicomObject dcmobj) {
        int len = 0;
        if (!dcmobj.isEmpty()) {
            int groupLengthTag = TagUtils.groupLengthTagOf(dcmobj.firstElement().tag());
            if (includeGroupLength && groupLengthTag != TagUtils.groupLengthTagOf(dcmobj.lastElement().tag())) {
                Map<Integer, Integer> groups = dcmobj.elementStream()
                        .collect(Collectors.groupingBy(
                                x -> TagUtils.groupNumber(x.tag()),
                                Collectors.filtering(x -> !TagUtils.isGroupLength(x.tag()),
                                        Collectors.summingInt(this::calculateLengthOf))));
                for (Map.Entry<Integer, Integer> group : groups.entrySet()) {
                    int glen = group.getValue();
                    dcmobj.setInt(group.getKey() << 16, VR.UL, glen);
                    len += glen + 12;
                }
            } else {
                len = dcmobj.elementStream().filter(x -> !TagUtils.isGroupLength(x.tag()))
                        .collect(Collectors.summingInt(this::calculateLengthOf));
                if (includeGroupLength) {
                    dcmobj.setInt(groupLengthTag, VR.UL, len);
                    len += 12;
                }
            }
        }
        dcmobj.setItemLength(len);
        return len;
    }

    private int lengthOf(DicomObject item) {
        return 8 + itemLengthEncoding.adjustLength.applyAsInt(item.getItemLength());
    }

    public enum LengthEncoding {
        UNDEFINED_OR_ZERO(false, x -> x > 0, x -> x > 0 ? x + 8 : x),
        UNDEFINED(false, x -> true, x -> x + 8),
        EXPLICIT(true, x -> false, x -> x);

        public final boolean explicit;
        public final IntPredicate undefined;
        public final IntUnaryOperator adjustLength;

        LengthEncoding(boolean explicit, IntPredicate undefined, IntUnaryOperator adjustLength) {
            this.explicit = explicit;
            this.undefined = undefined;
            this.adjustLength = adjustLength;
        }
    }

}

package org.dcm4che.io;

import org.dcm4che.data.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DicomWriter implements Closeable {

    private static final int BUFFER_LENGTH = 0x2000;
    private OutputStream out;

    private DicomEncoding encoding;
    private boolean includeGroupLength;
    private LengthEncoding itemLengthEncoding = LengthEncoding.UNDEFINED_OR_ZERO;
    private LengthEncoding sequenceLengthEncoding = LengthEncoding.UNDEFINED_OR_ZERO;
    private final byte[] header = new byte[12];
    private byte[] swapBuffer;

    public DicomWriter(OutputStream out) {
        this.out = Objects.requireNonNull(out);
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public DicomEncoding getEncoding() {
        return encoding;
    }

    public DicomWriter withEncoding(DicomEncoding encoding) {
        this.encoding = Objects.requireNonNull(encoding);
        if (encoding.deflated) {
            out = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
        }
        return this;
    }

    public boolean isIncludeGroupLength() {
        return includeGroupLength;
    }

    public DicomWriter withIncludeGroupLength(boolean includeGroupLength) {
        this.includeGroupLength = includeGroupLength;
        return this;
    }

    public LengthEncoding getItemLengthEncoding() {
        return itemLengthEncoding;
    }

    public DicomWriter withItemLengthEncoding(LengthEncoding itemLengthEncoding) {
        this.itemLengthEncoding = Objects.requireNonNull(itemLengthEncoding);
        return this;
    }

    public LengthEncoding getSequenceLengthEncoding() {
        return sequenceLengthEncoding;
    }

    public DicomWriter withSequenceLengthEncoding(LengthEncoding sequenceLengthEncoding) {
        this.sequenceLengthEncoding = Objects.requireNonNull(sequenceLengthEncoding);
        return this;
    }

    public byte[] swapBuffer() {
        if (swapBuffer == null) {
            swapBuffer = new byte[BUFFER_LENGTH];
        }
        return swapBuffer;
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

    public DicomWriter writeFileMetaInformation(DicomObject fmi) throws IOException {
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
        write(dcmobj);
    }

    void write(DicomObject dcmObj) throws IOException {
        if (includeGroupLength || itemLengthEncoding.explicit || sequenceLengthEncoding.explicit) {
            dcmObj.calculateItemLength(this);
        }
        dcmObj.writeTo(this);
    }

    public int calculateLengthOf(DicomElement el) {
        return (!encoding.explicitVR || el.vr().shortValueLength ? 8 : 12) + el.calculateValueLength(this);
    }

    public int calculateLengthOf(DicomObject item) {
        return 8 + itemLengthEncoding.adjustLength.applyAsInt(item.calculateItemLength(this));
    }

    public int lengthOf(DicomObject item) {
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

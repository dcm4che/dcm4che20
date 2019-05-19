package org.dcm4che6.io;

import org.dcm4che6.data.*;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.internal.DicomObjectImpl;
import org.dcm4che6.data.VR;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class DicomOutputStream extends OutputStream {

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

    public DicomOutputStream withEncoding(DicomObject fmi) {
        String tsuid = fmi.getString(Tag.TransferSyntaxUID).orElseThrow(
                () -> new IllegalArgumentException("Missing Transfer Syntax UID in File Meta Information"));

        return withEncoding(DicomEncoding.of(tsuid));
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
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
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
        write(header, 0, headerLength);
    }

    public DicomOutputStream writeFileMetaInformation(DicomObject fmi) throws IOException {
        if (encoding != null)
            throw new IllegalStateException("encoding already initialized: " + encoding);

        byte[] b = new byte[132];
        b[128] = 'D';
        b[129] = 'I';
        b[130] = 'C';
        b[131] = 'M';
        write(b);
        encoding = DicomEncoding.EVR_LE;
        boolean includeGroupLength0 = includeGroupLength;
        try {
            includeGroupLength = true;
            ((DicomObjectImpl) fmi).calculateItemLength(this);
            ((DicomObjectImpl) fmi).writeTo(this);
        } finally {
            includeGroupLength = includeGroupLength0;
        }
        return this;
    }

    public void writeDataSet(DicomObject dcmobj) throws IOException {
        if (encoding == null)
            throw new IllegalStateException("encoding not initialized");

        Objects.requireNonNull(dcmobj);
        if (includeGroupLength || itemLengthEncoding.calculate || sequenceLengthEncoding.calculate) {
            ((DicomObjectImpl) dcmobj).calculateItemLength(this);
        }
        ((DicomObjectImpl) dcmobj).writeTo(this);
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
        ((DicomObjectImpl) dcmobj).calculateItemLength(this);
        ((DicomObjectImpl) dcmobj).writeTo(this);
    }

    public byte[] swapBuffer() {
        if (swapBuffer == null) {
            swapBuffer = new byte[BUFFER_LENGTH];
        }
        return swapBuffer;
    }

    public void writeUTF(String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        ByteOrder.LITTLE_ENDIAN.shortToBytes(b.length, header, 0);
        write(header, 0, 2);
        write(b);
    }

    public enum LengthEncoding {
        UNDEFINED_OR_ZERO(false, x -> x != 0, (h, x) -> x == 0 ? h : h + x + 8),
        UNDEFINED(false, x -> true, (h, x) -> h + x + 8),
        EXPLICIT(true, x -> false, (h, x) -> h + x);

        public final boolean calculate;
        public final IntPredicate undefined;
        public final IntBinaryOperator totalLength;

        LengthEncoding(boolean calculate, IntPredicate undefined, IntBinaryOperator totalLength) {
            this.calculate = calculate;
            this.undefined = undefined;
            this.totalLength = totalLength;
        }
    }

}

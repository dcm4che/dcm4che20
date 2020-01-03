package org.dcm4che6.internal;

import org.dcm4che6.data.*;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.OptionalFloat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
class DicomInput {

    final MemoryCache cache;
    final DicomEncoding encoding;

    DicomInput(MemoryCache cache, DicomEncoding encoding) {
        this.cache = cache;
        this.encoding = encoding;
    }

    byte byteAt(long pos) {
        return cache.byteAt(pos);
    }

    short shortAt(long pos) {
        return cache.shortAt(pos, encoding.byteOrder);
    }

    int ushortAt(long pos) {
        return cache.ushortAt(pos, encoding.byteOrder);
    }

    int intAt(long pos) {
        return cache.intAt(pos, encoding.byteOrder);
    }

    long uintAt(long pos) {
        return cache.uintAt(pos, encoding.byteOrder);
    }

    int tagAt(long pos) {
        return cache.tagAt(pos, encoding.byteOrder);
    }

    long longAt(long pos) {
        return cache.longAt(pos, encoding.byteOrder);
    }

    String stringAt(long pos, int len, SpecificCharacterSet cs) {
        return cache.stringAt(pos, len, cs);
    }

    DicomElement dicomElement(DicomObject dcmObj, int tag, VR vr, long valuePos, int valueLength) {
        return new ParsedDicomElement(dcmObj, tag, vr, valuePos, valueLength);
    }

    DataFragment dataFragment(DataFragments dcmElm, long valuePos, int valueLength) {
        return new ParsedDataFragment(dcmElm, valuePos, valueLength);
    }

    private class ParsedDicomElement extends DicomElementImpl {
        final long valuePos;
        final int valueLen;

        private ParsedDicomElement(DicomObject dcmObj, int tag, VR vr, long valuePos, int valueLen) {
            super(dcmObj, tag, vr);
            this.valuePos = valuePos;
            this.valueLen = valueLen;
        }

        @Override
        public long getStreamPosition() {
            return valuePos - (!encoding.explicitVR || vr.shortValueLength ? 8 : 12);
        }

        @Override
        protected StringBuilder promptValueTo(StringBuilder appendTo, int maxLength) {
            appendTo.append(' ').append('[');
            if (vr.type.appendValue(DicomInput.this, valuePos, valueLen, dicomObject,
                    appendTo, maxLength).length() < maxLength) appendTo.append(']');
            return appendTo;
        }

        @Override
        public Optional<String> stringValue(int index) {
            return vr.type.stringValue(DicomInput.this, valuePos, valueLen, index, dicomObject);
        }

        @Override
        public String[] stringValues() {
            return vr.type.stringValues(DicomInput.this, valuePos, valueLen, dicomObject);
        }

        @Override
        public int valueLength() {
            return valueLen;
        }

        @Override
        public OptionalInt intValue(int index) {
            return vr.type.intValue(DicomInput.this, valuePos, valueLen, index);
        }

        @Override
        public int[] intValues() {
            return vr.type.intValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public OptionalFloat floatValue(int index) {
            return vr.type.floatValue(DicomInput.this, valuePos, valueLen, index);
        }

        @Override
        public float[] floatValues() {
            return vr.type.floatValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public OptionalDouble doubleValue(int index) {
            return vr.type.doubleValue(DicomInput.this, valuePos, valueLen, index);
        }

        @Override
        public double[] doubleValues() {
            return vr.type.doubleValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public void writeValueTo(DicomOutputStream dos) throws IOException {
            if (encoding.byteOrder == dos.getEncoding().byteOrder || vr.type.toggleByteOrder() == null) {
                cache.writeBytesTo(valuePos, valueLen, dos);
            } else {
                cache.writeSwappedBytesTo(valuePos, valueLen, dos,
                        vr.type.toggleByteOrder(), dos.swapBuffer());
            }
        }
    }

    private class ParsedDataFragment implements DataFragment {
        final DataFragments dataFragments;
        final long valuePos;
        final int valueLen;

        ParsedDataFragment(DataFragments dataFragments, long valuePos, int valueLen) {
            this.dataFragments = dataFragments;
            this.valuePos = valuePos;
            this.valueLen = valueLen;
        }

        @Override
        public DicomElement containedBy() {
            return dataFragments;
        }
        
        @Override
        public long valuePosition() {
            return valuePos;
        }

        @Override
        public int valueLength() {
            return valueLen;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            cache.writeBytesTo(valuePos, valueLen, out);
        }

        @Override
        public StringBuilder promptTo(StringBuilder appendTo, int maxLength) {
            appendTo.append(">(FFFE,E000) #").append(valueLen).append(' ').append('[');
            if (dataFragments.vr.type.appendValue(DicomInput.this, valuePos, valueLen, null,
                    appendTo, maxLength).length() < maxLength)
                appendTo.append(']');
            return appendTo;
        }
    }
}

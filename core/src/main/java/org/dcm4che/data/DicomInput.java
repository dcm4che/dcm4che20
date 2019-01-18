package org.dcm4che.data;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
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

    DicomObject item(DicomSequence dcmElm, long valuePos, int valueLength, ArrayList<DicomElement> elements) {
        return new DicomObject(dcmElm, this, valuePos, valueLength, elements);
    }

    DataFragment dataFragment(DataFragments dcmElm, long valuePos, int valueLength) {
        return new ParsedDataFragment(dcmElm, valuePos, valueLength);
    }

    private class ParsedDicomElement extends BaseDicomElement {
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
        public String stringValue(int index, String defaultValue) {
            return vr.type.stringValue(DicomInput.this, valuePos, valueLen, index, dicomObject, defaultValue);
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
        public int intValue(int index, int defaultValue) {
            return vr.type.intValue(DicomInput.this, valuePos, valueLen, index, defaultValue);
        }

        @Override
        public int[] intValues() {
            return vr.type.intValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public float floatValue(int index, float defaultValue) {
            return vr.type.floatValue(DicomInput.this, valuePos, valueLen, index, defaultValue);
        }

        @Override
        public float[] floatValues() {
            return vr.type.floatValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public double doubleValue(int index, double defaultValue) {
            return vr.type.doubleValue(DicomInput.this, valuePos, valueLen, index, defaultValue);
        }

        @Override
        public double[] doubleValues() {
            return vr.type.doubleValues(DicomInput.this, valuePos, valueLen);
        }

        @Override
        public void writeTo(DicomOutputStream dicomWriter) throws IOException {
            dicomWriter.writeHeader(tag, vr, valueLen);
            if (encoding.byteOrder == dicomWriter.getEncoding().byteOrder || vr.type.toggleByteOrder() == null) {
                cache.writeBytesTo(valuePos, valueLen, dicomWriter.getOutputStream());
            } else {
                cache.writeSwappedBytesTo(valuePos, valueLen, dicomWriter.getOutputStream(),
                        vr.type.toggleByteOrder(), dicomWriter.swapBuffer());
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
        public DataFragments containedBy() {
            return dataFragments;
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

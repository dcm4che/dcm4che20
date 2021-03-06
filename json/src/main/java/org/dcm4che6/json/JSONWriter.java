package org.dcm4che6.json;

import org.dcm4che6.data.*;
import org.dcm4che6.data.DataFragment;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.PersonName;
import org.dcm4che6.util.TagUtils;

import javax.json.stream.JsonGenerator;
import java.io.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jan 2019
 */
public class JSONWriter implements DicomInputHandler {

    private static final int BASE64_CHUNK_LENGTH = 256 * 3;
    private static final int BUFFER_LENGTH = 256 * 4;
    private static final byte[] INLINEBINARY = {
            ',', '"', 'I', 'n', 'l', 'i', 'n', 'e', 'B', 'i', 'n', 'a', 'r', 'y', '"', ':', '"'
    };

    private final JsonGenerator gen;
    private final OutputStream out;
    private InlineBinary inlineBinary;
    private boolean suppressEndElement;

    public JSONWriter(JsonGenerator gen, OutputStream out) {
        this.gen = gen;
        this.out = out;
    }

    public void writeDataSet(DicomObject dcmobj) throws IOException {
        gen.writeStartObject();
        writeElements(dcmobj);
        gen.writeEnd();
    }

    public void writeElements(DicomObject dcmobj) throws IOException {
        for (DicomElement dcmElm : dcmobj) {
            int tag = dcmElm.tag();
            if (!TagUtils.isGroupLength(tag)) {
                startElement(null, dcmElm, dcmElm.bulkDataURI());
                if (dcmElm.vr() == VR.SQ && !dcmElm.isEmpty()) {
                    gen.writeStartArray("Value");
                    dcmElm.forEachItem((item, number) -> writeDataSet(item));
                    gen.writeEnd();
                }
                gen.writeEnd();
            }
        }
    }

    @Override
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        int tag = dcmElm.tag();
        if (TagUtils.isPrivateCreator(tag) || tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet) {
            dis.loadValueFromStream();
            dcmElm.containedBy().add(dcmElm);
        }
        String bulkDataURI = null;
        if (!(suppressEndElement = bulkData
                ? (bulkDataURI = dis.bulkDataURI()) == null
                : TagUtils.isGroupLength(tag))) {
            startElement(dis, dcmElm, bulkDataURI);
        }
        return true;
    }

    private void startElement(DicomInputStream dis, DicomElement dcmElm, String bulkDataURI)
            throws IOException {
        VR vr = dcmElm.vr();
        gen.writeStartObject(TagUtils.toHexString(dcmElm.tag()));
        gen.write("vr", vr.name());
        if (!dcmElm.isEmpty()) {
            if (bulkDataURI != null) {
                gen.write("BulkDataURI", bulkDataURI);
            } else if (vr.jsonType == VR.JSONType.BASE64) {
                writeInlineBinary(dis, dcmElm);
            } else {
                if (dis != null)
                    dis.loadValueFromStream();
                gen.writeStartArray("Value");
                switch (vr.jsonType) {
                    case PN:
                        dcmElm.forEachStringValue(this::writePN);
                        break;
                    case STRING:
                        dcmElm.forEachStringValue(this::writeString);
                        break;
                    case INT:
                        dcmElm.forEachIntValue((IntConsumer) gen::write);
                        break;
                    case UINT:
                        dcmElm.forEachIntValue(this::writeUInt);
                        break;
                    case DOUBLE:
                        dcmElm.forEachDoubleValue((DoubleConsumer) gen::write);
                        break;
                }
                gen.writeEnd();
            }
        }
    }

    private void writeInlineBinary(DicomInputStream dis, DicomElement dcmElm) throws IOException {
        gen.flush();
        out.write(INLINEBINARY);
        if (inlineBinary == null)
            inlineBinary = new InlineBinary();

        if (dis != null) {
            if (dcmElm.valueLength() != -1)
                dis.skipBytes(0, dcmElm.valueLength(), inlineBinary);
        } else {
            dcmElm.writeValueTo(new DicomOutputStream(inlineBinary));
            inlineBinary.finish();
            out.write('"');
        }
    }

    private void writeString(String value, int number) {
        gen.write(value);
    }

    private void writePN(String value, int number) {
        PersonName pn = PersonName.parse(value);
        if (!pn.isEmpty()) {
            gen.writeStartObject();
            writePNGroup("Alphabetic", pn.alphabetic);
            writePNGroup("Ideographic", pn.ideographic);
            writePNGroup("Phonetic", pn.phonetic);
            gen.writeEnd();
        }
    }

    private void writeUInt(int i) {
        gen.write(i & 0xffffffffL);
    }

    private void writePNGroup(String name, PersonName.Group group) {
        if (!group.isEmpty())
            gen.write(name, group.toString());
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        if (suppressEndElement)
            return true;

        if (!bulkData && dcmElm.vr().jsonType == VR.JSONType.BASE64) {
            if (dcmElm.valueLength() == -1)
                dis.skipBytes(-8, 8, inlineBinary);
            inlineBinary.finish();
            out.write('"');
        }
        if (dcmElm.vr() == VR.SQ && !dcmElm.isEmpty()) {
            gen.writeEnd();
        }
        gen.writeEnd();
        return true;
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) throws IOException {
        if (dcmSeq.isEmpty()) {
            gen.writeStartArray("Value");
        }
        gen.writeStartObject();
        dcmSeq.addItem(dcmObj);
        return true;
    }

    @Override
    public boolean endItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) throws IOException {
        gen.writeEnd();
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DicomElement fragments, DataFragment dataFragment)
            throws IOException {
        dis.skipBytes(-8, 8 + dataFragment.valueLength(), inlineBinary);
        return true;
    }

    private class InlineBinary extends OutputStream {
        final byte[] src = new byte[BASE64_CHUNK_LENGTH];
        final byte[] dst = new byte[BUFFER_LENGTH];
        int pos;

        @Override
        public void write(int b) throws IOException {
            src[pos++] = (byte) b;
            encodeIfFull();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int n;
            while (len > 0) {
                n = Math.min(len, BASE64_CHUNK_LENGTH - pos);
                System.arraycopy(b, off, src, pos, n);
                pos += n;
                off += n;
                len -= n;
                encodeIfFull();
            }
        }

        void finish() throws IOException {
            if (pos > 0)
                encode(Arrays.copyOf(src, pos));
        }

        private void encodeIfFull() throws IOException {
            if (pos == BASE64_CHUNK_LENGTH)
                encode(src);
        }

        private void encode(byte[] src) throws IOException {
            int length = Base64.getEncoder().encode(src, dst);
            out.write(dst, 0, length);
            pos = 0;
        }
    }
}

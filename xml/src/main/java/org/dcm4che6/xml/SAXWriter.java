package org.dcm4che6.xml;

import org.dcm4che6.data.*;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.PersonName;
import org.dcm4che6.util.TagUtils;
import org.dcm4che6.util.function.StringValueConsumer;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
public class SAXWriter implements DicomInputHandler {
    private static final String NAMESPACE = "http://dicom.nema.org/PS3.19/models/NativeDICOM";
    private static final int BASE64_CHUNK_LENGTH = 256 * 3;
    private static final int BUFFER_LENGTH = 256 * 4;

    private boolean includeKeyword = true;
    private String namespace = "";

    private final ContentHandler handler;
    private final AttributesImpl atts = new AttributesImpl();
    private final char[] ch = new char[BUFFER_LENGTH];
    private InlineBinary inlineBinary;
    private boolean suppressEndElement;

    public SAXWriter(ContentHandler handler) {
        this.handler = handler;
    }

    public boolean isIncludeKeyword() {
        return includeKeyword;
    }

    public SAXWriter withIncludeKeyword(boolean includeKeyword) {
        this.includeKeyword = includeKeyword;
        return this;
    }

    public boolean isIncludeNamespaceDeclaration() {
        return namespace == NAMESPACE;
    }

    public SAXWriter withIncludeNamespaceDeclaration(boolean includeNameSpaceDeclaration) {
        this.namespace = includeNameSpaceDeclaration ? NAMESPACE : "";
        return this;
    }

    public void startDocument() throws SAXException {
        handler.startDocument();
        startElement("NativeDicomModel", "xml:space", "preserve");
    }

    public void endDocument() throws SAXException {
        endElement("NativeDicomModel");
        handler.endDocument();
    }

    public void writeDataSet(DicomObject dcmobj) throws SAXException {
        startDocument();
        writeElements(dcmobj);
        endDocument();
    }

    public void writeElements(DicomObject dcmobj) throws SAXException {
        for (DicomElement dcmElm : dcmobj) {
            int tag = dcmElm.tag();
            if (!isGroupLengthOrPrivateCreator(tag)) {
                try {
                    startElement(null, dcmElm, dcmElm.bulkDataUUID(), dcmElm.bulkDataURI());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                dcmElm.forEachItem(this::writeItem);
                endElement("DicomAttribute");
            }
        }
    }

    private void writeItem(DicomObject dcmobj, int number) throws SAXException {
        startElement("Item", "number", Integer.toString(number));
        writeElements(dcmobj);
        endElement("Item");
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
                : isGroupLengthOrPrivateCreator(tag))) {
            try {
                startElement(dis, dcmElm, null, bulkDataURI);
            } catch (SAXException e) {
                throw new IOException(e);
            }
        }
        return true;
    }

    private void startElement(DicomInputStream dis, DicomElement dcmElm, String bulkDataUUID, String bulkDataURI)
            throws SAXException, IOException {
        int tag = dcmElm.tag();
        VR vr = dcmElm.vr();
        Optional<String> privateCreator = dcmElm.containedBy().getPrivateCreator(tag);
        if (privateCreator.isPresent()) {
            addAttribute("privateCreator", privateCreator.get());
            tag &= 0xffff00ff;
        }
        addAttribute("tag", TagUtils.toHexString(tag));
        if (includeKeyword) {
            String keyword = ElementDictionary.keywordOf(dcmElm.tag(), privateCreator);
            if (keyword != null && !keyword.isEmpty())
                addAttribute("keyword", keyword);
        }
        addAttribute("vr", vr.name());
        startElement("DicomAttribute");
        if (bulkDataUUID != null || bulkDataURI != null) {
            writeBulkData(bulkDataUUID, bulkDataURI);
        } else if (vr.jsonType == VR.JSONType.BASE64) {
            writeInlineBinary(dis, dcmElm);
        } else {
            if (dis != null)
                dis.loadValueFromStream();
            dcmElm.forEachStringValue(vr == VR.PN
                    ? (StringValueConsumer<SAXException>) this::writePN
                    : (StringValueConsumer<SAXException>) this::writeValue);
        }
    }

    private void writeBulkData(String bulkDataUUID, String bulkDataURI) throws SAXException {
        if (bulkDataUUID != null)
            addAttribute("uuid", bulkDataUUID);
        if (bulkDataURI != null)
            addAttribute("uri", bulkDataURI);
        startElement("BulkData");
        endElement("BulkData");
    }

    private void writeInlineBinary(DicomInputStream dis, DicomElement dcmElm) throws SAXException, IOException {
        startElement("InlineBinary");
        if (inlineBinary == null)
            inlineBinary = new InlineBinary();

        if (dis != null) {
            if (dcmElm.valueLength() != -1)
                dis.skipBytes(0, dcmElm.valueLength(), inlineBinary);
        } else {
            dcmElm.writeValueTo(new DicomOutputStream(inlineBinary));
            inlineBinary.finish();
            endElement("InlineBinary");
        }
    }

    private void writeValue(String s, int number) throws SAXException {
        addAttribute("number", Integer.toString(number));
        writeElement("Value", s);
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        if (suppressEndElement)
            return true;

        try {
            if (!bulkData && dcmElm.vr().jsonType == VR.JSONType.BASE64) {
                if (dcmElm.valueLength() == -1)
                    dis.skipBytes(-8, 8, inlineBinary);
                inlineBinary.finish();
                endElement("InlineBinary");
            }
            endElement("DicomAttribute");
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return true;
    }

    private static boolean isGroupLengthOrPrivateCreator(int tag) {
        return TagUtils.isGroupLength(tag) || TagUtils.isPrivateCreator(tag);
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) throws IOException {
        try {
            startElement("Item", "number", Integer.toString(dcmSeq.size() + 1));
        } catch (SAXException e) {
            throw new IOException(e);
        }
        dcmSeq.addItem(dcmObj);
        return true;
    }

    @Override
    public boolean endItem(DicomInputStream dis, DicomElement dcmSeq, DicomObject dcmObj) throws IOException {
        try {
            endElement("Item");
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DicomElement fragments, DataFragment dataFragment) throws IOException {
        dis.skipBytes(-8, 8 + dataFragment.valueLength(), inlineBinary);
        return true;
    }

    private void startElement(String name, String attrName, String attrValue) throws SAXException {
        addAttribute(attrName, attrValue);
        startElement(name);
    }

    private void startElement(String name) throws SAXException {
        handler.startElement(namespace, name, name, atts);
        atts.clear();
    }

    private void endElement(String name) throws SAXException {
        handler.endElement(namespace, name, name);
    }

    private void addAttribute(String name, String value) {
        atts.addAttribute(namespace, name, name, "NMTOKEN", value);
    }

    private void writeElement(String qname, String s) throws SAXException {
        if (!s.isEmpty()) {
            startElement(qname);
            char[] buf = ch;
            for (int off = 0, totlen = s.length(); off < totlen;) {
                int len = Math.min(totlen - off, buf.length);
                s.getChars(off, off += len, buf, 0);
                handler.characters(buf, 0, len);
            }
            endElement(qname);
        }
    }

    private void writePN(String s, int number) throws SAXException {
        PersonName pn = PersonName.parse(s);
        if (!pn.isEmpty()) {
            startElement("PersonName", "number", Integer.toString(number));
            writePNGroup("Alphabetic", pn.alphabetic);
            writePNGroup("Ideographic", pn.ideographic);
            writePNGroup("Phonetic", pn.phonetic);
            endElement("PersonName");
        }
    }

    private void writePNGroup(String qname, PersonName.Group group) throws SAXException {
        if (!group.isEmpty()) {
            startElement(qname);
            writeElement("FamilyName", group.familyName);
            writeElement("GivenName", group.givenName);
            writeElement("MiddleName", group.middleName);
            writeElement("NamePrefix", group.namePrefix);
            writeElement("NameSuffix", group.nameSuffix);
            endElement(qname);
        }
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
            for (int i = 0; i < length; i++) {
                ch[i] = (char) dst[i];
            }
            try {
                handler.characters(ch, 0, length);
            } catch (SAXException e) {
                throw new IOException(e);
            }
            pos = 0;
        }
    }
}

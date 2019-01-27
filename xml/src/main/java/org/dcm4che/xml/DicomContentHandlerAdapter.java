package org.dcm4che.xml;

import org.dcm4che.data.*;
import org.dcm4che.util.TagUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
public class DicomContentHandlerAdapter implements DicomInputHandler {
    private static final String NAMESPACE = "http://dicom.nema.org/PS3.19/models/NativeDICOM";
    private static final int BASE64_CHUNK_LENGTH = 256 * 3;
    private static final int BUFFER_LENGTH = 256 * 4;

    private boolean includeKeyword = true;
    private String namespace = "";

    private final ContentHandler handler;
    private final AttributesImpl atts = new AttributesImpl();
    private final char[] ch = new char[BUFFER_LENGTH];
    private InlineBinary inlineBinary;
    private String bulkDataURI;

    public DicomContentHandlerAdapter(ContentHandler handler) {
        this.handler = handler;
    }

    public boolean isIncludeKeyword() {
        return includeKeyword;
    }

    public DicomContentHandlerAdapter withIncludeKeyword(boolean includeKeyword) {
        this.includeKeyword = includeKeyword;
        return this;
    }

    public boolean isIncludeNamespaceDeclaration() {
        return namespace == NAMESPACE;
    }

    public DicomContentHandlerAdapter withIncludeNamespaceDeclaration(boolean includeNameSpaceDeclaration) {
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

    @Override
    public boolean startElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        if (bulkData) {
            bulkDataURI = dis.bulkDataURI();
            dis.skipBulkData();
            if (bulkDataURI == null)
                return true;
        } else {
            bulkDataURI = null;
        }
        int tag = dcmElm.tag();
        VR vr = dcmElm.vr();
        String privateCreator = dcmElm.containedBy().getPrivateCreator(tag);
        if (privateCreator != null) {
            addAttribute("privateCreator", privateCreator);
            tag &= 0xffff00ff;
        }
        addAttribute("tag", TagUtils.toHexString(tag));
        if (includeKeyword) {
            String keyword = ElementDictionary.keywordOf(dcmElm.tag(), privateCreator);
            if (keyword != null && !keyword.isEmpty())
                addAttribute("keyword", keyword);
        }
        addAttribute("vr", vr.name());
        try {
            startElement("DicomAttribute");
            if (bulkData) {
                writeBulkData();
            } else switch (vr) {
                case SQ:
                    break;
                case OB:
                case OD:
                case OF:
                case OL:
                case OW:
                    writeInlineBinary(dis, dcmElm);
                    break;
                default:
                    writeValues(vr, dis.iterateStringValues(dcmElm));
                    break;
            }
        } catch (SAXException e) {
            throw new IOException(e);
        }
        if (TagUtils.isPrivateCreator(tag) || tag == Tag.TransferSyntaxUID || tag == Tag.SpecificCharacterSet) {
            dcmElm.containedBy().setString(tag, vr, dcmElm.stringValues());
        }
        return true;
    }

    private void writeBulkData() throws SAXException {
        startElement("BulkData", "uri", bulkDataURI);
        endElement("BulkData");
    }

    private void writeInlineBinary(DicomInputStream dis, DicomElement dcmElm) throws SAXException, IOException {
        startElement("InlineBinary");
        if (inlineBinary == null)
            inlineBinary = new InlineBinary();

        if (dcmElm.valueLength() == -1) {
            return;
        }
        dis.writeValueTo(dcmElm, inlineBinary.dos);
        inlineBinary.finish();
        endElement("InlineBinary");
    }

    private void writeValues(VR vr, Iterator<String> iter) throws SAXException {
        int n = 0;
        while (iter.hasNext()) {
            writeValue(vr, iter.next(), ++n);
        }
    }

    private void writeValue(VR vr, String s, int num) throws SAXException {
        addAttribute("number", Integer.toString(num));
        if (vr == VR.PN)
            writePN(PersonName.parse(s));
        else
            writeElement("Value", s);
    }

    @Override
    public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
        try {
            if (bulkData) {
                if (bulkDataURI == null)
                    return true;
            } else if (dcmElm.valueLength() == -1 && dcmElm.vr() != VR.SQ) {
                dis.skipBytes(-8, 8, inlineBinary);
                endElement("InlineBinary");
            }
            endElement("DicomAttribute");
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return true;
    }

    @Override
    public boolean startItem(DicomInputStream dis, DicomObject dcmObj) throws IOException {
        try {
            startElement("Item", "number", Integer.toString(dcmObj.containedBy().size() + 1));
        } catch (SAXException e) {
            throw new IOException(e);
        }
        dcmObj.containedBy().addItem(dcmObj);
        return true;
    }

    @Override
    public boolean endItem(DicomInputStream dis, DicomObject dcmObj) throws IOException {
       try {
            endElement("Item");
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return true;
    }

    @Override
    public boolean dataFragment(DicomInputStream dis, DataFragment dataFragment) throws IOException {
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

    private void writePN(PersonName pn) throws SAXException {
        if (!pn.isEmpty()) {
            startElement("PersonName");
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

    private class InlineBinary extends OutputStream{
        final byte[] src = new byte[BASE64_CHUNK_LENGTH];
        final byte[] dst = new byte[BUFFER_LENGTH];
        final Base64.Encoder base64Encoder = Base64.getEncoder();
        final DicomOutputStream dos = new DicomOutputStream(this).withEncoding(DicomEncoding.EVR_LE);
        int count;

        @Override
        public void write(int b) throws IOException {
            src[count++] = (byte) b;
            encodeIfFull();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int n;
            while (len > 0) {
                n = Math.min(len, BASE64_CHUNK_LENGTH - count);
                System.arraycopy(b, off, src, count, n);
                count += n;
                off += n;
                len -= n;
                encodeIfFull();
            }
        }

        void finish() throws IOException {
            if (count > 0)
                encode(Arrays.copyOf(src, count));
        }

        private void encodeIfFull() throws IOException {
            if (count == BASE64_CHUNK_LENGTH)
                encode(src);
        }

        private void encode(byte[] src) throws IOException {
            int length = base64Encoder.encode(src, dst);
            for (int i = 0; i < length; i++) {
                ch[i] = (char) dst[i];
            }
            try {
                handler.characters(ch, 0, length);
            } catch (SAXException e) {
                throw new IOException(e);
            }
            count = 0;
        }
    }
}

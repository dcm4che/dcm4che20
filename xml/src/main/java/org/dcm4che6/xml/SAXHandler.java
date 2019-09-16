package org.dcm4che6.xml;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.PersonName;
import org.dcm4che6.util.TagUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2019
 */
public class SAXHandler extends DefaultHandler {
    private DicomObject dicomObject;
    private DicomObject fmi;
    private DicomElement dicomSequence;
    private String privateCreator;
    private int tag;
    private VR vr;
    private final StringBuffer sb = new StringBuffer();
    private final List<String> strings = new ArrayList<>();
    private final List<byte[]> byteArrays = new ArrayList<>();
    private final byte[] leftoverBytes = new byte[3];
    private int leftover;
    private final PersonName.Builder personNameBuilder = new PersonName.Builder();
    private PersonName.Group.Builder personNameGroupBuilder;
    private CharsConsumer onCharacters = SAXHandler::noop;

    private Runnable onEndDicomAttribute;

    public SAXHandler(DicomObject dicomObject) {
        this.dicomObject = Objects.requireNonNull(dicomObject);
    }

    public DicomObject getFileMetaInformation() {
        return fmi;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
            case "DicomAttribute":
                startDicomAttribute(attributes);
                break;
            case "Item":
                startItem(attributes);
                break;
            case "PersonName":
                addEmptyValues(attributes);
                personNameBuilder.reset();
                break;
            case "Value":
                addEmptyValues(attributes);
                startText();
                break;
            case "BulkData":
                bulkData(attributes);
                break;
            case "InlineBinary":
                startInlineBinary();
                break;
            case "Alphabetic":
                personNameGroupBuilder = personNameBuilder.alphabetic;
                break;
            case "Ideographic":
                personNameGroupBuilder = personNameBuilder.ideographic;
                break;
            case "Phonetic":
                personNameGroupBuilder = personNameBuilder.phonetic;
                break;
            case "FamilyName":
            case "GivenName":
            case "MiddleName":
            case "NamePrefix":
            case "NameSuffix":
                startText();
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        onCharacters = SAXHandler::noop;
        switch (qName) {
            case "DicomAttribute":
                onEndDicomAttribute.run();
                dicomSequence = dicomObject.containedBy().orElse(null);
                break;
            case "Item":
                dicomObject = dicomSequence.containedBy();
                onEndDicomAttribute = SAXHandler::noop;
                break;
            case "PersonName":
                strings.add(personNameBuilder.build().toString());
                break;
            case "Value":
                strings.add(sb.toString());
                break;
            case "FamilyName":
                personNameGroupBuilder.withFamilyName(sb.toString());
                break;
            case "GivenName":
                personNameGroupBuilder.withGivenName(sb.toString());
                break;
            case "MiddleName":
                personNameGroupBuilder.withMiddleName(sb.toString());
                break;
            case "NamePrefix":
                personNameGroupBuilder.withNamePrefix(sb.toString());
                break;
            case "NameSuffix":
                personNameGroupBuilder.withNameSuffix(sb.toString());
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        onCharacters.accept(ch, start, length);
    }

    private static void noop(char[] ch, int start, int length) {
    }

    private static void noop() {
    }

    private void addEmptyValues(Attributes attributes) {
        int add = Integer.parseInt(attributes.getValue("number")) - strings.size();
        while (--add > 0)
            strings.add("");
        onEndDicomAttribute = this::setString;
    }

    private void startText() {
        sb.setLength(0);
        onCharacters = sb::append;
    }

    private void startInlineBinary() {
        byteArrays.clear();
        onCharacters = this::decodeBase64;
        onEndDicomAttribute = this::setBytes;
    }

    private void startItem(Attributes attributes) {
        if (dicomSequence == null)
            dicomSequence = dicomObject.newDicomSequence(privateCreator, tag);
        int add = Integer.parseInt(attributes.getValue("number")) - dicomSequence.size();
        while (add-- > 0)
            dicomSequence.addItem(dicomObject = DicomObject.newDicomObject());
    }

    private void bulkData(Attributes attributes) {
        dicomObject.setBulkData(privateCreator, tag, vr,
                attributes.getValue("uri"),
                attributes.getValue("uuid"));
        onEndDicomAttribute = SAXHandler::noop;
    }

    private void startDicomAttribute(Attributes attributes) {
        tag = (int) Long.parseLong(attributes.getValue("tag"), 16);
        privateCreator = attributes.getValue("privateCreator");
        vr = VR.valueOf(attributes.getValue("vr"));
        dicomSequence = null;
        onEndDicomAttribute = this::setNull;
        strings.clear();
    }

    @FunctionalInterface
    private interface CharsConsumer {
        void accept(char[] ch, int start, int length);
    }

    private void decodeBase64(char[] ch, int start, int length) {
        int prevLeftover = leftover;
        leftover = (prevLeftover + length) & 3;
        byte[] src = new byte[prevLeftover + length - leftover];
        System.arraycopy(leftoverBytes, 0, src, 0, prevLeftover);
        int j = start;
        for (int i = prevLeftover; i < src.length;) {
            src[i++] = (byte) ch[j++];
        }
        for (int i = 0; i < leftover;) {
            leftoverBytes[i++] = (byte) ch[j++];
        }
        byteArrays.add(Base64.getDecoder().decode(src));
    }

    private DicomObject dicomObject() {
        return TagUtils.isFileMetaInformation(tag) ? fmi() : dicomObject;
    }

    private DicomObject fmi() {
        if (fmi == null)
            fmi = DicomObject.newDicomObject();
        return fmi;
    }

    private void setNull() {
        dicomObject().setNull(privateCreator, tag, vr);
    }

    private void setString() {
        dicomObject().setString(privateCreator, tag, vr, strings.toArray(new String[0]));
    }

    private void setBytes() {
        dicomObject().setBytes(privateCreator, tag, vr, cat(byteArrays));
    }

    private static byte[] cat(List<byte[]> binary) {
        if (binary.size() == 1)
            return binary.remove(0);

        byte[] dst = new byte[binary.stream().mapToInt(b -> b.length).sum()];
        int off = 0;
        do {
            byte[] src = binary.remove(0);
            System.arraycopy(src, 0, dst, off, src.length);
            off += src.length;
        } while (!binary.isEmpty());
        return dst;
    }
}

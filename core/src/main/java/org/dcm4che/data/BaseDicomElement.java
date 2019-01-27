package org.dcm4che.data;

import org.dcm4che.util.TagUtils;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
class BaseDicomElement implements DicomElement {
    protected final DicomObject dicomObject;
    protected final int tag;
    protected final VR vr;

    BaseDicomElement(DicomObject dicomObject, int tag, VR vr) {
        this.dicomObject = dicomObject;
        this.tag = tag;
        this.vr = vr;
    }

    @Override
    public int tag() {
        return tag;
    }

    @Override
    public VR vr() {
        return vr;
    }

    @Override
    public DicomObject containedBy() {
        return dicomObject;
    }

    @Override
    public int valueLength() {
        return 0;
    }

    public void writeValueTo(DicomOutputStream dos) throws IOException {
    }

    @Override
    public String toString() {
        return promptTo(new StringBuilder(), 80).toString();
    }

    @Override
    public StringBuilder promptTo(StringBuilder appendTo, int maxLength) {
        dicomObject.appendNestingLevel(appendTo)
                .append(TagUtils.toString(tag))
                .append(' ').append(vr).append(' ')
                .append('#').append(valueLength());
        if (promptValueTo(appendTo, maxLength).length() < maxLength) {
            appendTo.append(' ').append(ElementDictionary.keywordOf(tag, dicomObject.getPrivateCreator(tag)));
            if (appendTo.length() > maxLength)
                appendTo.setLength(maxLength);
        }
        return appendTo;
    }

    protected StringBuilder promptValueTo(StringBuilder appendTo, int maxLength) {
        return appendTo;
    }
}

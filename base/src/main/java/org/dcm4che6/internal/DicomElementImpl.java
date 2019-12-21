package org.dcm4che6.internal;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.ElementDictionary;
import org.dcm4che6.data.VR;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.util.TagUtils;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
class DicomElementImpl implements DicomElement {
    protected final DicomObject dicomObject;
    protected final int tag;
    protected final VR vr;

    DicomElementImpl(DicomObject dicomObject, int tag, VR vr) {
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

    int elementLength(DicomOutputStream dos) {
        return dos.getEncoding().headerLength(vr) + valueLength();
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

    StringBuilder promptValueTo(StringBuilder appendTo, int maxLength) {
        return appendTo;
    }

    @Override
    public int promptItemsTo(StringBuilder appendTo, int maxWidth, int maxLines) {
        return maxLines;
    }

}

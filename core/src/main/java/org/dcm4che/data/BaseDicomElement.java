package org.dcm4che.data;

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

    @Override
    public void writeTo(DicomWriter writer) throws IOException {
        writer.writeHeader(tag, vr, 0);
    }
}

package org.dcm4che6.util;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public final class Code {
    private final String[] values;

    public Code(String ce) {
        this(StringUtils.split(ce, ce.length(), '^'));
    }

    private Code(String... values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return StringUtils.join(values, 0, values.length, '^');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Code code = (Code) o;
        return Arrays.equals(values, code.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    public DicomObject toItem() {
        DicomObject item = DicomObject.newDicomObject();
        item.setString(Tag.CodeValue, VR.SH, values[0]);
        item.setString(Tag.CodeMeaning, VR.LO, values[1]);
        item.setString(Tag.CodingSchemeDesignator, VR.SH, values[2]);
        return item;
    }
}

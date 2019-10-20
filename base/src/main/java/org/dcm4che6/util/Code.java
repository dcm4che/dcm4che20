package org.dcm4che6.util;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;

import java.net.MalformedURLException;
import java.net.URL;
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
        switch (values.length) {
            case 0:
            case 1:
                throw new IllegalArgumentException("Less then 2 code components");
            case 2:
                if (!isURN(values[0]))
                    throw new IllegalArgumentException("Missing Coding Scheme Designator");
            case 3:
            case 4:
                if (isURN(values[0]))
                    throw new IllegalArgumentException("URN Code Value with Coding Scheme Designator");
                break;
            default:
                throw new IllegalArgumentException("More then 4 code components");
        }
        this.values = values;
    }

    private boolean isURN(String value) {
        if (value.indexOf(':') > 0) {
            try {
                if (!value.startsWith("urn:")) {
                    new URL(value);
                }
                return true;
            } catch (MalformedURLException e) {}
        }
        return false;
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
        if (values.length < 3) {
            item.setString(Tag.URNCodeValue, VR.UR, values[0]);
        } else {
            if (values[0].length() > 16) {
                item.setString(Tag.LongCodeValue, VR.UC, values[0]);
            } else {
                item.setString(Tag.CodeValue, VR.SH, values[0]);
            }
            item.setString(Tag.CodingSchemeDesignator, VR.SH, values[2]);
            if (values.length > 3) {
                item.setString(Tag.CodingSchemeVersion, VR.SH, values[2]);
            }
        }
        item.setString(Tag.CodeMeaning, VR.LO, values[1]);
        return item;
    }
}

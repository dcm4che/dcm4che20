package org.dcm4che6.tool.upsrs;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.ElementDictionary;
import org.dcm4che6.util.StringUtils;
import org.dcm4che6.util.TagUtils;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class TagPath {
    private final int[] tags;

    public TagPath(String s) {
        StringUtils.requireNonEmpty(s);
        String[] ss = StringUtils.split(s, s.length(),'.');
        int[] tags = new int[ss.length];
        for (int i = 0; i < ss.length; i++) {
            tags[i] = TagUtils.forName(ss[i]);
        }
        this.tags = tags;
    }

    public void setString(DicomObject dcmobj, String value) {
        DicomObject item = dcmobj;
        int last = tags.length - 1;
        int i = 0;
        while (i < last) {
            DicomElement seq = dcmobj.get(tags[i]).orElseGet(() -> dcmobj.newDicomSequence(tags[i]));
            item = seq.isEmpty() ? seq.addItem(DicomObject.newDicomObject()) : seq.getItem(0);
        }
        item.setString(tags[last], ElementDictionary.standardElementDictionary().vrOf(tags[last]), value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagPath tagPath = (TagPath) o;
        return Arrays.equals(tags, tagPath.tags);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(tags);
    }
}

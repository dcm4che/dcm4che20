package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class BulkDataElement extends BaseDicomElement {
    private final String uri;

    public BulkDataElement(DicomObject dicomObject, int tag, VR vr, String uri) {
        super(dicomObject, tag, vr);
        this.uri = uri;
    }

    @Override
    public int valueLength() {
        return parseInt("length=", -1);
    }

    @Override
    public String bulkDataURI() {
        return uri;
    }

    int offset() {
        return parseInt("offset=", 0);
    }

    private String cut(String name) {
        int hashIndex = uri.indexOf('#');
        if (hashIndex < 0)
            return null;

        int nameIndex = uri.indexOf(name, hashIndex + 1);
        if (nameIndex < 0)
            return null;

        int begin = nameIndex + name.length();
        int end = uri.indexOf('&', begin + 1);
        return end < 0 ? uri.substring(begin) : uri.substring(begin, end);
    }

    private int parseInt(String name, int defval) {
        String s = cut(name);
        if (s == null)
            return defval;

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defval;
        }
    }
}

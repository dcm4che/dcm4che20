package org.dcm4che.data;

import java.io.*;
import java.net.URI;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public class BulkDataElement extends BaseDicomElement {

    static final int MAGIC_LEN = 0xfbfb;

    private final String uuid;
    private final String uri;

    public BulkDataElement(DicomObject dicomObject, int tag, VR vr, String uri, String uuid) {
        super(dicomObject, tag, vr);
        this.uri = uri;
        this.uuid = uuid;
    }

    @Override
    public int valueLength() {
        return parseInt("length=", -1);
    }

    @Override
    public int valueLength(DicomOutputStream dos) {
        return dos.getEncoding() == DicomEncoding.SERIALIZE ? MAGIC_LEN : valueLength();
    }

    public String bulkDataURI() {
        return uri;
    }

    public String bulkDataUUID() {
        return uuid;
    }

    @Override
    public void writeValueTo(DicomOutputStream dos) throws IOException {
        if (dos.getEncoding() == DicomEncoding.SERIALIZE)
            dos.writeUTF(uri);
        else
            transferTo(dos);
    }

    int offset() {
        return parseInt("offset=", 0);
    }

    ByteOrder byteOrder() {
        return "big".equals(cut("endian=")) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    private void transferTo(DicomOutputStream dos) throws IOException {
        int vallen = valueLength();
        ByteOrder byteOrder = byteOrder();
        ToggleByteOrder toggleByteOrder = dos.getEncoding().byteOrder != byteOrder
                ? vr.type.toggleByteOrder()
                : null;
        byte[] buf = dos.swapBuffer();
        try (InputStream in = URI.create(uri).toURL().openStream()) {
            skipNBytes(in, offset());
            if (vallen == -1) {
                PushbackInputStream pushback = new PushbackInputStream(in, 4);
                if (peekTag(pushback, byteOrder, buf) == Tag.Item) {
                    transferDataFragments(pushback, byteOrder, dos, toggleByteOrder);
                } else {
                    transfer(pushback, dos, toggleByteOrder, dos.swapBuffer());
                }
            } else {
                transferNBytes(in, dos, vallen, toggleByteOrder, dos.swapBuffer());
            }
        }
    }

    private static int skipNBytes(InputStream in, int length) throws IOException {
        int n = 0;
        while (n < length) {
            int count = (int) in.skip(length - n);
            if (count < 0)
                break;
            n += count;
        }
        return n;
    }

    private int peekTag(PushbackInputStream in, ByteOrder byteOrder, byte[] buf) throws IOException {
        int nread = in.readNBytes(buf, 0, 4);
        in.unread(buf, 0, nread);
        return byteOrder.bytesToTag(buf, 0);

    }

    private void transferNBytes(InputStream in, OutputStream out, int length, ToggleByteOrder toggleByteOrder, byte[] b)
            throws IOException {
        int remaining = length;
        while (remaining > 0) {
            int nread = in.readNBytes(b, 0, Math.min(remaining, b.length));
            if (nread == 0) throw new EOFException();
            if (toggleByteOrder != null) {
                toggleByteOrder.swapBytes(b, nread);
            }
            out.write(b, 0, nread);
            remaining -= nread;
        }
    }

    private void transfer(InputStream in, OutputStream out, ToggleByteOrder toggleByteOrder, byte[] b)
            throws IOException {
        int nread;
        while ((nread = in.readNBytes(b, 0, b.length)) > 0) {
            if (toggleByteOrder != null) {
                toggleByteOrder.swapBytes(b, nread);
            }
            out.write(b, 0, nread);
        }
    }

    private void transferDataFragments(InputStream in, ByteOrder byteOrder, DicomOutputStream dos,
                                       ToggleByteOrder toggleByteOrder) throws IOException {
        byte[] b = dos.swapBuffer();
        in.readNBytes(b, 0, 8);
        while (byteOrder.bytesToTag(b, 0) == Tag.Item) {
            int itemLen = byteOrder.bytesToInt(b, 4);
            dos.writeHeader(Tag.Item, VR.NONE, itemLen);
            transferNBytes(in, dos, itemLen, toggleByteOrder, b);
            in.readNBytes(b, 0, 8);
        }
        if (byteOrder.bytesToTag(b, 0) != Tag.SequenceDelimitationItem
                || byteOrder.bytesToInt(b, 4) != 0) {
            throw new IOException("Invalid Item Sequence @ " + uri);
        }
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

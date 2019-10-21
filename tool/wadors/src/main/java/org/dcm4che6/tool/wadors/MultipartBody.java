package org.dcm4che6.tool.wadors;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2019
 */
public class MultipartBody extends InputStream {
    private static final int BUFFER_SIZE = 8192;
    private final PushbackInputStream in;
    private final byte[] delimiter;
    private boolean eof;


    public MultipartBody(InputStream in, String boundary) throws IOException {
        this.in = new PushbackInputStream(in, BUFFER_SIZE);
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        skipPreamble();
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        int b = readFrom(in, delimiter);
        eof = b < 0;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (eof) {
            return -1;
        }
        len = in.read(b, off, Math.min(len, BUFFER_SIZE));
        for (int i = 0; i < len; i++) {
            if (containsDelimiter(b, off + i, len - i)) {
                int delimiterEnd = i + delimiter.length;
                if (eof = len >= delimiterEnd) {
                    in.unread(b, off + delimiterEnd, len - delimiterEnd);
                    return i > 0 ? i : -1;
                }
                in.unread(b, off + i, len - i);
                if (i > 0) {
                    return i;
                }
                b[off] = (byte) read();
                return eof ? -1 : 1;
            }
        }
        return len;
    }

    private void skipPreamble() throws IOException {
        byte[] dashBoundary = Arrays.copyOfRange(this.delimiter, 2, this.delimiter.length);
        while (readFrom(in, dashBoundary) != -1);
    }

    private static int readFrom(PushbackInputStream in, byte[] delimiter) throws IOException {
        int b;
        for (int i = 0; i < delimiter.length; i++) {
            if ((b = in.read()) != delimiter[i]) {
                if (i == 0) {
                    return b;
                }
                in.unread(b);
                in.unread(delimiter, 1, i - 1);
                return delimiter[0];
            }
        }
        return -1;
    }

    private boolean containsDelimiter(byte[] b, int off, int len) {
        int max = Math.min(delimiter.length, len);
        for (int i = 0; i < max; i++) {
            if (b[off + i] != delimiter[i]) {
                return false;
            }
        }
        return true;
    }

    Map<String, List<String>> nextPart() throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if ((b1 | b2) < 0)
            throw new EOFException();

        if (b1 == '-' && b2 == '-')
            return null;

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String header;
        int endName;
        while ((header = nextHeader()) != null) {
            endName = header.indexOf(':');
            headers.merge(header.substring(0, endName),
                    List.of(header.substring(endName + 1).trim()),
                    MultipartBody::merge);
        }
        eof = false;
        return headers;
    }

    private static <T> List<T> merge(List<T> l1, List<T> l2) {
        List<T> l3 = new ArrayList<>(l1.size() + l2.size());
        l3.addAll(l1);
        l3.addAll(l2);
        return l3;
    }

    private String nextHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b1, b2;
        while (true) {
            while ((b1 = in.read()) != '\r') {
                if (b1 < 0) {
                    throw new EOFException();
                }
                out.write(b1);
            }
            if ((b2 = in.read()) == '\n') {
                break;
            }
            if (b2 < 0) {
                throw new EOFException();
            }
            out.write(b1);
            in.unread(b2);
        }
        return out.size() > 0 ? new String(out.toByteArray(), StandardCharsets.UTF_8) : null;
    }
}

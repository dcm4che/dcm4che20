package org.dcm4che6.tool.wadors;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2019
 */
public class MultipartBody extends InputStream {
    private static final int BUFFER_SIZE = 8192;
    private final PushbackInputStream in;
    private byte[] delimiter;
    private boolean eof;


    public MultipartBody(InputStream in, String boundary) throws IOException {
        this.in = new PushbackInputStream(in, BUFFER_SIZE);
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] dashBoundary = Arrays.copyOfRange(this.delimiter, 2, this.delimiter.length);
        while (read0(dashBoundary) != -1);
    }

    @Override
    public int read() throws IOException {
        return read0(delimiter);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (eof) {
            return -1;
        }
        len = in.read(b, off, Math.min(len, BUFFER_SIZE));
        for (int i = 0; i < len; i++) {
            if (endOfPart(b, off + i, len - i)) {
                int delimiterEnd = i + delimiter.length;
                if (eof = len >= delimiterEnd) {
                    in.unread(b, off + delimiterEnd, len - delimiterEnd);
                } else {
                    in.unread(b, off + i, len - i);
                }
                return i > 0 ? i : -1;
            }
        }
        return len;
    }

    private boolean endOfPart(byte[] b, int off, int len) {
        int max = Math.min(delimiter.length, len);
        for (int i = 0; i < max; i++) {
            if (b[off + i] != delimiter[i]) {
                return false;
            }
        }
        return true;
    }

    int read0(byte[] delimiter) throws IOException {
        if (eof) {
            return -1;
        }
        int b;
        for (int i = 0; i < delimiter.length; i++) {
            if ((b = in.read()) != delimiter[i]) {
                if (i == 0) {
                    return b;
                }
                in.unread(b);
                while (--i > 0) {
                    in.unread(delimiter[i]);
                }
                return delimiter[0];
            }
        }
        eof = true;
        return -1;
    }

    public Map<String, List<String>> nextPart() throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        if ((b1 | b2) < 0)
            throw new EOFException();

        if (b1 == '-' && b2 == '-')
            return null;

        Map<String, List<String>> headers = new HashMap<>();
        String header;
        int endName;
        while ((header = nextHeader()) != null) {
            endName = header.indexOf(':');
            char ch = header.charAt(endName + 2);
            headers.put(header.substring(0, endName),
                    List.of(ch == '"'
                            ? header.substring(endName + 2, header.lastIndexOf(ch))
                            : header.substring(endName + 1).trim()));
        }
        eof = false;
        return headers;
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

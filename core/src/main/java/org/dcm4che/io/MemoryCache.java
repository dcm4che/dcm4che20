package org.dcm4che.io;

import org.dcm4che.data.SpecificCharacterSet;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class MemoryCache {

    private static final int MAX_BUFFER_SIZE = 2048;
    private final ArrayList<byte[]> blocks = new ArrayList<>();
    private long length;
    private final List<SkippedBytes> skippedBytes = new ArrayList<>();
    private boolean eof;

    long length() {
        return length;
    }

    void setLength(long length) {
        this.length = length;
    }

    byte[] firstBlock() {
        return blocks.get(0);
    }

    private byte[] newBlock() {
        return new byte[blocks.isEmpty() ? 0x100 : 0x80 << blocks.size()];
    }

    private static int blockIndex(long pos) {
        int i = 8;
        while ((pos >>> i) != 0)
            i++;
        return i - 8;
    }

    private static int blockOffset(byte[] block, long pos) {
        return (int) (pos & (block.length - 1));
    }

    long loadFromStream(long pos, InputStream in) throws IOException {
        while (pos >= length) {
            if (eof) {
                return length;
            }
            byte[] b = newBlock();
            int read = in.readNBytes(b, 0, b.length);
            if (read == 0) {
                eof = true;
                return length;
            }

            blocks.add(b);
            this.length += read;
            if (read < b.length) {
                eof = true;
                return Math.min(pos, length);
            }
        }
        return pos;
    }

    InputStream inflate(long pos, InputStream in) throws IOException {
        if (loadFromStream(pos + 2, in) != pos + 2)
            throw new EOFException();

        int size = (int) (length - pos);
        PushbackInputStream pushbackInputStream = new PushbackInputStream(in, size);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        pushbackInputStream.unread(b, offset, size);
        InflaterInputStream inflaterInputStream = new InflaterInputStream(pushbackInputStream,
                new Inflater(b[offset] != 120 || b[offset+1] != -100));
        int read = inflaterInputStream.readNBytes(b, offset, b.length - offset);
        eof = offset + read < b.length;
        length = pos + read;
        return inflaterInputStream;
    }

    public byte byteAt(long pos) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        return b[blockOffset(b, pos)];
    }

    int vrcode(long pos) {
        return shortAt(pos, ByteOrder.BIG_ENDIAN);
    }

    public short shortAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 1 < b.length)
                ? byteOrder.bytesToShort(b, offset)
                : byteOrder.bytesToShort(byteAt(pos), byteAt(pos + 1));
    }

    public int ushortAt(long pos, ByteOrder byteOrder) {
        return shortAt(pos, byteOrder) & 0xffff;
    }

    public int intAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 3 < b.length)
                ? byteOrder.bytesToInt(b, offset)
                : byteOrder.bytesToInt(byteAt(pos), byteAt(pos + 1), byteAt(pos + 2), byteAt(pos + 3));
    }

    public long uintAt(long pos, ByteOrder byteOrder) {
        return intAt(pos, byteOrder) & 0xffffffffL;
    }

    public int tagAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 3 < b.length)
                ? byteOrder.bytesToTag(b, offset)
                : byteOrder.bytesToTag(byteAt(pos), byteAt(pos + 1), byteAt(pos + 2), byteAt(pos + 3));
    }

    public long longAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 7 < b.length)
                ? byteOrder.bytesToLong(b, offset)
                : byteOrder.bytesToLong(byteAt(pos), byteAt(pos + 1), byteAt(pos + 2), byteAt(pos + 3),
                                byteAt(pos + 4), byteAt(pos + 5), byteAt(pos + 6), byteAt(pos + 7));
    }

    public String stringAt(long pos, int len, SpecificCharacterSet cs) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + len <= b.length)
                ? cs.decode(b, offset, len)
                : cs.decode(bytesAt(pos, len), 0, len);
    }

    byte[] bytesAt(long pos, int length) {
        byte[] dest = new byte[length];
        copyBytesTo(pos, dest, 0, length);
        return dest;
    }

    void copyBytesTo(long pos, byte[] dest, int destPos, int length) {
        int i = blockIndex(pos);
        byte[] src = blocks.get(i);
        int srcPos = blockOffset(src, pos);
        int copy =  Math.min(length, src.length - srcPos);
        System.arraycopy(src, srcPos, dest, destPos, copy);
        int remaining = length;
        while ((remaining -= copy) > 0) {
            destPos += copy;
            src = blocks.get(++i);
            copy = Math.min(remaining, src.length);
            System.arraycopy(src, 0, dest, destPos, copy);
        }
    }

    public void writeBytesTo(long pos, int length, OutputStream out) throws IOException {
        int i = blockIndex(pos);
        byte[] src = blocks.get(i);
        int srcPos = blockOffset(src, pos);
        int rlen = Math.min(length, src.length - srcPos);
        out.write(src, srcPos, rlen);
        int remaining = length;
        while ((remaining -= rlen) > 0L) {
            src = blocks.get(++i);
            rlen = Math.min(remaining, src.length);
            out.write(src, 0, rlen);
        }
    }

    public void writeSwappedBytesTo(long pos, int length, OutputStream out, ToggleByteOrder toggleByteOrder, byte[] buf)
            throws IOException {
        if (buf.length == 0 || (buf.length & 7) != 0) {
            throw new IllegalArgumentException("buf.length: " + buf.length);
        }
        int remaining = length;
        int copy = 0;
        while ((remaining -= copy) > 0) {
            pos += copy;
            copy =  Math.min(remaining, buf.length);
            copyBytesTo(pos, buf, 0, copy);
            toggleByteOrder.swapBytes(buf, copy);
            out.write(buf, 0, copy);
        }
    }

    void skipBytes(long pos, int len, InputStream in, OutputStream out) throws IOException {
        long skip = pos + len - length;
        long pos1 = pos - skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos1));
        int off = blockOffset(b, pos1);
        if (out != null) {
            out.write(b, off, skip < 0 ? len : b.length - off);
        }
        if (skip > 0) {
            if (eof)
                throw new EOFException();

            if (out == null)
                skipAll(in, skip);
            else
                transferTo(in, out, skip);

            length += skip;
        } else {
            System.arraycopy(b, off + len, b, off, (int) -skip);
            off -= skip;
        }
        if (!eof) {
            int read = in.readNBytes(b, off, b.length - off);
            eof = off + read < b.length;
            this.length += read;
        }
        bytesSkipped(pos, len);
    }

    private void skipAll(InputStream in, long n) throws IOException {
        long nr;
        do {
            if ((nr = in.skip(n)) == 0)
                throw new EOFException();

        } while ((n -= nr) > 0);
    }

    private void transferTo(InputStream in, OutputStream out, long n) throws IOException {
        byte[] b = new byte[(int) Math.min(MAX_BUFFER_SIZE, n)];
        int nr;
        do {
            nr = (int) Math.min(b.length, n);
            if (in.readNBytes(b, 0, nr) < nr)
                throw new EOFException();

            out.write(b, 0, nr);
        } while ((n -= nr) > 0);
    }

    private void bytesSkipped(long pos, int len) {
        SkippedBytes last;
        if (!skippedBytes.isEmpty() && pos == (last = skippedBytes.get(skippedBytes.size() - 1)).end()) {
            last.len += len;
        } else {
            skippedBytes.add(new SkippedBytes(pos, len));
        }
    }

    private long skippedBytes(long pos) {
        long len = 0L;
        for (SkippedBytes skipped : skippedBytes) {
            if (pos <= skipped.pos) return len;
            len += skipped.len;
        }
        return len;
    }

    private static class SkippedBytes {
        final long pos;
        long len;

        SkippedBytes(long pos, long len) {
            this.pos = pos;
            this.len = len;
        }

        long end() {
            return pos + len;
        }
    }

}

package org.dcm4che.internal;

import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.io.ByteOrder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
class MemoryCache {

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

    byte byteAt(long pos) {
        return byteAt1(pos - skippedBytes(pos));
    }

    private byte byteAt1(long pos) {
        byte[] b = blocks.get(blockIndex(pos));
        return b[blockOffset(b, pos)];
    }

    int vrcode(long pos) {
        return shortAt(pos, ByteOrder.BIG_ENDIAN);
    }

    short shortAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 1 < b.length)
                ? byteOrder.bytesToShort(b, offset)
                : byteOrder.bytesToShort(byteAt1(pos), byteAt1(pos + 1));
    }

    int ushortAt(long pos, ByteOrder byteOrder) {
        return shortAt(pos, byteOrder) & 0xffff;
    }

    int intAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 3 < b.length)
                ? byteOrder.bytesToInt(b, offset)
                : byteOrder.bytesToInt(byteAt1(pos), byteAt1(pos + 1), byteAt1(pos + 2), byteAt1(pos + 3));
    }

    long uintAt(long pos, ByteOrder byteOrder) {
        return intAt(pos, byteOrder) & 0xffffffffL;
    }

    int tagAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 3 < b.length)
                ? byteOrder.bytesToTag(b, offset)
                : byteOrder.bytesToTag(byteAt1(pos), byteAt1(pos + 1), byteAt1(pos + 2), byteAt1(pos + 3));
    }

    long longAt(long pos, ByteOrder byteOrder) {
        pos -= skippedBytes(pos);
        byte[] b = blocks.get(blockIndex(pos));
        int offset = blockOffset(b, pos);
        return (offset + 7 < b.length)
                ? byteOrder.bytesToLong(b, offset)
                : byteOrder.bytesToLong(byteAt1(pos), byteAt1(pos + 1), byteAt1(pos + 2), byteAt1(pos + 3),
                                byteAt1(pos + 4), byteAt1(pos + 5), byteAt1(pos + 6), byteAt1(pos + 7));
    }

    String stringAt(long pos, int len, SpecificCharacterSet cs) {
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

    void writeBytesTo(long pos, int length, OutputStream out) throws IOException {
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

    void writeSwappedBytesTo(long pos, int length, OutputStream out, ToggleByteOrder toggleByteOrder, byte[] buf)
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
        int skip = (int) (pos + len - length);
        long pos1 = pos - skippedBytes(pos);
        int index = blockIndex(pos1);
        byte[] b = blocks.get(index);
        int off = blockOffset(b, pos1);
        byte[] src = blocks.get(blocks.size() - 1);
        int srcPos = blockOffset(src, pos1 + len);
        if (out != null) {
            out.write(b, off, skip <= 0 ? len : b.length - off);
        }
        if (skip > 0) {
            if (eof)
                throw new EOFException();

            if (out == null)
                skipAll(in, skip);
            else
                transferTo(in, out, skip);

            length += skip;
        } else if (skip < 0) {
            int len1;
            while ((len1 = b.length - off) < -skip) {
                System.arraycopy(src, srcPos, b, off, len1);
                srcPos += len1;
                skip += len1;
                off = 0;
                b = blocks.get(++index);
            }
            System.arraycopy(src, srcPos, b, off, -skip);
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

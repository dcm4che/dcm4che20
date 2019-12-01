package org.dcm4che6.net;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
class ByteBufferPool {

    private static final ByteBufferPool BYTE_BUFFER_POOL_8K = new ByteBufferPool(0x2000);
    private static final ByteBufferPool BYTE_BUFFER_POOL_32K = new ByteBufferPool(0x8000);

    private final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
    private final int size;

    private ByteBufferPool(int size) {
        this.size = size;
    }

    private ByteBuffer allocate0() {
        ByteBuffer buffer = queue.poll();
        return buffer != null ? buffer : ByteBuffer.allocate(size);
    }

    private void free0(ByteBuffer buffer) {
        queue.offer(buffer.clear());
    }

    public static ByteBuffer allocate() {
        return BYTE_BUFFER_POOL_8K.allocate0();
    }

    public static ByteBuffer allocate(int size) {
        return size > 0x8000
                ? ByteBuffer.allocate(size)
                : (size > 0x2000 ? BYTE_BUFFER_POOL_32K : BYTE_BUFFER_POOL_8K).allocate0().limit(size);
    }

    public static void free(ByteBuffer buffer) {
        switch (buffer.capacity()) {
            case 0x2000:
                BYTE_BUFFER_POOL_8K.free0(buffer);
                break;
            case 0x8000:
                BYTE_BUFFER_POOL_32K.free0(buffer);
                break;
        }
    }

}

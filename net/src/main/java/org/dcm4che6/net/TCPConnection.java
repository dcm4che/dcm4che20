package org.dcm4che6.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public abstract class TCPConnection<T extends TCPConnection> {
    static final Logger LOG = LoggerFactory.getLogger(TCPConnection.class);
    private static final AtomicInteger idGenerator = new AtomicInteger();
    public final int id = idGenerator.incrementAndGet();
    public final Role role;
    public final TCPConnector<T> connector;
    protected final BlockingQueue<WriteAndThen<T>> writeQueue = new LinkedBlockingQueue<>();
    protected final CompletableFuture<T> connected = new CompletableFuture<>();
    protected SelectionKey key;
    protected String name;

    public enum Role {
        SERVER,
        CLIENT;
    }

    public TCPConnection(TCPConnector<T> connector, Role role) {
        this.connector = connector;
        this.role = role;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    void setKey(SelectionKey key) {
        this.key = key;
    }

    public boolean write(ByteBuffer src, Consumer<T> action) {
        key.interestOpsOr(SelectionKey.OP_WRITE);
        boolean offer = writeQueue.offer(new WriteAndThen<T>(src, action));
        connector.wakeup();
        return offer;
    }

    public boolean isOpen() {
        return key.channel().isOpen();
    }

    public void close() throws IOException {
        key.channel().close();
    }

    void continueReceive() throws IOException {
        connector.onReadable(key);
    }

    protected abstract boolean onNext(ByteBuffer buffer);

    protected void connected() {
        connected.complete((T) this);
    }

    void onWritable() throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        WriteAndThen<T> writeAndThen;
        while ((writeAndThen = writeQueue.peek()) != null) {
            ch.write(writeAndThen.buffer);
            if (writeAndThen.buffer.hasRemaining()) return;
            writeAndThen.action.accept((T) this);
            ByteBufferPool.free(writeQueue.remove().buffer);
        }
        key.interestOpsAnd(~SelectionKey.OP_WRITE);
    }

    private static class WriteAndThen<T> {
        final ByteBuffer buffer;
        final Consumer<T> action;

        private WriteAndThen(ByteBuffer buffer, Consumer<T> action) {
            this.buffer = buffer;
            this.action = action;
        }
    }
}

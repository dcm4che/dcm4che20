package org.dcm4che6.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public abstract class TCPConnection<T extends TCPConnection> {

    protected final TCPConnector<T> connector;
    protected final Role role;
    protected final BlockingQueue<WriteAndThen<T>> writeQueue = new LinkedBlockingQueue<>();
    protected final CompletableFuture<T> connected = new CompletableFuture<>();
    protected SelectionKey key;

    public enum Role { SERVER, CLIENT }

    public TCPConnection(TCPConnector<T> connector, Role role) {
        this.connector = connector;
        this.role = role;
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

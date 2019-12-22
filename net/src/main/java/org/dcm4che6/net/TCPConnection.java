package org.dcm4che6.net;

import org.dcm4che6.conf.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
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
    public final TCPConnector<T> connector;
    public final Connection local;
    protected final BlockingQueue<WriteAndThen<T>> writeQueue = new LinkedBlockingQueue<>();
    protected final CompletableFuture<T> connected = new CompletableFuture<>();
    protected final CompletableFuture<T> closed = new CompletableFuture<>();
    protected Role role;
    protected SelectionKey key;
    private String name;

    public enum Role {
        ACCEPTOR,
        REQUESTOR;
    }

    public TCPConnection(TCPConnector<T> connector, Connection local) {
        this.connector = connector;
        this.local = local;
    }

    void accepted(SelectionKey key) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        this.name = sc.getLocalAddress() + "<-" + sc.getRemoteAddress() + "(" + id + ")";
        this.key = key;
        this.role = Role.ACCEPTOR;
        LOG.info("{}: accepted", name);
    }

    boolean connect(SelectionKey key, SocketAddress remote) throws IOException {
        SocketChannel sc = (SocketChannel) key.channel();
        this.name = sc.getLocalAddress() + "->" + remote + "(" + id + ")";
        this.key = key;
        this.role = Role.REQUESTOR;
        LOG.info("{}: connect", name);
        return sc.connect(remote);
    }

    public String toString() {
        return name;
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
        LOG.info("{}: close", name);
        key.channel().close();
        closed.complete((T) this);
    }

    public CompletableFuture<T> onClose() {
        return closed;
    }

    void continueReceive() throws IOException {
        connector.onReadable(key);
    }

    protected abstract boolean onNext(ByteBuffer buffer);

    protected void connected() {
        LOG.info("{}: connected", name);
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

package org.dcm4che6.net;

import org.dcm4che6.conf.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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
    private final Semaphore writeSemaphore = new Semaphore(1);
    protected volatile WriteAndThen<T> writeAndThen;
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

    @Override
    public String toString() {
        return name;
    }

    public void write(ByteBuffer src, Consumer<T> action) {
        if (!writeSemaphore.tryAcquire())
        try {
            LOG.trace("{}: wait to queue writing {} bytes", this, src.remaining());
            writeSemaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOG.trace("{}: queue writing {} bytes", this, src.remaining());
        writeAndThen = new WriteAndThen<T>(src, action);
        interestOpsOr(SelectionKey.OP_WRITE);
        connector.wakeup();
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

    void interestOpsAnd(int ops) {
        int oldVal = key.interestOpsAnd(ops);
        LOG.trace("{}: interestOps {}->{}", key.attachment(), oldVal, oldVal & ops);
    }

    void interestOpsOr(int ops) {
        int oldVal = key.interestOpsOr(ops);
        LOG.trace("{}: interestOps {}->{}", key.attachment(), oldVal, oldVal | ops);
        connector.wakeup();
    }

    protected abstract void onNext(ByteBuffer buffer);

    protected void connected() {
        LOG.info("{}: connected", name);
        connected.complete((T) this);
    }

    void onWritable() throws IOException {
        WriteAndThen<T> writeAndThen;
        if ((writeAndThen = this.writeAndThen) == null) {
            LOG.trace("{}: no bytes for writing", this);
            return;
        }
        int remaining = writeAndThen.buffer.remaining();
        LOG.trace("{}: writing {} bytes", this, remaining);
        ((SocketChannel) key.channel()).write(writeAndThen.buffer);
        LOG.trace("{}: wrote {} bytes", this, remaining - writeAndThen.buffer.remaining());
        if (writeAndThen.buffer.hasRemaining()) {
            return;
        }
        ByteBufferPool.free(writeAndThen.buffer);
        writeAndThen.action.accept((T) this);
        this.writeAndThen = null;
        interestOpsAnd(~SelectionKey.OP_WRITE);
        writeSemaphore.release();
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

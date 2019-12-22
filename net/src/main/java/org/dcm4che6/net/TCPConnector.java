package org.dcm4che6.net;

import org.dcm4che6.conf.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public class TCPConnector<T extends TCPConnection> implements Runnable {
    static final Logger LOG = LoggerFactory.getLogger(TCPConnector.class);
    private final BiFunction<TCPConnector, Connection, T> connFactory;
    private final Selector selector;

    public TCPConnector(BiFunction<TCPConnector, Connection, T> connFactory)
            throws IOException {
        this.connFactory = Objects.requireNonNull(connFactory);
        selector = Selector.open();
    }

    public Selector wakeup() {
        return selector.wakeup();
    }

    public ServerSocketChannel bind(Connection local) throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        configure(ssc, local);
        ssc.register(selector, SelectionKey.OP_ACCEPT, local);
        selector.wakeup();
        return ssc;
    }

    public CompletableFuture<T> connect(Connection local, Connection remote) throws IOException {
        SocketChannel sc = SocketChannel.open();
        configure(sc, local);
        SocketAddress addr = addr(remote);
        T conn = connFactory.apply(this, local);
        SelectionKey key = sc.register(selector, SelectionKey.OP_CONNECT, conn);
        if (conn.connect(key, addr)) {
            onConnectable(key);
        }
        wakeup();
        return conn.connected;
    }

    private void configure(ServerSocketChannel ssc, Connection conn) throws IOException {
        ssc.configureBlocking(false);
        SocketAddress local = serverBind(conn);
        LOG.info("Start listening on {}", local);
        ssc.bind(local, 0);
    }

    private void configure(SocketChannel sc, Connection local) throws IOException {
        sc.configureBlocking(false);
        sc.bind(clientBind(local));
    }

    private static SocketAddress serverBind(Connection conn) {
        return new InetSocketAddress(portOf(conn));
    }

    private static SocketAddress clientBind(Connection conn) {
        return new InetSocketAddress(0);
    }

    private static int portOf(Connection conn) {
        return conn.getPort()
                .orElseThrow(() -> new IllegalArgumentException("Connection not listening"));
    }

    private static SocketAddress addr(Connection conn) {
        return new InetSocketAddress(conn.getHostname(), portOf(conn));
    }

    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                selector.select();
                for (SelectionKey key : selector.selectedKeys()) {
                    try {
                        if (key.isAcceptable()) {
                            onAcceptable(key);
                        }
                        if (key.isConnectable()) {
                            onConnectable(key);
                        }
                        if (key.isWritable()) {
                            ((TCPConnection) key.attachment()).onWritable();
                        }
                        if (key.isReadable()) {
                            onReadable(key);
                        }
                    } catch (CancelledKeyException e) {
//                        e.printStackTrace();
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    void onAcceptable(SelectionKey skey) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) skey.channel();
        SocketChannel sc = ssc.accept();
        if (sc == null) return;
        sc.configureBlocking(false);
        TCPConnection conn = connFactory.apply(this, (Connection) skey.attachment());
        conn.accepted(sc.register(selector, SelectionKey.OP_READ, conn));
    }

    private void onConnectable(SelectionKey key) throws IOException {
        if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0
                && ((SocketChannel) key.channel()).finishConnect()) {
            ((TCPConnection) key.attachment()).connected();
            key.interestOpsAnd(~SelectionKey.OP_CONNECT);
            key.interestOpsOr(SelectionKey.OP_READ);
        }
    }

    void onReadable(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        ByteBuffer buffer;
        boolean more = true;
        while (more && ch.isOpen()){
            if (ch.read(buffer = ByteBufferPool.allocate()) == 0) {
                ByteBufferPool.free(buffer);
                return;
            }
            more = !buffer.hasRemaining();
            buffer.flip();
            if (!((TCPConnection) key.attachment()).onNext(buffer)) {
                key.interestOpsAnd(~SelectionKey.OP_READ);
                return;
            }
        }
        if (!key.isReadable()) {
            key.interestOpsOr(SelectionKey.OP_READ);
        }
    }
}

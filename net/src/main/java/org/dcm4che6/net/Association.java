package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public class Association extends TCPConnection<Association> {
    private static final int user_no_reason_given = 0x010101;
    private static final int application_context_name_not_supported = 0x010102;
    private static final int calling_AE_title_not_recognized = 0x010103;
    private static final int called_AE_title_not_recognized = 0x010107;
    private static final int acse_no_reason_given = 0x010201;
    private static final int protocol_version_not_supported = 0x010202;
    private static final int temporary_congestion = 0x000301;
    private static final int local_limit_exceeded = 0x000302;

    private static final int MAX_PDU_LENGTH = 1048576;

    private volatile ByteBuffer carry;
    private volatile ByteBuffer pdv;
    private volatile int pduLength;
    private volatile State state = State.STA_1;
    private volatile BiConsumer<Association, ByteBuffer> action;
    private volatile int resultSourceReason;
    private volatile AAssociate.RQ aarq;
    private volatile AAssociate.AC aaac;
    private final BlockingQueue<ByteBuffer> pdvQueue = new LinkedBlockingQueue<>();
    private final DimseHandler dimseRQHandler;
    private int maxPDULength;
    private final CompletableFuture<Association> aaacReceived = new CompletableFuture<>();

    public Association(TCPConnector<Association> connector, Role role, DimseHandler dimseRQHandler) {
        super(connector, role);
        this.dimseRQHandler = dimseRQHandler;
        if (role == Role.SERVER) {
            ae_5();
        } else {
            ae_1();
        }
    }

    AAssociate.CommonExtendedNegotation commonExtendedNegotationFor(String cuid) {
        return aarq.getCommonExtendedNegotation(cuid);
    }

    private void changeState(State state) {
        System.out.println("Enter State: " + state);
        this.state = state;
    }

    @Override
    protected boolean onNext(ByteBuffer buffer) {
        buffer = takeCarry(buffer);
        do {
            if (state.discard) {
                ByteBufferPool.free(buffer);
                return true;
            }
            if (buffer.remaining() < 6) {
                setCarry(buffer);
                return true;
            }
            if (action == null) {
                action = state.action(buffer.getShort() >>> 8);
                pduLength = buffer.getInt();
            }
            action.accept(this, buffer);
        } while (buffer.hasRemaining());
        return true;
    }

    private ByteBuffer takeCarry(ByteBuffer buffer) {
        if (carry != null) {
            carry.put(buffer);
            ByteBufferPool.free(buffer);
            buffer = carry;
            buffer.flip();
            carry = null;
        }
        return buffer;
    }

    private void setCarry(ByteBuffer buffer) {
        carry = ByteBufferPool.allocate();
        carry.put(buffer);
        ByteBufferPool.free(buffer);
    }

    private void run()  {
        try {
            ByteBuffer pdv;
            while ((pdv = nextPDV()).hasRemaining()) {
                Byte pcid = requireAcceptedPresentationContext(pdv.get());
                PDVInputStream commandStream = new PDVInputStream(pcid, requireCommandPDV(MCH.of(pdv.get())), pdv);
                DicomObject commandSet = new DicomInputStream(commandStream).readCommandSet();
                Dimse dimse = Dimse.of(commandSet);
                dimse.handler.accept(this, pcid, dimse, commandSet,
                        hasDataSet(commandSet) ? dataStream(pcid, pdv) : null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void onDimseRQ(Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) throws IOException {
        dimseRQHandler.accept(this, pcid, dimse, commandSet, dataStream);
    }

    void onDimseRSP(Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) {

    }

    void onCancelRQ(Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) {

    }

    private static boolean hasDataSet(DicomObject commandSet) {
        return 0x0101 != commandSet.getInt(Tag.CommandDataSetType)
                .orElseThrow(() -> new IllegalArgumentException("Missing Command Data Set Type (0000,0800)"));
    }

    private InputStream dataStream(Byte pcid, ByteBuffer pdv) {
        return new PDVInputStream(
                requirePresentationContextID(pdv.get(), pcid),
                requireDataPDV(MCH.of(pdv.get())),
                pdv);
    }

    private Byte requireAcceptedPresentationContext(Byte pcid) {
        AAssociate.AC.PresentationContext pc = aaac.getPresentationContext(pcid);
        if (pc == null || pc.result != AAssociate.AC.Result.ACCEPTANCE) {
            throw new IllegalArgumentException("No accepted Presentation Context with ID: " + (pcid & 0xff));
        }
        return pcid;
    }

    private ByteBuffer nextPDV() {
        try {
            return pdvQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private boolean negotiate() {
        aaac = new AAssociate.AC();
        aaac.setCalledAETitle(aarq.getCalledAETitle());
        aaac.setCallingAETitle(aarq.getCallingAETitle());
        aarq.forEachPresentationContext((id, pc) ->
                aaac.putPresentationContext(id, AAssociate.AC.Result.ACCEPTANCE, pc.transferSyntax()[0]));
        maxPDULength = aarq.getMaxPDULength();
        return true;
    }

    private static ByteBuffer toBuffer(short pduType, AAssociate aaxx) {
        ByteBuffer buffer = ByteBufferPool.allocate();
        buffer.putShort(pduType);
        buffer.putInt(aaxx.pduLength());
        aaxx.writeTo(buffer);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer mkAAssociateRJ() {
        return toBuffer((short) 0x0300, resultSourceReason);
    }

    private ByteBuffer mkAReleaseRQ() {
        return toBuffer((short) 0x0500, 0);
    }

    private ByteBuffer mkAReleaseRP() {
        return toBuffer((short) 0x0600, 0);
    }

    private ByteBuffer mkAAbort() {
        return toBuffer((short) 0x0700, resultSourceReason);
    }

    private static ByteBuffer toBuffer(short pduType, int resultSourceReason) {
        ByteBuffer buffer = ByteBufferPool.allocate();
        buffer.putShort(pduType);
        buffer.putInt(4);
        buffer.putInt(resultSourceReason);
        buffer.flip();
        return buffer;
    }

    void writeDimse(Byte pcid, Dimse dimse, DicomObject commandSet, DicomObject dataSet) throws IOException {
        ByteBuffer buffer = ByteBufferPool.allocate(maxPDULength + 6).position(6);
        PDVOutputStream pdv = new PDVOutputStream(pcid, MCH.COMMAND, buffer);
        new DicomOutputStream(pdv).writeCommandSet(commandSet);
        buffer = pdv.writePDVHeader(MCH.LAST_COMMAND);
        if (dataSet != null) {
            pdv = new PDVOutputStream(pcid, MCH.DATA, buffer);
            new DicomOutputStream(pdv)
                    .withEncoding(DicomEncoding.of(getTransferSyntax(pcid)))
                    .writeDataSet(dataSet);
            buffer = pdv.writePDVHeader(MCH.LAST_DATA);
        }
        writePDataTF(buffer);
    }

    private void writePDataTF(ByteBuffer buffer) {
        buffer.flip();
        buffer.putShort(0, (short) 0x0400);
        buffer.putInt(2, buffer.remaining() - 6);
        write(buffer, x -> {});
    }

    private void closeAfterDelay() {
        changeState(State.STA_13);
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(this::safeClose);
    }

    private void safeClose() {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        changeState(State.STA_1);
    }

    String getTransferSyntax(Byte pcid) {
        return aaac.getPresentationContext(pcid).transferSyntax;
    }

    public CompletableFuture<Association> open(AAssociate.RQ aarq) {
        state.open(this, Objects.requireNonNull(aarq));
        return aaacReceived;
    }

    @Override
    public void connected() {
        state.connected(this);
        super.connected();
    }

    public CompletableFuture<Association> release() {
        state.release(this);
        return aaacReceived;
    }

    private enum State {
        STA_1(true, "Idle"),
        STA_2(false, "Transport connection open (Awaiting A-ASSOCIATE-RQ PDU)") {
            @Override
            BiConsumer<Association, ByteBuffer> onAAssociateRQ() {
                return Association::ae_6;
            }
        },
        STA_3(true, "Awaiting local A-ASSOCIATE response primitive (from local user)") {
            @Override
            public void open(Association as, AAssociate.RQ aarq) {
                as.ae_2(aarq);
            }
        },
        STA_4(true, "Awaiting transport connection opening to complete (from local transport service)") {
            @Override
            public void connected(Association as) {
                as.changeState(STA_3);
            }
        },
        STA_5(false, "Awaiting A-ASSOCIATE-AC or A-ASSOCIATE-RJ PDU"){
            @Override
            BiConsumer<Association, ByteBuffer> onAAssociateAC() {
                return Association::ae_3;
            }

            @Override
            BiConsumer<Association, ByteBuffer> onAAssociateRJ() {
                return Association::ae_4;
            }
        },
        STA_6(false, "Association established and ready for data transfer") {
            @Override
            BiConsumer<Association, ByteBuffer> onPDataTF() {
                return Association::dt_2;
            }

            @Override
            BiConsumer<Association, ByteBuffer> onAReleaseRQ() {
                return Association::ar_2;
            }

            @Override
            public void release(Association as) {
                as.ar_1();
            }
        },
        STA_7(false, "Awaiting A-RELEASE-RP PDU") {
            @Override
            BiConsumer<Association, ByteBuffer> onAReleaseRP() {
                return Association::ar_3;
            }
        },
        STA_8(true, "Awaiting local A-RELEASE response primitive (from local user)"),
        STA_9(true, "Release collision requestor side; awaiting A-RELEASE response (from local user)"),
        STA_10(false, "Release collision acceptor side; awaiting A-RELEASE-RP PDU"),
        STA_11(false, "Release collision requestor side; awaiting A-RELEASE-RP PDU"),
        STA_12(true, "Release collision acceptor side; awaiting A-RELEASE response primitive (from local user)"),
        STA_13(true, "Awaiting Transport Connection Close Indication (Association no longer exists)");

        final boolean discard;
        final String description;

        State(boolean discard, String description) {
            this.discard = discard;
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }

        BiConsumer<Association, ByteBuffer> action(int pduType) {
            switch (pduType) {
                case 1:
                    return onAAssociateRQ();
                case 2:
                    return onAAssociateAC();
                case 3:
                    return onAAssociateRJ();
                case 4:
                    return onPDataTF();
                case 5:
                    return onAReleaseRQ();
                case 6:
                    return onAReleaseRP();
                case 7:
                    return onAAbort();
            }
            return onInvalidPDU(pduType);
        }

        BiConsumer<Association, ByteBuffer> onAAssociateRQ() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onAAssociateAC() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onAAssociateRJ() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onPDataTF() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onAReleaseRQ() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onAReleaseRP() {
            return Association::aa_8;
        }

        BiConsumer<Association, ByteBuffer> onAAbort() {
            return Association::aa_3;
        }

        BiConsumer<Association, ByteBuffer> onInvalidPDU(int pduType) {
            return Association::aa_8;
        }

        public void connected(Association as) {
        }

        public void open(Association as, AAssociate.RQ aarq) {
            throw new IllegalStateException(toString());
        }

        public void release(Association as) {
            throw new IllegalStateException(toString());
        }
    }

    private void ae_1() {
        changeState(State.STA_4);
    }

    private void ae_2(AAssociate.RQ aarq) {
        this.aarq = aarq;
        write(toBuffer((short) 0x0100, this.aarq), as -> as.changeState(State.STA_5));
    }

    private void ae_3(ByteBuffer buffer) {
        if (buffer.remaining() < pduLength) {
            setCarry(buffer);
            return;
        }
        aaac = new AAssociate.AC(buffer, pduLength);
        changeState(State.STA_6);
        aaacReceived.complete(this);
        action = null;
    }

    private void ae_4(ByteBuffer buffer) {
        if (buffer.remaining() < pduLength) {
            setCarry(buffer);
            return;
        }
        resultSourceReason = buffer.getInt();
        aaacReceived.completeExceptionally(new AAssociateRJ(resultSourceReason));
        safeClose();
        action = null;
    }

    private void ae_5() {
        changeState(State.STA_2);
    }

    private void ae_6(ByteBuffer buffer) {
        if (buffer.remaining() < pduLength) {
            setCarry(buffer);
            return;
        }
        aarq = new AAssociate.RQ(buffer, pduLength);
        action = null;
        if (negotiate()) {
            write(toBuffer((short) 0x0200, aaac), Association::onEstablished);
        } else {
            write(mkAAssociateRJ(), Association::closeAfterDelay);
        }
    }

    private void onEstablished() {
        changeState(State.STA_6);
        CompletableFuture.runAsync(this::run);
    }

    private void dt_2(ByteBuffer buffer) {
        while (pduLength > 0) {
            if (pdv == null) {
                if (buffer.remaining() < 4) {
                    setCarry(buffer);
                    return;
                }
                pduLength -= 4;
                int pdvLen = buffer.getInt();
                if (pdvLen == 0) {
                    continue;
                }
                pdv = ByteBufferPool.allocate(pdvLen);
            }
            pdv.put(buffer);
            if (pdv.hasRemaining()) {
                return;
            }
            pdv.flip();
            pduLength -= pdv.remaining();
            pdvQueue.offer(pdv);
            pdv = null;
        }
        action = null;
    }

    private void ar_1() {
        write(mkAReleaseRQ(), as -> as.changeState(State.STA_7));
    }

    private void ar_2(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        changeState(State.STA_8);
        write(mkAReleaseRP(), Association::closeAfterDelay);
        action = null;
    }

    private void ar_3(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        safeClose();
        action = null;
    }

    private void aa_3(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        safeClose();
        action = null;
    }

    private void aa_8(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        write(mkAAbort(), Association::closeAfterDelay);
        action = null;
    }

    private enum MCH {
        DATA(false, false),
        COMMAND(false, true),
        LAST_DATA(true, false),
        LAST_COMMAND(true, true);

        final boolean last;
        final boolean command;

        MCH(boolean last, boolean command) {
            this.last = last;
            this.command = command;
        }

        static MCH of(int b) {
            return MCH.values()[b & 3];
        }

        byte value() {
            return (byte) ordinal();
        }
    }

    private class PDVOutputStream extends OutputStream {

        final Byte pcid;
        final MCH mch;
        ByteBuffer pdu;
        int pdvPosition;

        private PDVOutputStream(Byte pcid, MCH mch, ByteBuffer pdu) {
            this.pcid = pcid;
            this.mch = mch;
            this.pdu = pdu;
            pdvPosition = pdu.position();
            pdu.position(pdvPosition + 6);
        }

        @Override
        public void write(int b) throws IOException {
            ensureRemaining();
            pdu.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int wlen;
            while (len > 0) {
                ensureRemaining();
                pdu.put(b, off, wlen = Math.min(pdu.remaining(), len));
                off += wlen;
                len -= wlen;
            }
        }

        private void ensureRemaining() {
            if (!pdu.hasRemaining()) {
                int size = pdu.limit();
                writePDataTF(writePDVHeader(mch));
                pdvPosition = 6;
                pdu = ByteBufferPool.allocate(size).position(12);
            }
        }

        ByteBuffer writePDVHeader(MCH mch) {
            pdu.putInt(pdvPosition, pdu.position() - pdvPosition - 4);
            pdu.put(pdvPosition + 4, pcid);
            pdu.put(pdvPosition + 5, mch.value());
            return pdu;
        }
    }

    private class PDVInputStream extends InputStream {

        final Byte pcid;
        MCH mch;
        ByteBuffer pdv;

        PDVInputStream(Byte pcid, MCH mcn, ByteBuffer pdv) {
            this.pcid = pcid;
            this.mch = mcn;
            this.pdv = pdv;
        }

        @Override
        public int read() throws IOException {
            return eof() ? -1 : pdv.get();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (eof()) {
                return -1;
            }
            int read = Math.min(pdv.remaining(), len);
            pdv.get(b, off, read);
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0L || eof()) {
                return 0;
            }
            long skip = Math.min(pdv.remaining(), n);
            pdv.position(pdv.position() + (int) skip);
            return skip;
        }

        private boolean eof() {
            while (!pdv.hasRemaining()) {
                ByteBufferPool.free(pdv);
                if (mch.last) {
                    return true;
                }
                pdv = nextPDV();
                pdv.get();
                requirePresentationContextID(pdv.get(), pcid);
                mch = requireMatchingPDV(MCH.of(pdv.get()), mch);
            }
            return false;
        }
    }

    private static byte requirePresentationContextID(byte pcid, byte expected) {
        if (pcid != expected) {
            throw new IllegalArgumentException(
                    "Unexpected Presentation Context ID: " + (pcid & 0xff) + " - expected "+ (pcid & 0xff));
        }
        return pcid;
    }

    private static MCH requireMatchingPDV(MCH mch, MCH expected) {
        return expected.command ? requireCommandPDV(mch) : requireDataPDV(mch);
    }

    private static MCH requireCommandPDV(MCH mch) {
        if (!mch.command) {
            throw new IllegalArgumentException("Unexpected PDV Data Fragment");
        }
        return mch;
    }

    private static MCH requireDataPDV(MCH mch) {
        if (mch.command) {
            throw new IllegalArgumentException("Unexpected PDV Command Fragment");
        }
        return mch;
    }
}

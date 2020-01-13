package org.dcm4che6.net;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.io.DicomEncoding;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.io.DicomOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final Handler handler;
    private final CompletableFuture<Association> aaacReceived = new CompletableFuture<>();
    private final CompletableFuture<Association> arrpReceived = new CompletableFuture<>();
    private final AtomicInteger messageID = new AtomicInteger();
    private final Semaphore readSemaphore = new Semaphore(0);
    private volatile ByteBuffer readBuffer;
    private int pduLength;
    private int pdvLength;
    private Byte pcid;
    private MCH mch;
    private State state = State.STA_1;
    private int resultSourceReason;
    private AAssociate.RQ aarq;
    private AAssociate.AC aaac;
    private BlockingQueue<OutstandingRSP> outstandingRSPs;
    private int maxPDULength;
    private String asname;
    private ApplicationEntity ae;

    public interface Handler extends DimseHandler {
        void onAAssociateRQ(Association as) throws AAssociateRJ;
    }

    public Association(TCPConnector<Association> connector, Connection local, Handler handler) {
        super(connector, local);
        this.handler = handler;
    }

    @Override
    void accepted(SelectionKey key) throws IOException {
        super.accepted(key);
        ae_5();
    }

    @Override
    boolean connect(SelectionKey key, SocketAddress remote) throws IOException {
        boolean connect = super.connect(key, remote);
        ae_1();
        return connect;
    }

    @Override
    public String toString() {
        return asname != null ? asname : super.toString();
    }

    AAssociate.CommonExtendedNegotation commonExtendedNegotationFor(String cuid) {
        return aarq.getCommonExtendedNegotation(cuid);
    }

    private void changeState(State state) {
        LOG.debug("{}: {}", this, state);
        this.state = state;
    }

    @Override
    protected void onNext(ByteBuffer buffer) {
        LOG.trace("{}: read {} bytes", this, buffer.remaining());
        this.readBuffer = buffer;
        interestOpsAnd(~SelectionKey.OP_READ);
        readSemaphore.release();
    }

    private void startReading() {
        CompletableFuture.runAsync(this::run);
    }

    private void run() {
        LOG.trace("{}: start reading", this);
        ByteBuffer buffer;
        while ((buffer = nextBuffer()) != null) {
            processNext(buffer);
        }
        LOG.trace("{}: stop reading", this);
    }

    private ByteBuffer nextBuffer() {
        if (!readSemaphore.tryAcquire())
            try {
                LOG.trace("{}: wait for read bytes", this);
                readSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        ByteBuffer buffer = readBuffer;
        readBuffer = null;
        if (buffer != null) {
            LOG.trace("{}: process {} bytes", this, buffer.remaining());
            interestOpsOr(SelectionKey.OP_READ);
        }
        return buffer;
    }

    private void processNext(ByteBuffer buffer) {
        while (!state.discard && buffer.hasRemaining()) {
            buffer = ensureRemaining(buffer, 10);
            int pduType = buffer.getShort() >>> 8;
            pduLength = buffer.getInt();
            buffer = state.action(this, pduType, pduLength, buffer);
        }
        ByteBufferPool.free(buffer);
    }

    private ByteBuffer ensureRemaining(ByteBuffer buffer, int remaining) {
        while (buffer.remaining() < remaining) {
            buffer = cat(buffer, nextBuffer());
        }
        return buffer;
    }

    private static ByteBuffer cat(ByteBuffer bb1, ByteBuffer bb2) {
        ByteBuffer bb = ByteBufferPool.allocate(bb1.remaining() + bb2.remaining());
        bb.put(bb1);
        bb.put(bb2);
        ByteBufferPool.free(bb1);
        ByteBufferPool.free(bb2);
        return bb.flip();
    }

    void onDimseRQ(Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) throws IOException {
        handler.accept(this, pcid, dimse, commandSet, dataStream);
    }

    void onDimseRSP(Byte pcid, Dimse dimse, DicomObject commandSet, DicomObject dataSet) {
        int messageID = commandSet.getInt(Tag.MessageIDBeingRespondedTo).getAsInt();
        OutstandingRSP outstandingRSP =
                outstandingRSPs.stream().filter(o -> o.messageID == messageID).findFirst().get();
        outstandingRSPs.remove(outstandingRSP);
        outstandingRSP.futureDimseRSP.complete(new DimseRSP(dimse, commandSet, dataSet));
    }

    void onCancelRQ(Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) {

    }

    private Byte requireAcceptedPresentationContext(Byte pcid) {
        AAssociate.AC.PresentationContext pc = aaac.getPresentationContext(pcid);
        if (pc == null || pc.result != AAssociate.AC.Result.ACCEPTANCE) {
            throw new IllegalArgumentException("No accepted Presentation Context with ID: " + (pcid & 0xff));
        }
        return pcid;
    }

    public void onAAssociateRQ() throws AAssociateRJ {
        ae = aeOf(aarq.getCalledAETitle());
        if (ae == null) {
            throw new AAssociateRJ(called_AE_title_not_recognized);
        }
        aaac = new AAssociate.AC();
        aaac.setCalledAETitle(aarq.getCalledAETitle());
        aaac.setCallingAETitle(aarq.getCallingAETitle());
        if (aarq.hasAsyncOpsWindow()) {
            aaac.setAsyncOpsWindow(aarq.getMaxOpsInvoked(), aarq.getMaxOpsPerformed());
        }
        aarq.forEachPresentationContext(this::negotiate);
    }

    private void negotiate(Byte pcid, AAssociate.RQ.PresentationContext pc) {
        Optional<TransferCapability> tc = getTransferCapability(pc.abstractSyntax());
        if (tc.isPresent()) {
            Optional<String> ts = tc.get().selectTransferSyntax(pc::containsTransferSyntax);
            if (ts.isPresent()) {
                aaac.putPresentationContext(pcid, AAssociate.AC.Result.ACCEPTANCE, ts.get());
            } else {
                aaac.putPresentationContext(pcid,
                        tc.get().anyTransferSyntax()
                                ? AAssociate.AC.Result.ACCEPTANCE
                                : AAssociate.AC.Result.TRANSFER_SYNTAXES_NOT_SUPPORTED,
                        pc.anyTransferSyntax());
            }
        } else {
            aaac.putPresentationContext(pcid,
                    AAssociate.AC.Result.ABSTRACT_SYNTAX_NOT_SUPPORTED, pc.anyTransferSyntax());
        }
    }

    private Optional<TransferCapability> getTransferCapability(String abstractSyntax) {
        AAssociate.RoleSelection roleSelection = aarq.getRoleSelection(abstractSyntax);
        if (roleSelection == null) {
            return ae.getTransferCapabilityOrDefault(TransferCapability.Role.SCP, abstractSyntax);
        }
        Optional<TransferCapability> scuTC = roleSelection.scu
                ? ae.getTransferCapabilityOrDefault(TransferCapability.Role.SCU, abstractSyntax)
                : Optional.empty();
        Optional<TransferCapability> scpTC = roleSelection.scp
                ? ae.getTransferCapabilityOrDefault(TransferCapability.Role.SCP, abstractSyntax)
                : Optional.empty();
        aaac.putRoleSelection(abstractSyntax,
                roleSelection = AAssociate.RoleSelection.of(scuTC.isPresent(), scpTC.isPresent()));
        return roleSelection == AAssociate.RoleSelection.SCU ? scuTC : scpTC;
    }

    private ApplicationEntity aeOf(String aeTitle) {
        Optional<Device> optDevice = local.getDevice();
        if (optDevice.isPresent()) {
            Optional<ApplicationEntity> optAE = optDevice.get().getApplicationEntityOrDefault(aeTitle);
            if (optAE.isPresent()) {
                ApplicationEntity ae = optAE.get();
                if (ae.isInstalled() && ae.hasConnection(local)) {
                    return ae;
                }
            }
        }
        return null;
    }

    private static ByteBuffer toBuffer(short pduType, AAssociate aaxx) {
        ByteBuffer buffer = ByteBufferPool.allocate();
        buffer.putShort(pduType);
        buffer.putInt(aaxx.pduLength());
        aaxx.writeTo(buffer);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer mkAReleaseRQ() {
        return toBuffer((short) 0x0500, 0);
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

    public void writeDimse(Byte pcid, Dimse dimse, DicomObject commandSet) throws IOException {
         writePDataTF(writeCommandSet(pcid, dimse, commandSet));
    }

    void writeDimse(Byte pcid, Dimse dimse, DicomObject commandSet, DataWriter dataWriter) throws IOException {
        writePDataTF(writeDataSet(pcid, dataWriter, writeCommandSet(pcid, dimse, commandSet)));
    }

    void writeDimse(Byte pcid, Dimse dimse, DicomObject commandSet, DicomObject dataSet) throws IOException {
        writeDimse(pcid, dimse, commandSet,((out, tsuid) ->
                new DicomOutputStream(out)
                    .withEncoding(DicomEncoding.of(getTransferSyntax(pcid)))
                    .writeDataSet(dataSet)));
    }

    private ByteBuffer writeDataSet(Byte pcid, DataWriter dataWriter, ByteBuffer buffer) throws IOException {
        LOG.debug("{} << Data start", this);
        PDVOutputStream pdv = new PDVOutputStream(pcid, MCH.DATA_PDV, buffer);
        dataWriter.writeTo(pdv, getTransferSyntax(pcid));
        buffer = pdv.writePDVHeader(MCH.LAST_DATA_PDV);
        LOG.debug("{} << Data finished", this);
        return buffer;
    }

    private ByteBuffer writeCommandSet(Byte pcid, Dimse dimse, DicomObject commandSet) throws IOException {
        LOG.info("{} << {}", this, dimse.toString(pcid, commandSet, getTransferSyntax(pcid)));
        LOG.debug("{} << Command:\n{}", this, commandSet);
        ByteBuffer buffer = ByteBufferPool.allocate(maxPDULength + 6).position(6);
        PDVOutputStream pdv = new PDVOutputStream(pcid, MCH.COMMAND_PDV, buffer);
        new DicomOutputStream(pdv).writeCommandSet(commandSet);
        return pdv.writePDVHeader(MCH.LAST_COMMAND_PDV);
    }

    private void writePDataTF(ByteBuffer buffer) {
        buffer.flip();
        int pduLength = buffer.remaining() - 6;
        buffer.putShort(0, (short) 0x0400);
        buffer.putInt(2, pduLength);
        LOG.debug("{} << P-DATA-TF[length: {}]", this, pduLength);
        write(buffer, x -> {});
    }

    private void closeAfterDelay() {
        CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS).execute(this::safeClose);
        changeState(State.STA_13);
    }

    private void safeClose() {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        changeState(State.STA_1);
        readSemaphore.release();
    }

    public String getTransferSyntax(Byte pcid) {
        return aaac.getPresentationContext(pcid).transferSyntax;
    }

    public CompletableFuture<Association> open(AAssociate.RQ aarq) {
        state.open(this, Objects.requireNonNull(aarq));
        return aaacReceived;
    }

    public CompletableFuture<DimseRSP> cecho() throws IOException, InterruptedException {
        return cecho(UID.VerificationSOPClass);
    }

    public CompletableFuture<DimseRSP> cecho(String sopClassUID) throws IOException, InterruptedException {
        int msgid = messageID.incrementAndGet();
        return invoke(sopClassUID, msgid, Dimse.C_ECHO_RQ,
                Dimse.C_ECHO_RQ.mkRQ(msgid, sopClassUID, null, Dimse.NO_DATASET));
    }

    public CompletableFuture<DimseRSP> cstore(String sopClassUID, String sopInstanceUID,
            DataWriter dataWriter, String transferSyntax) throws IOException, InterruptedException {
        int msgid = messageID.incrementAndGet();
        return invoke(sopClassUID, msgid, Dimse.C_STORE_RQ,
                Dimse.C_STORE_RQ.mkRQ(msgid, sopClassUID, sopInstanceUID, Dimse.WITH_DATASET),
                dataWriter, transferSyntax);
    }

    private CompletableFuture<DimseRSP> invoke(String abstractSyntax, int msgid, Dimse dimse, DicomObject commandSet)
            throws IOException, InterruptedException {
        Byte pcid = pcidFor(abstractSyntax);
        OutstandingRSP outstandingRSP = new OutstandingRSP(msgid, new CompletableFuture<>());
        outstandingRSPs.put(outstandingRSP);
        writeDimse(pcid, dimse, commandSet);
        return outstandingRSP.futureDimseRSP;
    }

    private CompletableFuture<DimseRSP> invoke(String abstractSyntax, int msgid, Dimse dimse, DicomObject commandSet,
            DataWriter dataWriter, String transferSyntax) throws IOException, InterruptedException {
        Byte pcid = pcidFor(abstractSyntax, transferSyntax);
        OutstandingRSP outstandingRSP = new OutstandingRSP(msgid, new CompletableFuture<>());
        outstandingRSPs.put(outstandingRSP);
        writeDimse(pcid, dimse, commandSet, dataWriter);
        return outstandingRSP.futureDimseRSP;
    }

    private Byte pcidFor(String abstractSyntax) {
        return aarq.pcidsFor(abstractSyntax)
                .filter(aaac::isAcceptance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No accepted Presentation Context"));
    }

    private Byte pcidFor(String abstractSyntax, String transferSyntax) {
        return aarq.pcidsFor(abstractSyntax, transferSyntax)
                .filter(pcid -> aaac.acceptedTransferSyntax(pcid, transferSyntax))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No accepted Presentation Context"));
    }

    public CompletableFuture<Association> release() {
        state.release(this);
        return arrpReceived;
    }

    @Override
    protected void connected() {
        state.connected(this);
        super.connected();
    }

    private enum State {
        STA_1(false, true, "Sta1 - Idle"),
        STA_2(true, false, "Sta2 - Transport connection open (Awaiting A-ASSOCIATE-RQ PDU)") {
            @Override
            ByteBuffer onAAssociateRQ(Association as, ByteBuffer buffer) {
                return as.ae_6(buffer);
            }
        },
        STA_3(true, true, "Sta3 - Awaiting local A-ASSOCIATE response primitive (from local user)"),
        STA_4(true, true, "Sta4 - Awaiting transport connection opening to complete (from local transport service)") {
            @Override
            public void connected(Association as) {
                as.changeState(STA_4a);
                as.startReading();
            }
        },
        STA_4a(true, true, "Sta4a - Awaiting local A-ASSOCIATE request primitive (from local user)") {
            @Override
            public void open(Association as, AAssociate.RQ aarq) {
                as.ae_2(aarq);
            }
        },
        STA_5(true, false, "Sta5 - Awaiting A-ASSOCIATE-AC or A-ASSOCIATE-RJ PDU"){
            @Override
            ByteBuffer onAAssociateAC(Association as, ByteBuffer buffer) {
                return as.ae_3(buffer);
            }

            @Override
            ByteBuffer onAAssociateRJ(Association as, ByteBuffer buffer) {
                return as.ae_4(buffer);
            }
        },
        STA_6(true, false, "Sta6 - Association established and ready for data transfer") {
            @Override
            ByteBuffer onPDataTF(Association as, ByteBuffer buffer) {
                return as.dt_2(buffer);
            }

            @Override
            ByteBuffer onAReleaseRQ(Association as, ByteBuffer buffer) {
                return as.ar_2(buffer);
            }

            @Override
            public void release(Association as) {
                as.ar_1();
            }
        },
        STA_7(true, false, "Sta7 - Awaiting A-RELEASE-RP PDU") {
            @Override
            ByteBuffer onPDataTF(Association as, ByteBuffer buffer) {
                return as.dt_2(buffer);
            }

            @Override
            ByteBuffer onAReleaseRP(Association as, ByteBuffer buffer) {
                return as.ar_3(buffer);
            }
        },
        STA_8(true, true, "Sta8 - Awaiting local A-RELEASE response primitive (from local user)"),
        STA_9(true, true, "Sta9 - Release collision requestor side; awaiting A-RELEASE response (from local user)"),
        STA_10(true, false, "Sta10 - Release collision acceptor side; awaiting A-RELEASE-RP PDU"),
        STA_11(true, false, "Sta11 - Release collision requestor side; awaiting A-RELEASE-RP PDU"),
        STA_12(true, true, "Sta12 - Release collision acceptor side; awaiting A-RELEASE response primitive (from local user)"),
        STA_13(false, true, "Sta13 - Awaiting Transport Connection Close Indication (Association no longer exists)");

        final boolean reading;
        final boolean discard;
        final String description;

        State(boolean reading, boolean discard, String description) {
            this.reading = reading;
            this.discard = discard;
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }

        ByteBuffer action(Association as, int pduType, int pduLength, ByteBuffer buffer) {
            switch (pduType) {
                case 1:
                    LOG.info("{} >> A-ASSOCIATE-RQ", as);
                    return onAAssociateRQ(as, buffer);
                case 2:
                    LOG.info("{} >> A-ASSOCIATE-AC", as);
                    return onAAssociateAC(as, buffer);
                case 3:
                    LOG.info("{} >> {}", as, AAssociateRJ.toString(buffer.getInt(buffer.position())));
                    return onAAssociateRJ(as, buffer);
                case 4:
                    LOG.debug("{} >> P-DATA-TF[length: {}]", as, pduLength);
                    return onPDataTF(as, buffer);
                case 5:
                    LOG.info("{} >> A-RELEASE-RQ", as);
                    return onAReleaseRQ(as, buffer);
                case 6:
                    LOG.info("{} >> A-RELEASE-RP", as);
                    return onAReleaseRP(as, buffer);
                case 7:
                    LOG.info("{} >> A-ABORT", as);
                    return onAAbort(as, buffer);
            }
            return onInvalidPDU(as, buffer, pduType);
        }

        ByteBuffer onAAssociateRQ(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onAAssociateAC(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onAAssociateRJ(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onPDataTF(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onAReleaseRQ(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onAReleaseRP(Association as, ByteBuffer buffer) {
            return as.aa_8(buffer);
        }

        ByteBuffer onAAbort(Association as, ByteBuffer buffer) {
            return as.aa_3(buffer);
        }

        ByteBuffer onInvalidPDU(Association as, ByteBuffer buffer, int pduType) {
            return as.aa_8(buffer);
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
        asname = aarq.getCallingAETitle() + "->" + aarq.getCalledAETitle() + "(" + id + ")";
        ByteBuffer buffer = toBuffer((short) 0x0100, this.aarq);
        LOG.info("{} << A-ASSOCIATE-RQ", this);
        LOG.debug("{}", aarq);
        write(buffer, as -> as.changeState(State.STA_5));
    }

    private ByteBuffer ae_3(ByteBuffer buffer) {
        buffer = ensureRemaining(buffer, pduLength);
        aaac = new AAssociate.AC(buffer, pduLength);
        LOG.debug("{}", aaac);
        maxPDULength = aaac.getMaxPDULength();
        outstandingRSPs = newBlockingQueue(aaac.getMaxOpsInvoked());
        onEstablished();
        aaacReceived.complete(this);
        return buffer;
    }

    private static BlockingQueue<OutstandingRSP> newBlockingQueue(int limit) {
        return new LinkedBlockingQueue<>(limit > 0 ? limit : Integer.MAX_VALUE);
    }

    private ByteBuffer ae_4(ByteBuffer buffer) {
        buffer = ensureRemaining(buffer, pduLength);
        resultSourceReason = buffer.getInt();
        aaacReceived.completeExceptionally(new AAssociateRJ(resultSourceReason));
        safeClose();
        return buffer;
    }

    private void ae_5() {
        changeState(State.STA_2);
        startReading();
    }

    private ByteBuffer ae_6(ByteBuffer buffer) {
        buffer = ensureRemaining(buffer, pduLength);
        aarq = new AAssociate.RQ(buffer, pduLength);
        asname = aarq.getCalledAETitle() + "<-" + aarq.getCallingAETitle() + "(" + id + ")";
        LOG.debug("{}", aarq);
        changeState(State.STA_3);
        try {
            handler.onAAssociateRQ(this);
            maxPDULength = aarq.getMaxPDULength();
            outstandingRSPs = newBlockingQueue(aaac.getMaxOpsPerformed());
            writeAAAC();
        } catch (AAssociateRJ aarj) {
            LOG.info("{} << {}", this, aarj.getMessage());
            write(toBuffer((short) 0x0300, aarj.resultSourceReason), Association::closeAfterDelay);
        }
        return buffer;
    }

    private void writeAAAC() {
        ByteBuffer buffer = toBuffer((short) 0x0200, aaac);
        LOG.info("{} << A-ASSOCIATE-AC", this);
        LOG.debug("{}", aaac);
        write(buffer, Association::onEstablished);
    }

    private void writeARRP() {
        LOG.info("{} << A-RELEASE-RP", this);
        write(toBuffer((short) 0x0600, 0), Association::closeAfterDelay);
    }

    private void onEstablished() {
        changeState(State.STA_6);
//        CompletableFuture.runAsync(this::run);
    }

/*
    private void markEndOfPDVs() {
        key.interestOpsAnd(~SelectionKey.OP_READ);
        LOG.trace("{}: Notify worker thread about END_OF_PDVS", asname);
        pdvSemaphore.release();
    }
*/
    private ByteBuffer dt_2(ByteBuffer buffer) {
        try {
            buffer = ensureRemaining(buffer, 6);
            readPDVHeader(buffer);
            requireAcceptedPresentationContext(pcid);
            requireCommandPDV(mch);
            PDVInputStream commandStream = new PDVInputStream(buffer);
            DicomObject commandSet = new DicomInputStream(commandStream).readCommandSet();
            Dimse dimse = Dimse.of(commandSet);
            LOG.info("{} >> {}", this, dimse.toString(pcid, commandSet, getTransferSyntax(pcid)));
            LOG.debug("{} >> Command:\n{}", this, commandSet);
            buffer = commandStream.buffer;
            if (!Dimse.hasDataSet(commandSet)) {
                dimse.handler.accept(this, pcid, dimse, commandSet, null);
            } else {
                if (pduLength == 0) {
                    buffer = ensureRemaining(buffer, 10);
                    if (buffer.getShort() >>> 8 != 4) {
                        buffer.position(buffer.position() - 2);
                        return buffer;
                    }
                    pduLength = buffer.getInt();
                    LOG.debug("{} >> P-DATA-TF[length: {}]", this, pduLength);
                }
                buffer = ensureRemaining(buffer, 6);
                readPDVHeader(buffer);
                Byte pcid0 = pcid;
                requirePresentationContextID(pcid, pcid0);
                requireDataPDV(mch);
                PDVInputStream dataStream = new PDVInputStream(buffer);
                dimse.handler.accept(this, pcid, dimse, commandSet, dataStream);
                buffer = dataStream.buffer;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    private void ar_1() {
        LOG.info("{} << A-RELEASE-RQ", this);
        write(mkAReleaseRQ(), as -> as.changeState(State.STA_7));
    }

    private ByteBuffer ar_2(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        changeState(State.STA_8);
        writeARRP();
        return buffer;
    }

    private ByteBuffer ar_3(ByteBuffer buffer) {
        arrpReceived.complete(this);
        buffer.position(buffer.limit());
        safeClose();
        return buffer;
    }

    private void aa_2() {
        safeClose();
        changeState(State.STA_1);
        readSemaphore.release();
    }

    private ByteBuffer aa_3(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        safeClose();
        return buffer;
    }

    private ByteBuffer aa_8(ByteBuffer buffer) {
        buffer.position(buffer.limit());
        write(mkAAbort(), Association::closeAfterDelay);
        return buffer;
    }

    private enum MCH {
        DATA_PDV(false, false),
        COMMAND_PDV(false, true),
        LAST_DATA_PDV(true, false),
        LAST_COMMAND_PDV(true, true);

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

    private void readPDVHeader(ByteBuffer buffer) {
        pdvLength = buffer.getInt();
        pcid = buffer.get();
        mch = MCH.of(buffer.get());
        LOG.debug("{} >> PDV[length: {}, pcid: {}, mch: {}]", this, pdvLength, pcid, mch.value());
        pduLength -= 4 + pdvLength;
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
            int pdvLen = pdu.position() - pdvPosition - 4;
            LOG.debug("{} << PDV[length: {}, pcid: {}, mch: {}]", asname, pdvLen, pcid, mch.value());
            pdu.putInt(pdvPosition, pdvLen);
            pdu.put(pdvPosition + 4, pcid);
            pdu.put(pdvPosition + 5, mch.value());
            return pdu;
        }
    }

    private class PDVInputStream extends InputStream {
        ByteBuffer buffer;
        int pdvRemaining;

        PDVInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
            pdvRemaining = pdvLength - 2;
        }

        @Override
        public int read() {
            if (eof()) {
                return -1;
            }
            pdvRemaining--;
            return buffer.get() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) {
                return 0;
            }
            if (eof()) {
                return -1;
            }
            int read = Math.min(buffer.remaining(), Math.min(pdvRemaining, len));
            buffer.get(b, off, read);
            pdvRemaining -= read;
            return read;
        }

        @Override
        public long skip(long n) {
            if (n <= 0L || eof()) {
                return 0;
            }
            int skip = Math.min(buffer.remaining(), (int) Math.min(pdvRemaining, n));
            buffer.position(buffer.position() + skip);
            pdvRemaining =- skip;
            return skip;
        }

        private boolean eof() {
            while (pdvRemaining == 0 || !buffer.hasRemaining()) {
                if (pdvRemaining == 0 && mch.last) {
                    return true;
                }
                if (!buffer.hasRemaining()) {
                    ByteBufferPool.free(buffer);
                    buffer = nextBuffer();
                }
                if (pdvRemaining == 0) {
                    if (pduLength == 0) {
                        buffer = ensureRemaining(buffer, 10);
                        if (buffer.getShort() >>> 8 != 4) {
                            buffer.position(buffer.position() - 2);
                            return true;
                        }
                        pduLength = buffer.getInt();
                        LOG.debug("{} >> P-DATA-TF[length: {}]", Association.this, pduLength);
                    }
                    buffer = ensureRemaining(buffer, 6);
                    Byte pcid0 = pcid;
                    MCH mch0 = mch;
                    readPDVHeader(buffer);
                    requirePresentationContextID(pcid, pcid0);
                    mch = requireMatchingPDV(mch, mch0);
                    pdvRemaining = Association.this.pdvLength - 2;
                }
            }
            return false;
        }
    }

    private static byte requirePresentationContextID(Byte pcid, Byte expected) {
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

    @FunctionalInterface
    public interface DataWriter {
        void writeTo(OutputStream out, String tsuid) throws IOException;
    }

    private static class OutstandingRSP {
        final int messageID;
        final CompletableFuture<DimseRSP> futureDimseRSP;

        private OutstandingRSP(int messageID, CompletableFuture<DimseRSP> futureDimseRSP) {
            this.messageID = messageID;
            this.futureDimseRSP = futureDimseRSP;
        }
    }
}

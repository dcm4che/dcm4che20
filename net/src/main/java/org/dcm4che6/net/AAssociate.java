package org.dcm4che6.net;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.data.Implementation;
import org.dcm4che6.data.UID;
import org.dcm4che6.util.UIDUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public abstract class AAssociate {
    public static final int DEF_MAX_PDU_LENGTH = 16378;
    // to fit into SunJSSE TLS Application Data Length 16408

    private int protocolVersion = 1;
    private String calledAETitle = "ANONYMOUS";
    private String callingAETitle = "ANONYMOUS";
    private String applicationContextName = UID.DICOMApplicationContextName;
    private int maxPDULength = DEF_MAX_PDU_LENGTH;
    private String implClassUID = Implementation.CLASS_UID;
    private String implVersionName = Implementation.VERSION_NAME;
    private int asyncOpsWindow = -1;
    private final Map<String, RoleSelection> roleSelectionMap = new LinkedHashMap<>();
    private final Map<String, byte[]> extNegMap = new LinkedHashMap<>();

    public String getCalledAETitle() {
        return calledAETitle;
    }

    public void setCalledAETitle(String calledAETitle) {
        this.calledAETitle = calledAETitle;
    }

    public String getCallingAETitle() {
        return callingAETitle;
    }

    public void setCallingAETitle(String callingAETitle) {
        this.callingAETitle = callingAETitle;
    }

    public String getApplicationContextName() {
        return applicationContextName;
    }

    public void setApplicationContextName(String applicationContextName) {
        this.applicationContextName = applicationContextName;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getImplClassUID() {
        return implClassUID;
    }

    public void setImplClassUID(String implClassUID) {
        this.implClassUID = implClassUID;
    }

    public String getImplVersionName() {
        return implVersionName;
    }

    public void setImplVersionName(String implVersionName) {
        this.implVersionName = implVersionName;
    }

    public int getMaxPDULength() {
        return maxPDULength;
    }

    public void setMaxPDULength(int maxPDULength) {
        this.maxPDULength = maxPDULength;
    }

    public int getMaxOpsInvoked() {
        return asyncOpsWindow != -1 ? asyncOpsWindow >> 16 : 1;
    }

    public int getMaxOpsPerformed() {
        return asyncOpsWindow != -1 ? asyncOpsWindow & 0xffff : 1;
    }

    public void setAsyncOpsWindow(int maxOpsInvoked, int maxOpsPerformed) {
        this.asyncOpsWindow = maxOpsInvoked << 16 | maxOpsPerformed;
    }

    public void clearAsyncOpsWindow() {
        this.asyncOpsWindow = -1;
    }

    public boolean hasAsyncOpsWindow() {
        return asyncOpsWindow != -1;
    }

    public void putRoleSelection(String cuid, RoleSelection roleSelection) {
        roleSelectionMap.put(cuid, roleSelection);
    }

    public RoleSelection getRoleSelection(String cuid) {
        return roleSelectionMap.get(cuid);
    }

    public void putExtendedNegotation(String cuid, byte[] extNeg) {
        extNegMap.put(cuid, extNeg.clone());
    }

    public byte[] getExtendedNegotation(String cuid) {
        return clone(extNegMap.get(cuid));
    }

    protected String toString(String header) {
        StringBuilder sb = new StringBuilder(512)
                .append(header)
                .append(System.lineSeparator())
                .append("  called-AE-title: ")
                .append(calledAETitle)
                .append(System.lineSeparator())
                .append("  calling-AE-title: ")
                .append(callingAETitle)
                .append(System.lineSeparator())
                .append("  application-context-name: ");
        UIDUtils.promptTo(applicationContextName, sb)
                .append(System.lineSeparator())
                .append("  implementation-class-uid: ")
                .append(implClassUID)
                .append(System.lineSeparator());
        if (implVersionName != null) {
            sb.append("  implementation-version-name: ")
                    .append(implVersionName)
                    .append(System.lineSeparator());
        }
        sb.append("  max-pdu-length: ")
                .append(maxPDULength)
                .append(System.lineSeparator());
        if (asyncOpsWindow != -1) {
            sb.append("  max-ops-invoked: ")
                    .append(asyncOpsWindow >> 16)
                    .append(System.lineSeparator())
                    .append("  max-ops-performed: ")
                    .append(asyncOpsWindow & 0xffff)
                    .append(System.lineSeparator());
        }
        promptUserIdentityTo(sb);
        promptPresentationContextsTo(sb);
        roleSelectionMap.forEach((cuid, rs) -> promptTo(cuid, rs, sb));
        extNegMap.forEach((cuid, b) -> promptTo(cuid, b, sb));
        promptCommonExtendedNegotationTo(sb);
        return sb.append(']').toString();
    }

    private void promptTo(String cuid, RoleSelection roleSelection, StringBuilder sb) {
        sb.append("  RoleSelection[")
                .append(System.lineSeparator())
                .append("    sop-class: ");
        UIDUtils.promptTo(cuid, sb)
                .append(System.lineSeparator())
                .append("    role(s): ").append(roleSelection)
                .append(System.lineSeparator())
                .append("  ]");
    }

    private void promptTo(String cuid, byte[] info, StringBuilder sb) {
        sb.append("  ExtendedNegotiation[")
                .append(System.lineSeparator())
                .append("    sop-class: ");
        UIDUtils.promptTo(cuid, sb)
                .append(System.lineSeparator())
                .append("    info: [");
        for (byte b : info) {
            sb.append(b).append(", ");
        }
        sb.append(']')
                .append(System.lineSeparator())
                .append("  ]");
    }

    protected abstract void promptUserIdentityTo(StringBuilder sb);

    protected abstract void promptPresentationContextsTo(StringBuilder sb);

    protected void promptCommonExtendedNegotationTo(StringBuilder sb) {}

    static byte[] clone(byte[] value) {
        return value != null ? value.clone() : null;
    }

    void parse(ByteBuffer buffer, int pduLength) {
        byte[] b64 = new byte[64];
        int pduEnd = buffer.position() + pduLength;
        protocolVersion = buffer.getInt() >> 16;
        buffer.get(b64);
        calledAETitle = new String(b64, 0, 0, 16).trim();
        callingAETitle = new String(b64, 0, 16, 32).trim();
        applicationContextName = null;
        maxPDULength = -1;
        implClassUID = null;
        implVersionName = null;
        while (buffer.position() < pduEnd) {
            parseItem(buffer, buffer.getInt(), b64);
        }
        if (applicationContextName == null) {
            throw new IllegalArgumentException("Missing Application Context Item");
        }
        if (maxPDULength == -1) {
            throw new IllegalArgumentException("Missing Maximum Length Sub-Item");
        }
        if (implClassUID == null) {
            throw new IllegalArgumentException("Missing Implementation Class UID Sub-Item");
        }
    }

    void parseItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
        switch (itemTypeLength >>> 24) {
            case 0x10:
                applicationContextName =  ByteBufferUtils.getASCII(buffer, itemTypeLength & 0xffff, b64);
                break;
            case 0x50:
                parseUserItems(buffer, itemTypeLength & 0xffff, b64);
                break;
        }
    }

    private void parseUserItems(ByteBuffer buffer, int itemLength, byte[] b64) {
        int itemEnd = buffer.position() + itemLength;
        while (buffer.position() < itemEnd) {
            parseUserItem(buffer, buffer.getInt(), b64);
        }
    }

    void parseUserItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
        switch (itemTypeLength >>> 24) {
            case 0x51:
                maxPDULength = buffer.getInt();
                break;
            case 0x52:
                implClassUID =  ByteBufferUtils.getASCII(buffer, itemTypeLength & 0xffff, b64);
                break;
            case 0x53:
                asyncOpsWindow =  buffer.getInt();
                break;
            case 0x54:
                parseRoleSelection(buffer, b64);
                break;
            case 0x55:
                implVersionName =  ByteBufferUtils.getASCII(buffer, itemTypeLength & 0xffff, b64);
                break;
            case 0x56:
                parseExtendedNegotation(buffer, itemTypeLength & 0xffff, b64);
                break;
        }
    }

    private void parseRoleSelection(ByteBuffer buffer, byte[] b64) {
        roleSelectionMap.put(
                ByteBufferUtils.getASCII(buffer, buffer.getShort(), b64),
                RoleSelection.parse(buffer));
    }

    private void parseExtendedNegotation(ByteBuffer buffer, int itemLength, byte[] b64) {
        int uidLen = buffer.getShort();
        extNegMap.put(
                ByteBufferUtils.getASCII(buffer, uidLen, b64),
                ByteBufferUtils.getBytes(buffer, itemLength - (2 + uidLen)));
    }

    int pduLength() {
        return 76 + applicationContextName.length() + presentationContextLength() + userItemLength();
    }

    abstract int presentationContextLength();

    int userItemLength() {
        return (asyncOpsWindow != -1 ? 24 : 16)
                + implClassUID.length()
                + implVersionName.length()
                + roleSelectionMap.keySet().stream().mapToInt(cuid -> 8 + cuid.length()).sum()
                + extNegMap.entrySet().stream()
                    .mapToInt(e -> 6 + e.getKey().length() + e.getValue().length)
                    .sum();
    }

    void writeTo(ByteBuffer buffer) {
        byte[] b64 = new byte[64];
        buffer.putInt(protocolVersion << 16);
        Arrays.fill(b64, 0, 32, (byte) 0x20);
        calledAETitle.getBytes(0, calledAETitle.length(), b64, 0);
        callingAETitle.getBytes(0, callingAETitle.length(), b64, 16);
        buffer.put(b64);
        buffer.putShort((short) 0x1000);
        ByteBufferUtils.putLengthASCII(buffer, applicationContextName, b64);
        writePresentationContextTo(buffer, b64);
        writeUserItemTo(buffer, b64);
    }

    void writeUserItemTo(ByteBuffer buffer, byte[] b64) {
        buffer.putShort((short) 0x5000);
        buffer.putShort((short) userItemLength());
        buffer.putInt(0x51000004);
        buffer.putInt(maxPDULength);
        buffer.putShort((short) 0x5200);
        ByteBufferUtils.putLengthASCII(buffer, implClassUID, b64);
        buffer.putShort((short) 0x5500);
        ByteBufferUtils.putLengthASCII(buffer, implVersionName, b64);
        if (asyncOpsWindow != -1) {
            buffer.putInt(0x53000004);
            buffer.putInt(asyncOpsWindow);
        }
        roleSelectionMap.forEach((cuid,roleSel) -> {
            buffer.putShort((short) 0x5400);
            buffer.putShort((short) (4 + cuid.length()));
            ByteBufferUtils.putLengthASCII(buffer, cuid, b64);
            roleSel.writeTo(buffer);
        });
        extNegMap.forEach((cuid,extNeg) -> {
            buffer.putShort((short) 0x5600);
            buffer.putShort((short) (2 + cuid.length() + extNeg.length));
            ByteBufferUtils.putLengthASCII(buffer, cuid, b64);
            buffer.put(extNeg);
        });
    }

    abstract void writePresentationContextTo(ByteBuffer buffer, byte[] b64);

    public static class RQ extends AAssociate {

        private final Map<Byte, PresentationContext> pcs = new LinkedHashMap<>();
        private final Map<String, CommonExtendedNegotation> commonExtNegMap = new LinkedHashMap<>();
        private UserIdentity userIdentity;

        public RQ() {}

        public RQ(ByteBuffer buffer, int pduLength) {
            parse(buffer, pduLength);
        }

        public void putPresentationContext(Byte id, String abstractSyntax, String... transferSyntaxes) {
            pcs.put(id, new PresentationContext(abstractSyntax, transferSyntaxes));
        }

        public PresentationContext getPresentationContext(Byte id) {
            return pcs.get(id);
        }

        public void forEachPresentationContext(BiConsumer<Byte, PresentationContext> action) {
            pcs.forEach(action);
        }

        Stream<Byte> pcidsFor(String abstractSyntax) {
            return pcs.entrySet().stream()
                    .filter(e -> abstractSyntax.endsWith(e.getValue().abstractSyntax))
                    .map(Map.Entry::getKey);
        }

        public void putCommonExtendedNegotation(String cuid, String serviceClass, String... relatedSOPClasses) {
            commonExtNegMap.put(cuid, new CommonExtendedNegotation(serviceClass, relatedSOPClasses));
        }

        public CommonExtendedNegotation getCommonExtendedNegotation(String cuid) {
            return commonExtNegMap.get(cuid);
        }

        public UserIdentity getUserIdentity() {
            return userIdentity;
        }

        public void setUserIdentity(int type, boolean positiveResponseRequested, byte[] primaryField) {
            setUserIdentity(type, positiveResponseRequested, primaryField, new byte[0]);
        }

        public void setUserIdentity(int type, boolean positiveResponseRequested, byte[] primaryField,
                byte[] secondaryField) {
            this.userIdentity = new UserIdentity(type, positiveResponseRequested, primaryField, secondaryField);
        }

        @Override
        public String toString() {
            return toString("A-ASSOCIATE-RQ[");
        }

        @Override
        protected void promptUserIdentityTo(StringBuilder sb) {
            if (userIdentity != null) userIdentity.promptTo(sb);
        }

        @Override
        protected void promptPresentationContextsTo(StringBuilder sb) {
            pcs.forEach((pcid, pc) -> pc.promptTo(pcid, sb));
        }

        @Override
        protected void promptCommonExtendedNegotationTo(StringBuilder sb) {
            commonExtNegMap.forEach((cuid, cen) -> cen.promptTo(cuid, sb));
        }

        @Override
        protected void parseItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
            switch (itemTypeLength >>> 24) {
                case 0x20:
                    pcs.put((byte) (buffer.getShort() >>> 8),
                            new PresentationContext(buffer, itemTypeLength & 0xffff, b64));
                    break;
                default:
                    super.parseItem(buffer, itemTypeLength, b64);
            }
        }

        private void parseCommonExtendedNegotation(ByteBuffer buffer, int itemLength, byte[] b64) {
            int uidLen = buffer.getShort();
            commonExtNegMap.put(
                    ByteBufferUtils.getASCII(buffer, uidLen, b64),
                    new CommonExtendedNegotation(buffer, b64));
        }

        @Override
        void parseUserItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
            switch (itemTypeLength >>> 24) {
                case 0x57:
                    parseCommonExtendedNegotation(buffer, itemTypeLength & 0xffff, b64);
                    break;
                case 0x58:
                    userIdentity = new UserIdentity(buffer);
                    break;
                default:
                    super.parseUserItem(buffer, itemTypeLength, b64);
            }
        }

        @Override
        int presentationContextLength() {
            return pcs.values().stream().mapToInt(pc -> 4 + pc.itemLength()).sum();
        }

        @Override
        int userItemLength() {
            return super.userItemLength()
                    + commonExtNegMap.entrySet().stream()
                        .mapToInt(e -> 6 + e.getKey().length() + e.getValue().length())
                        .sum()
                    + (userIdentity != null ? 4 + userIdentity.itemLength() : 0);
        }

        @Override
        void writeUserItemTo(ByteBuffer buffer, byte[] b64) {
            super.writeUserItemTo(buffer, b64);
            commonExtNegMap.forEach((cuid,commonExtNeg) -> {
                buffer.putShort((short) 0x5700);
                buffer.putShort((short) (2 + cuid.length() + commonExtNeg.length()));
                ByteBufferUtils.putLengthASCII(buffer, cuid, b64);
                commonExtNeg.writeTo(buffer, b64);
            });
            if (userIdentity != null) {
                buffer.putShort((short) 0x5800);
                buffer.putShort((short) userIdentity.itemLength());
                userIdentity.writeTo(buffer);
            }
        }

        @Override
        void writePresentationContextTo(ByteBuffer buffer, byte[] b64) {
            pcs.forEach((id, pc) -> writeTo(id, pc, buffer, b64));
        }

        private void writeTo(Byte id, PresentationContext pc, ByteBuffer buffer, byte[] b64) {
            buffer.putShort((short) 0x2000);
            buffer.putShort((short) pc.itemLength());
            buffer.putShort((short) (id.intValue() << 8));
            pc.writeTo(buffer, b64);
        }

        public static class PresentationContext {

            private String abstractSyntax;
            private final List<String> transferSyntaxList = new ArrayList<>();

            PresentationContext(String abstractSyntax, String... transferSyntaxes) {
                this.abstractSyntax = abstractSyntax;
                this.transferSyntaxList.addAll(List.of(transferSyntaxes));
            }

            PresentationContext(ByteBuffer buffer, int itemLength, byte[] b64) {
                int itemEnd = buffer.position() + itemLength - 2;
                buffer.position(buffer.position() + 2);
                while (buffer.position() < itemEnd) {
                    parseSubItem(buffer.getShort() >>> 8, buffer, buffer.getShort(), b64);
                }
            }

            public String abstractSyntax() {
                return abstractSyntax;
            }

            public String anyTransferSyntax() {
                return transferSyntaxList.get(0);
            }

            public String[] transferSyntax() {
                return transferSyntaxList.toArray(new String[0]);
            }

            public boolean containsTransferSyntax(String transferSyntax) {
                return transferSyntaxList.contains(transferSyntax);
            }

            private void parseSubItem(int itemType, ByteBuffer buffer, int itemLength, byte[] b64) {
                switch (itemType) {
                    case 0x30:
                        abstractSyntax =  ByteBufferUtils.getASCII(buffer, itemLength, b64);
                        break;
                    case 0x40:
                        transferSyntaxList.add(ByteBufferUtils.getASCII(buffer, itemLength, b64));
                        break;
                }
            }

            int itemLength() {
                return 8 + abstractSyntax.length()
                        + transferSyntaxList.stream().mapToInt(s -> 4 + s.length()).sum();
            }

            void writeTo(ByteBuffer buffer, byte[] b64) {
                buffer.putInt(0x3000);
                ByteBufferUtils.putLengthASCII(buffer, abstractSyntax, b64);
                transferSyntaxList.stream().forEach(
                        s -> {
                            buffer.putShort((short) 0x4000);
                            ByteBufferUtils.putLengthASCII(buffer, s, b64);
                        });
            }

            void promptTo(Byte pcid, StringBuilder sb) {
                sb.append("  PresentationContext[pcid: ")
                        .append(pcid)
                        .append(System.lineSeparator())
                        .append("    abstract-syntax: ");
                UIDUtils.promptTo(abstractSyntax, sb)
                        .append(System.lineSeparator());
                transferSyntaxList.forEach(ts ->
                        UIDUtils.promptTo(ts, sb.append("    transfer-syntax: "))
                            .append(System.lineSeparator()));
                sb.append("  ]")
                        .append(System.lineSeparator());
            }
        }
    }

    public static class AC extends AAssociate {

        private final Map<Byte, PresentationContext> pcs = new LinkedHashMap<>();
        private byte[] userIdentityServerResponse;

        public AC() {}

        public AC(ByteBuffer buffer, int pduLength) {
            parse(buffer, pduLength);
        }

        public void putPresentationContext(Byte id, Result result, String transferSyntax) {
            pcs.put(id, new PresentationContext(result, transferSyntax));
        }

        public PresentationContext getPresentationContext(Byte id) {
            return pcs.get(id);
        }

        public byte[] getUserIdentityServerResponse() {
            return clone(userIdentityServerResponse);
        }

        public void setUserIdentityServerResponse(byte[] userIdentityServerResponse) {
            this.userIdentityServerResponse = clone(userIdentityServerResponse);
        }

        @Override
        public String toString() {
            return toString("A-ASSOCIATE-AC[");
        }

        @Override
        protected void promptUserIdentityTo(StringBuilder sb) {
            if (userIdentityServerResponse != null) {
                sb.append("  UserIdentity[server-response: byte[")
                        .append(userIdentityServerResponse.length)
                        .append("]]");
            }
        }

        @Override
        protected void promptPresentationContextsTo(StringBuilder sb) {
            pcs.forEach((pcid, pc) -> pc.promptTo(pcid, sb));
        }

        @Override
        protected void parseItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
            switch (itemTypeLength >>> 24) {
                case 0x21:
                    pcs.put((byte) (buffer.getShort() >>> 8),
                            new PresentationContext(buffer, itemTypeLength & 0xffff, b64));
                    break;
                default:
                    super.parseItem(buffer, itemTypeLength, b64);
            }
        }

        @Override
        void parseUserItem(ByteBuffer buffer, int itemTypeLength, byte[] b64) {
            switch (itemTypeLength >>> 24) {
                case 0x59:
                    userIdentityServerResponse = ByteBufferUtils.getBytes(buffer, buffer.getShort());
                    break;
                default:
                    super.parseUserItem(buffer, itemTypeLength, b64);
            }
        }

        @Override
        int presentationContextLength() {
            return pcs.values().stream().mapToInt(pc -> 4 + pc.itemLength()).sum();
        }

        @Override
        int userItemLength() {
            return super.userItemLength()
                    + (userIdentityServerResponse != null ? 6 + userIdentityServerResponse.length : 0);
        }

        @Override
        void writeUserItemTo(ByteBuffer buffer, byte[] b64) {
            super.writeUserItemTo(buffer, b64);
            if (userIdentityServerResponse != null) {
                buffer.putShort((short) 0x5900);
                buffer.putShort((short) (2 + userIdentityServerResponse.length));
                buffer.putShort((short) userIdentityServerResponse.length);
                buffer.put(userIdentityServerResponse);
            }
        }

        @Override
        void writePresentationContextTo(ByteBuffer buffer, byte[] b64) {
            pcs.forEach((id, pc) -> writeTo(id, pc, buffer, b64));
        }

        private void writeTo(Byte id, PresentationContext pc, ByteBuffer buffer, byte[] b64) {
            buffer.putShort((short) 0x2100);
            buffer.putShort((short) pc.itemLength());
            buffer.putShort((short) (id.intValue() << 8));
            pc.writeTo(buffer, b64);
        }

        boolean isAcceptance(Byte pcid) {
            PresentationContext pc = pcs.get(pcid);
            return pc != null && pc.result == Result.ACCEPTANCE;
        }

        public static class PresentationContext {

            public final Result result;
            public final String transferSyntax;

            PresentationContext(Result result, String transferSyntax) {
                this.result = result;
                this.transferSyntax = transferSyntax;
            }

            PresentationContext(ByteBuffer buffer, int itemLength, byte[] b64) {
                int itemEnd = buffer.position() + itemLength - 2;
                result = Result.of((buffer.getShort() >>> 8) & 0xff);
                requireItemType(0x40, buffer.getShort() >>> 8);
                transferSyntax = ByteBufferUtils.getASCII(buffer, buffer.getShort(), b64);
            }

            int itemLength() {
                return 8 + transferSyntax.length();
            }

            void writeTo(ByteBuffer buffer, byte[] b64) {
                buffer.putShort((short) (result.code() << 8));
                buffer.putShort((short) 0x4000);
                ByteBufferUtils.putLengthASCII(buffer, transferSyntax, b64);
            }

            void promptTo(Byte pcid, StringBuilder sb) {
                sb.append("  PresentationContext[pcid: ")
                        .append(pcid)
                        .append(", result: ")
                        .append(result)
                        .append(System.lineSeparator())
                        .append("    transfer-syntax: ");
                UIDUtils.promptTo(transferSyntax, sb)
                        .append(System.lineSeparator())
                        .append("  ]")
                        .append(System.lineSeparator());
            }
        }

        enum Result {
            ACCEPTANCE("0 - acceptance"),
            USER_REJECTION("1 - user-rejection"),
            NO_REASON("2 - no-reason" ),
            ABSTRACT_SYNTAX_NOT_SUPPORTED("3 - abstract-syntax-not-supported"),
            TRANSFER_SYNTAXES_NOT_SUPPORTED("transfer-syntaxes-not-supported");

            final String prompt;

            Result(String prompt) {
                this.prompt = prompt;
            }

            public static Result of(int code) {
                try {
                    return values()[code];
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException(
                            String.format("Invalid Presentation Context Result Code: %2XH", code & 0xff));
                }
            }

            @Override
            public String toString() {
                return prompt;
            }

            public int code() {
                return ordinal();
            }
        }
    }

    static void requireItemType(int expected, int itemType) {
        if (itemType != expected) {
            throw new IllegalArgumentException(String.format("Item-type: %2XH - expected: %2XH", itemType, expected));
        }
    }

    public enum RoleSelection {
        NONE(false, false),
        SCU(true, false),
        SCP(false, true),
        BOTH(true, true);

        public final boolean scu;
        public final boolean scp;

        RoleSelection(boolean scu, boolean scp) {
            this.scu = scu;
            this.scp = scp;
        }

        static RoleSelection of(boolean scu, boolean scp) {
            return scu
                    ? (scp ? BOTH : SCU)
                    : (scp ? SCP : NONE);
        }

        static RoleSelection parse(ByteBuffer bb) {
            return bb.get() == 0
                    ? (bb.get() == 0 ? NONE : SCP)
                    : (bb.get() == 0 ? SCU : BOTH);
        }

        void writeTo(ByteBuffer bb) {
            bb.put(scu ? (byte) 1 : 0);
            bb.put(scp ? (byte) 1 : 0);
        }
    }

    public static class CommonExtendedNegotation {
        public final String serviceClass;
        private final List<String> relatedSOPClassList = new ArrayList<>();

        CommonExtendedNegotation(String serviceClass, String... relatedSOPClasses) {
            this.serviceClass = serviceClass;
            this.relatedSOPClassList.addAll(List.of(relatedSOPClasses));
        }

        CommonExtendedNegotation(ByteBuffer buffer, byte[] b64) {
            serviceClass = ByteBufferUtils.getASCII(buffer, buffer.getShort(), b64);
            int relatedSOPClassEnd = buffer.position() + buffer.getShort();
            while (buffer.position() < relatedSOPClassEnd) {
                relatedSOPClassList.add(ByteBufferUtils.getASCII(buffer, buffer.getShort(), b64));
            }
        }

        public String[] relatedSOPClasses() {
            return relatedSOPClassList.toArray(new String[0]);
        }

        public Stream<String> relatedSOPClassesStream() {
            return relatedSOPClassList.stream();
        }

        int length() {
            return 4 + serviceClass.length() + relatedSOPClassListLength();
        }

        private int relatedSOPClassListLength() {
            return relatedSOPClassList.stream().mapToInt(s -> 2 + s.length()).sum();
        }

        void writeTo(ByteBuffer buffer, byte[] b64) {
            ByteBufferUtils.putLengthASCII(buffer, serviceClass, b64);
            buffer.putShort((short) relatedSOPClassListLength());
            relatedSOPClassList.forEach(s -> ByteBufferUtils.putLengthASCII(buffer, s, b64));
        }

        public void promptTo(String cuid, StringBuilder sb) {
            sb.append("  CommonExtendedNegotation[")
                    .append(System.lineSeparator())
                    .append("    sop-class: ");
            UIDUtils.promptTo(cuid, sb)
                    .append(System.lineSeparator())
                    .append("    service-class: ");
            UIDUtils.promptTo(serviceClass, sb)
                    .append(System.lineSeparator());
            relatedSOPClassList.forEach(s ->
                    UIDUtils.promptTo(serviceClass, sb.append("    related-general-sop-class: "))
                            .append(System.lineSeparator()));
        }
    }

    public static class UserIdentity {
        public static final int USERNAME = 1;
        public static final int USERNAME_PASSCODE = 2;
        public static final int KERBEROS = 3;
        public static final int SAML = 4;
        public static final int JWT = 5;

        public final int type;
        public final boolean positiveResponseRequested;
        private final byte[] primaryField;
        private final byte[] secondaryField;

        UserIdentity(int type, boolean positiveResponseRequested, byte[] primaryField, byte[] secondaryField) {
            this.type = type;
            this.positiveResponseRequested = positiveResponseRequested;
            this.primaryField = primaryField.clone();
            this.secondaryField = secondaryField.clone();
        }

        UserIdentity(ByteBuffer buffer) {
            type = buffer.get();
            positiveResponseRequested = buffer.get() != 0;
            buffer.get(primaryField = new byte[buffer.getShort()]);
            buffer.get(secondaryField = new byte[buffer.getShort()]);
        }

        public byte[] primaryField() {
            return primaryField.clone();
        }

        public byte[] secondaryField() {
            return secondaryField.clone();
        }

        public boolean hasUsername() {
            return type == USERNAME || type == USERNAME_PASSCODE;
        }

        public String username() {
            if (!hasUsername()) {
                throw new IllegalStateException("type: " + type);
            }
            return new String(primaryField, StandardCharsets.UTF_8);
        }

        void writeTo(ByteBuffer buffer) {
            buffer.put((byte) type);
            buffer.put(positiveResponseRequested ? (byte) 1 : 0);
            buffer.putShort((short) primaryField.length);
            buffer.put(primaryField);
            buffer.putShort((short) secondaryField.length);
            buffer.put(secondaryField);
        }

        int itemLength() {
            return 6 + primaryField.length + secondaryField.length;
        }

        void promptTo(StringBuilder sb) {
            sb.append("  UserIdentity[")
                    .append(System.lineSeparator())
                    .append("    type: ")
                    .append(type)
                    .append(System.lineSeparator());
            if (hasUsername()) {
                sb.append("    username: ").append(username());
            } else {
                sb.append("    primaryField: byte[")
                        .append(primaryField.length)
                        .append(']');
            }
            if (secondaryField.length > 0) {
                sb.append(System.lineSeparator());
                if (type == UserIdentity.USERNAME_PASSCODE) {
                    sb.append("    passcode: ");
                    for (int i = secondaryField.length; --i >= 0; ) {
                        sb.append('*');
                    }
                } else {
                    sb.append("    secondaryField: byte[")
                            .append(secondaryField.length)
                            .append(']');
                }
            }
            sb.append(System.lineSeparator()).append("  ]");
        }
    }
}

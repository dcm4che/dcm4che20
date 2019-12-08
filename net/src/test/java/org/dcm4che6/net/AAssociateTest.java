package org.dcm4che6.net;

import org.dcm4che6.data.UID;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.dcm4che6.net.AAssociate.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
class AAssociateTest {

    private static final Byte PCID = (byte) 1;
    private static final byte[] EXT_NEG = {1, 1, 0, 0};
    private static final byte[] USER_IDENTITY_SERVER_RESPONSE = {0, 1, 2, 3};
    private static final byte[] USERNAME = "user".getBytes(StandardCharsets.UTF_8);

    @Test
    void aarq() {
        RQ aarq = new RQ();
        aarq.putPresentationContext(PCID, UID.StorageCommitmentPushModelSOPClass, UID.ImplicitVRLittleEndian);
        aarq.putCommonExtendedNegotation(
                UID.TwelveLeadECGWaveformStorage, UID.StorageServiceClass, UID.GeneralECGWaveformStorage);
        aarq.setUserIdentity(UserIdentity.USERNAME, true, USERNAME);
        RQ aarq2 = aaxx(aarq, buffer -> new RQ(buffer, buffer.remaining()));
        RQ.PresentationContext pc = aarq2.getPresentationContext(PCID);
        assertNotNull(pc);
        assertEquals(UID.StorageCommitmentPushModelSOPClass, pc.abstractSyntax());
        assertArrayEquals(new String[]{ UID.ImplicitVRLittleEndian }, pc.transferSyntax());
        CommonExtendedNegotation commonExtNeg = aarq2.getCommonExtendedNegotation(UID.TwelveLeadECGWaveformStorage);
        assertNotNull(commonExtNeg);
        assertEquals(UID.StorageServiceClass, commonExtNeg.serviceClass);
        assertArrayEquals(new String[]{ UID.GeneralECGWaveformStorage }, commonExtNeg.relatedSOPClasses());
        UserIdentity userIdentity = aarq2.getUserIdentity();
        assertEquals(UserIdentity.USERNAME, userIdentity.type);
        assertTrue(userIdentity.positiveResponseRequested);
        assertArrayEquals(USERNAME, userIdentity.primaryField());
    }

    @Test
    void aaac() {
        AC aaac = new AC();
        aaac.putPresentationContext(PCID, AC.Result.ACCEPTANCE, UID.ImplicitVRLittleEndian);
        aaac.setUserIdentityServerResponse(USER_IDENTITY_SERVER_RESPONSE);
        AC aaac2 = aaxx(aaac, buffer -> new AC(buffer, buffer.remaining()));
        AC.PresentationContext pc = aaac2.getPresentationContext(PCID);
        assertNotNull(pc);
        assertEquals(AC.Result.ACCEPTANCE, pc.result);
        assertEquals(UID.ImplicitVRLittleEndian, pc.transferSyntax);
        assertArrayEquals(USER_IDENTITY_SERVER_RESPONSE, aaac2.getUserIdentityServerResponse());
    }

    <T extends AAssociate> T aaxx(T aaxx, Function<ByteBuffer, T> parse) {
        aaxx.setCalledAETitle("STORESCP");
        aaxx.setCallingAETitle("STORESCU");
        aaxx.setAsyncOpsWindow(1,2);
        aaxx.putRoleSelection(UID.StorageCommitmentPushModelSOPClass, RoleSelection.SCP);
        aaxx.putExtendedNegotation(UID.StudyRootQueryRetrieveInformationModelFIND, EXT_NEG);
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        aaxx.writeTo(buffer);
        assertEquals(aaxx.pduLength(), buffer.position());
        buffer.flip();
        T aaxx2 = parse.apply(buffer);
        assertEquals("STORESCP", aaxx2.getCalledAETitle());
        assertEquals("STORESCU", aaxx2.getCallingAETitle());
        assertEquals(1, aaxx2.getMaxOpsInvoked());
        assertEquals(2, aaxx2.getMaxOpsPerformed());
        assertEquals(RoleSelection.SCP, aaxx2.getRoleSelection(UID.StorageCommitmentPushModelSOPClass));
        assertArrayEquals(EXT_NEG, aaxx2.getExtendedNegotation(UID.StudyRootQueryRetrieveInformationModelFIND));
        return aaxx2;
    }

}

package org.dcm4che6.conf.ldap;

import org.dcm4che6.conf.model.*;
import org.dcm4che6.data.UID;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
class LdapDicomConfigurationTest {

    static final Code code = new Code("VALUE^Meaning^99SCHEMA");
    static final Issuer issuerOfPatientID = new Issuer("ISSUER&1.2.3&ISO");

    @Test
    void persistDevice() throws Exception {
        try (LdapDicomConfiguration config = new LdapDicomConfiguration()) {
            config.purgeDicomConfiguration();
            config.persist(createDevice());
            Optional<Device> optionalDevice = config.loadDevice("dcm4chee-arc");
            assertTrue(optionalDevice.isPresent());
            assertDevice(optionalDevice.get());
        }
    }

    private Device createDevice() {
        Connection conn = new Connection()
                .setName("dicom-tls")
                .setHostname("hostname")
                .setPort(2762)
                .setTlsCipherSuites(Connection.TLS_RSA_WITH_AES_128_CBC_SHA);
        ApplicationEntity ae = new ApplicationEntity()
                .setAETitle("DCM4CHEE")
                .addConnection(conn)
                .addTransferCapability(new TransferCapability()
                        .setTransferSyntaxes(UID.ImplicitVRLittleEndian));
        KeyStoreConfiguration trustStore = new KeyStoreConfiguration()
                .setName("trustStore")
                .setPath("${jboss.server.config.dir}/keystores/cacerts.jks")
                .setKeyStoreType("JKS")
                .setPassword("secret");
        KeyStoreConfiguration keyStore = new KeyStoreConfiguration()
                .setName("keyStore")
                .setPath("${jboss.server.config.dir}/keystores/key.jks")
                .setKeyStoreType("JKS")
                .setPassword("secret");
        return new Device()
                .setDeviceName("dcm4chee-arc")
                .setPrimaryDeviceTypes("ARCHIVE")
                .setIssuerOfPatientID(issuerOfPatientID)
                .setInstitutionCodes(code)
                .setLimitOpenAssociations(100)
                .setTrustManagerConfiguration(new TrustManagerConfiguration().setKeyStoreConfiguration(trustStore))
                .setKeyManagerConfiguration(new KeyManagerConfiguration().setKeyStoreConfiguration(keyStore)
                        .setPassword("secret"))
                .addConnection(conn)
                .addApplicationEntity(ae)
                .addKeyStoreConfiguration(trustStore)
                .addKeyStoreConfiguration(keyStore);
    }

    private void assertDevice(Device device) {
        assertEquals(Optional.of(issuerOfPatientID), device.getIssuerOfPatientID());
        assertEquals(List.of("ARCHIVE"), device.getPrimaryDeviceTypes());
        assertEquals(List.of(code), device.getInstitutionCodes());
        assertEquals(OptionalInt.of(100), device.getLimitOpenAssociations());
        List<Connection> conns = device.getConnections();
        List<ApplicationEntity> aes = device.getApplicationEntities();
        assertEquals(1, conns.size());
        assertEquals(1, aes.size());
        ApplicationEntity ae1 = aes.get(0);
        List<Connection> ae1conns = ae1.getConnections();
        assertEquals(1, ae1conns.size());
        assertSame(conns.get(0), ae1conns.get(0));
    }

}
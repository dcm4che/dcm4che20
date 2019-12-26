package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.*;
import org.dcm4che6.data.UID;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;
import org.junit.jupiter.api.Test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
class DeviceSerializerTest {

    @Test
    void serializeDevice() {
        Issuer issuerOfPatientID = new Issuer("ISSUER&1.2.3&ISO");
        Code code = new Code("VALUE^Meaning^99SCHEMA");
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
        Device device = new Device()
                .setDeviceName("dcm4chee-arc")
                .setPrimaryDeviceTypes("ARCHIVE")
                .setIssuerOfPatientID(issuerOfPatientID)
                .setInstitutionCodes(code)
                .setLimitOpenAssociations(100)
                .setTrustManagerConfiguration(new TrustManagerConfiguration()
                        .setKeyStoreConfiguration(trustStore))
                .setKeyManagerConfiguration(new KeyManagerConfiguration()
                        .setKeyStoreConfiguration(keyStore)
                        .setPassword("secret"))
                .addConnection(conn)
                .addApplicationEntity(ae)
                .addKeyStoreConfiguration(trustStore)
                .addKeyStoreConfiguration(keyStore)
                .addDeviceExtension(new Extension().setValue("value"));
        JsonbConfig config = new JsonbConfig()
                .withFormatting(true)
                .withSerializers(new DeviceSerializer(), new ExtensionSerializer())
                .withDeserializers(new DeviceDeserializer(Extension.class), new ExtensionDeserializer());
        Jsonb jsonb = JsonbBuilder.create(config);
        String json = jsonb.toJson(device);
//        System.out.println(json);
        Device device1 = jsonb.fromJson(json, Device.class);
        assertEquals(Optional.of(issuerOfPatientID), device1.getIssuerOfPatientID());
        assertEquals(List.of("ARCHIVE"), device1.getPrimaryDeviceTypes());
        assertEquals(List.of(code), device1.getInstitutionCodes());
        assertSame(device1.getKeyStoreConfiguration("trustStore").get(),
                device1.getTrustManagerConfiguration().get().getKeyStoreConfiguration());
        assertSame(device1.getKeyStoreConfiguration("keyStore").get(),
                device1.getKeyManagerConfiguration().get().getKeyStoreConfiguration());
        assertEquals("value", device1.getDeviceExtension(Extension.class).value);
    }

}
package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.*;

import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

import java.util.List;

import static org.dcm4che6.conf.json.SerializerUtils.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class DeviceSerializer implements JsonbSerializer<Device> {

    @Override
    public void serialize(Device device, JsonGenerator gen, SerializationContext ctx) {
        gen.writeStartObject();
        gen.write("dicomDeviceName", device.getDeviceName());
        serializeValue("dicomDescription", device.getDescription(), gen);
        serializeValue("dicomDeviceUID", device.getDeviceUID(), gen);
        serializeValue("dicomManufacturer", device.getManufacturer(), gen);
        serializeValue("dicomManufacturerModelName", device.getManufacturerModelName(), gen);
        serializeArray("dicomSoftwareVersion", device.getSoftwareVersions(), gen);
        serializeValue("dicomStationName", device.getStationName(), gen);
        serializeValue("dicomDeviceSerialNumber", device.getDeviceSerialNumber(), gen);
        serializeValue("dicomIssuerOfPatientID", device.getIssuerOfPatientID(), gen);
        serializeValue("dicomIssuerOfAccessionNumber", device.getIssuerOfAccessionNumber(), gen);
        serializeValue("dicomOrderPlacerIdentifier", device.getOrderPlacerIdentifier(), gen);
        serializeValue("dicomOrderFillerIdentifier", device.getOrderFillerIdentifier(), gen);
        serializeValue("dicomIssuerOfAdmissionID", device.getIssuerOfAdmissionID(), gen);
        serializeValue("dicomIssuerOfServiceEpisodeID", device.getIssuerOfServiceEpisodeID(), gen);
        serializeValue("dicomIssuerOfContainerIdentifier", device.getIssuerOfContainerIdentifier(), gen);
        serializeValue("dicomIssuerOfSpecimenIdentifier", device.getIssuerOfSpecimenIdentifier(), gen);
        serializeArray("dicomInstitutionName", device.getInstitutionNames(), gen);
        serializeArray("dicomInstitutionCode", device.getInstitutionCodes(), gen);
        serializeArray("dicomInstitutionAddress", device.getInstitutionAddresses(), gen);
        serializeArray("dicomInstitutionDepartmentName", device.getInstitutionalDepartmentNames(), gen);
        serializeArray("dicomPrimaryDeviceType", device.getPrimaryDeviceTypes(), gen);
        serializeArray("dicomAuthorizedNodeCertificateReferences", device.getAuthorizedNodeCertificateReferences(), gen);
        serializeArray("dicomThisNodeCertificateReferences", device.getThisNodeCertificateReferences(), gen);
        gen.write("dicomInstalled", device.isInstalled());
        serializeConnections(device.getConnections(), gen);
        serializeApplicationEntities(device.getApplicationEntities(), gen);
        if (!device.isStrictDicom()) {
            gen.writeStartObject("dcmDevice");
            serializeInt("dcmLimitOpenAssociations", device.getLimitOpenAssociations(), gen);
            serializeKeyStoreConfs(device.getKeyStoreConfigurations(), gen);
            device.getKeyManagerConfiguration().ifPresent(km -> serializeKeyManagerConf(km, gen));
            device.getTrustManagerConfiguration().ifPresent(km -> serializeTrustManagerConf(km, gen));
            device.getDeviceExtensions().forEach(ext -> ctx.serialize(jsonPropertyOf(ext.getClass()), ext, gen));
            gen.writeEnd();
        }
        gen.writeEnd();
    }

    private static void serializeApplicationEntities(List<ApplicationEntity> aes, JsonGenerator gen) {
        if (!aes.isEmpty()) {
            gen.writeStartArray("dicomNetworkAE");
            aes.forEach(ae -> serializeApplicationEntity(ae, gen));
            gen.writeEnd();
        }
    }

    private static void serializeApplicationEntity(ApplicationEntity ae, JsonGenerator gen) {
        gen.writeStartObject();
        gen.write("dicomAETitle", ae.getAETitle());
        serializeValue("dicomDescription", ae.getDescription(), gen);
        serializeArray("dicomApplicationCluster", ae.getApplicationClusters(), gen);
        gen.write("dicomAssociationInitiator", ae.isAssociationInitiator());
        gen.write("dicomAssociationAcceptor", ae.isAssociationAcceptor());
        serializeArray("dicomSupportedCharacterSet", ae.getSupportedCharacterSets(), gen);
        serializeBoolean("dicomInstalled", ae.getInstalled(), gen);
        serializeConnectionRefs(ae.getConnections(), gen);
        serializeTransferCapabilities(ae.getTransferCapabilities(), gen);
        gen.writeEnd();
    }

    private static void serializeConnections(List<Connection> conns, JsonGenerator gen) {
        if (!conns.isEmpty()) {
            gen.writeStartArray("dicomNetworkConnection");
            conns.forEach(conn -> serializeConnection(conn, gen));
            gen.writeEnd();
        }
    }

    private static void serializeConnection(Connection conn, JsonGenerator gen) {
        gen.writeStartObject();
        serializeValue("cn", conn.getName(), gen);
        gen.write("dicomHostname", conn.getHostname());
        serializeInt("dicomPort", conn.getPort(), gen);
        serializeArray("dicomTLSCipherSuite", conn.getTlsCipherSuites(), gen);
        serializeBoolean("dicomInstalled", conn.getInstalled(), gen);
        gen.writeEnd();
    }

    private static void serializeConnectionRefs(List<Connection> conns, JsonGenerator gen) {
        if (!conns.isEmpty()) {
            gen.writeStartArray("dicomNetworkConnection");
            conns.forEach(conn -> serializeConnectionRef(conn, gen));
            gen.writeEnd();
        }
    }

    private static void serializeConnectionRef(Connection conn, JsonGenerator gen) {
        gen.writeStartObject();
        conn.getName().ifPresentOrElse(
                name -> gen.write("cn", name),
                () -> {
                    gen.write("dicomHostname", conn.getHostname());
                    serializeInt("dicomPort", conn.getPort(), gen);
                });
        gen.writeEnd();
    }

    private static void serializeTransferCapabilities(List<TransferCapability> tcs, JsonGenerator gen) {
        if (!tcs.isEmpty()) {
            gen.writeStartArray("dicomTransferCapability");
            tcs.forEach(tc -> serializeTransferCapability(tc, gen));
            gen.writeEnd();
        }
    }

    private static void serializeTransferCapability(TransferCapability tc, JsonGenerator gen) {
        gen.writeStartObject();
        serializeValue("cn", tc.getName(), gen);
        gen.write("dicomSOPClass", tc.getSOPClass());
        gen.write("dicomTransferRole", tc.getRole().toString());
        serializeArray("dicomTransferSyntax", tc.getTransferSyntaxes(), gen);
        gen.writeEnd();
    }

    private void serializeKeyStoreConfs(List<KeyStoreConfiguration> list, JsonGenerator gen) {
        if (!list.isEmpty()) {
            gen.writeStartArray("dcmKeyStore");
            list.forEach(keyStoreRef -> serializeKeyStoreConf(keyStoreRef, gen));
            gen.writeEnd();
        }
    }

    private void serializeKeyStoreConf(KeyStoreConfiguration ks, JsonGenerator gen) {
        gen.writeStartObject();
        gen.write("dcmKeyStoreName", ks.getName());
        serializeValue("dcmKeyStoreType", ks.getKeyStoreType(), gen);
        serializeValue("dcmProvider", ks.getProvider(), gen);
        serializeValue("dcmPath", ks.getPath(), gen);
        serializeValue("dcmURL", ks.getURL(), gen);
        gen.write("dcmPassword", ks.getPassword());
        gen.writeEnd();
    }

    private void serializeKeyManagerConf(KeyManagerConfiguration km, JsonGenerator gen) {
        gen.writeStartObject("dcmKeyManager");
        gen.write("dcmKeyStoreName", km.getKeyStoreConfiguration().getName());
        serializeValue("dcmAlgorithm", km.getAlgorithm(), gen);
        serializeValue("dcmProvider", km.getProvider(), gen);
        gen.write("dcmPassword", km.getPassword());
        gen.writeEnd();
    }

    private void serializeTrustManagerConf(TrustManagerConfiguration tm, JsonGenerator gen) {
        gen.writeStartObject("dcmTrustManager");
        gen.write("dcmKeyStoreName", tm.getKeyStoreConfiguration().getName());
        serializeValue("dcmAlgorithm", tm.getAlgorithm(), gen);
        serializeValue("dcmProvider", tm.getProvider(), gen);
        gen.writeEnd();
    }
}

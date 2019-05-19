package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;

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
        gen.write("dicomInstalled", device.isInstalled());
        serializeConnections(device.getConnections(), gen);
        serializeApplicationEntities(device.getApplicationEntities(), gen);
        if (!device.isStrictDicom()) {
            gen.writeStartObject("dcmDevice");
            serializeInt("dcmLimitOpenAssociations", device.getLimitOpenAssociations(), gen);
            serializeValue("dcmTrustStoreURL", device.getTrustStoreURL(), gen);
            serializeValue("dcmTrustStoreType", device.getTrustStoreType(), gen);
            serializeValue("dcmTrustStorePin", device.getTrustStorePin(), gen);
            serializeValue("dcmTrustStorePinProperty", device.getTrustStorePinProperty(), gen);
            serializeValue("dcmKeyStoreURL", device.getKeyStoreURL(), gen);
            serializeValue("dcmKeyStoreType", device.getKeyStoreType(), gen);
            serializeValue("dcmKeyStorePin", device.getKeyStorePin(), gen);
            serializeValue("dcmKeyStorePinProperty", device.getKeyStorePinProperty(), gen);
            serializeValue("dcmKeyStoreKeyPin", device.getKeyStoreKeyPin(), gen);
            serializeValue("dcmKeyStoreKeyPinProperty", device.getKeyStoreKeyPinProperty(), gen);
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
}

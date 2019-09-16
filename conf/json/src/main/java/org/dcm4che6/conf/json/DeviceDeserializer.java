package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.*;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.dcm4che6.conf.json.DeserializerUtils.*;
import static org.dcm4che6.conf.json.SerializerUtils.serializeValue;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public class DeviceDeserializer implements JsonbDeserializer<Device> {
    private final Map<String, Class<?>> key2class;

    public DeviceDeserializer(Class<?>... classes) {
        key2class = Stream.of(classes).collect(Collectors.toMap(SerializerUtils::jsonPropertyOf, Function.identity()));
    }

    @Override
    public Device deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        Device device = new Device();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dicomDeviceName":
                    device.setDeviceName(deserializeString(parser));
                    break;
                case "dicomDescription":
                    device.setDescription(deserializeString(parser));
                    break;
                case "dicomDeviceUID":
                    device.setDeviceUID(deserializeString(parser));
                    break;
                case "dicomManufacturer":
                    device.setManufacturer(deserializeString(parser));
                    break;
                case "dicomManufacturerModelName":
                    device.setManufacturerModelName(deserializeString(parser));
                    break;
                case "dicomSoftwareVersion":
                    device.setSoftwareVersions(deserializeStringArray(parser));
                    break;
                case "dicomStationName":
                    device.setStationName(deserializeString(parser));
                    break;
                case "dicomDeviceSerialNumber":
                    device.setDeviceSerialNumber(deserializeString(parser));
                    break;
                case "dicomIssuerOfPatientID":
                    device.setIssuerOfPatientID(deserializeIssuer(parser));
                    break;
                case "dicomIssuerOfAccessionNumber":
                    device.setIssuerOfAccessionNumber(deserializeIssuer(parser));
                    break;
                case "dicomOrderPlacerIdentifier":
                    device.setOrderPlacerIdentifier(deserializeIssuer(parser));
                    break;
                case "dicomOrderFillerIdentifier":
                    device.setOrderFillerIdentifier(deserializeIssuer(parser));
                    break;
                case "dicomIssuerOfAdmissionID":
                    device.setIssuerOfAdmissionID(deserializeIssuer(parser));
                    break;
                case "dicomIssuerOfServiceEpisodeID":
                    device.setIssuerOfServiceEpisodeID(deserializeIssuer(parser));
                    break;
                case "dicomIssuerOfContainerIdentifier":
                    device.setIssuerOfContainerIdentifier(deserializeIssuer(parser));
                    break;
                case "dicomIssuerOfSpecimenIdentifier":
                    device.setIssuerOfSpecimenIdentifier(deserializeIssuer(parser));
                    break;
                case "dicomInstitutionName":
                    device.setInstitutionNames(deserializeStringArray(parser));
                    break;
                case "dicomInstitutionCode":
                    device.setInstitutionCodes(deserializeCodeArray(parser));
                    break;
                case "dicomInstitutionAddress":
                    device.setInstitutionAddresses(deserializeStringArray(parser));
                    break;
                case "dicomInstitutionDepartmentName":
                    device.setInstitutionalDepartmentNames(deserializeStringArray(parser));
                    break;
                case "dicomPrimaryDeviceType":
                    device.setPrimaryDeviceTypes(deserializeStringArray(parser));
                    break;
                case "dicomAuthorizedNodeCertificateReferences":
                    device.setAuthorizedNodeCertificateReferences(deserializeStringArray(parser));
                    break;
                case "dicomThisNodeCertificateReferences":
                    device.setThisNodeCertificateReferences(deserializeStringArray(parser));
                    break;
                case "dicomInstalled":
                    device.setInstalled(deserializeBoolean(parser));
                    break;
                case "dicomNetworkConnection":
                    assertEvent(JsonParser.Event.START_ARRAY, parser.next());
                    while ((event = parser.next()) == JsonParser.Event.START_OBJECT) {
                        device.addConnection(deserializeConnection(parser));
                    }
                    assertEvent(JsonParser.Event.END_ARRAY, event);
                    break;
                case "dicomNetworkAE":
                    assertEvent(JsonParser.Event.START_ARRAY, parser.next());
                    while ((event = parser.next()) == JsonParser.Event.START_OBJECT) {
                        device.addApplicationEntity(deserializeApplicationEntity(parser));
                    }
                    assertEvent(JsonParser.Event.END_ARRAY, event);
                    break;
                case "dcmDevice":
                    assertEvent(JsonParser.Event.START_OBJECT, parser.next());
                    while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
                        switch (key = parser.getString()) {
                            case "dcmLimitOpenAssociations":
                                device.setLimitOpenAssociations(deserializeInt(parser));
                                break;
                            case "dcmKeyStore":
                                assertEvent(JsonParser.Event.START_ARRAY, parser.next());
                                while ((event = parser.next()) == JsonParser.Event.START_OBJECT) {
                                    device.addKeyStoreConfiguration(deserializeKeyStoreConf(parser));
                                }
                                assertEvent(JsonParser.Event.END_ARRAY, event);
                                break;
                            case "dcmKeyManager":
                                device.setKeyManagerConfiguration(deserializeKeyManagerConf(parser));
                                break;
                            case "dcmTrustManager":
                                device.setTrustManagerConfiguration(deserializeTrustManagerConf(parser));
                                break;
                            default:
                                device.addDeviceExtension(ctx.deserialize(key2class(key), parser));
                        }
                        device.getKeyManagerConfiguration().ifPresent(km ->
                                km.setKeyStoreConfiguration(replaceKeyStoreConf(km.getKeyStoreConfiguration(), device)));
                        device.getTrustManagerConfiguration().ifPresent(tm ->
                                tm.setKeyStoreConfiguration(replaceKeyStoreConf(tm.getKeyStoreConfiguration(), device)));
                    }
                    assertEvent(JsonParser.Event.END_OBJECT, event);
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        device.getApplicationEntities().forEach(ae -> adjustApplicationEntity(ae, device));
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return device;
    }

    private ApplicationEntity deserializeApplicationEntity(JsonParser parser) {
        ApplicationEntity ae = new ApplicationEntity();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dicomAETitle":
                    ae.setAETitle(deserializeString(parser));
                    break;
                case "dicomDescription":
                    ae.setDescription(deserializeString(parser));
                    break;
                case "dicomApplicationCluster":
                    ae.setApplicationClusters(deserializeStringArray(parser));
                    break;
                case "dicomAssociationInitiator":
                    ae.setAssociationInitiator(deserializeBoolean(parser));
                    break;
                case "dicomAssociationAcceptor":
                    ae.setAssociationAcceptor(deserializeBoolean(parser));
                    break;
                case "dicomSupportedCharacterSet":
                    ae.setSupportedCharacterSets(deserializeStringArray(parser));
                    break;
                case "dicomInstalled":
                    ae.setInstalled(deserializeBoolean(parser));
                    break;
                case "dicomNetworkConnection":
                    assertEvent(JsonParser.Event.START_ARRAY, parser.next());
                    while ((event = parser.next()) == JsonParser.Event.START_OBJECT) {
                        ae.addConnection(deserializeConnection(parser));
                    }
                    assertEvent(JsonParser.Event.END_ARRAY, event);
                    break;
                case "dicomTransferCapability":
                    assertEvent(JsonParser.Event.START_ARRAY, parser.next());
                    while ((event = parser.next()) == JsonParser.Event.START_OBJECT) {
                        ae.addTransferCapability(deserializeTransferCapability(parser));
                    }
                    assertEvent(JsonParser.Event.END_ARRAY, event);
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return ae;
    }

    private void adjustApplicationEntity(ApplicationEntity ae, Device device) {
        List<Connection> list = replaceConnections(ae.getConnections(), device);
        ae.clearConnections();
        list.forEach(ae::addConnection);
    }

    private static Connection deserializeConnection(JsonParser parser) {
        Connection conn = new Connection();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "cn":
                    conn.setName(deserializeString(parser));
                    break;
                case "dicomHostname":
                    conn.setHostname(deserializeString(parser));
                    break;
                case "dicomPort":
                    conn.setPort(deserializeInt(parser));
                    break;
                case "dicomTLSCipherSuite":
                    conn.setTlsCipherSuites(deserializeStringArray(parser));
                    break;
                case "dicomInstalled":
                    conn.setInstalled(deserializeBoolean(parser));
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return conn;
    }

    private TransferCapability deserializeTransferCapability(JsonParser parser) {
        TransferCapability tc = new TransferCapability();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "cn":
                    tc.setName(deserializeString(parser));
                    break;
                case "dicomSOPClass":
                    tc.setSOPClass(deserializeString(parser));
                    break;
                case "dicomTransferRole":
                    tc.setRole(deserializeValue(parser, TransferCapability.Role::valueOf));
                    break;
                case "dicomTransferSyntax":
                    tc.setTransferSyntaxes(deserializeStringArray(parser));
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return tc;
    }

    private KeyStoreConfiguration deserializeKeyStoreConf(JsonParser parser) {
        KeyStoreConfiguration ks = new KeyStoreConfiguration();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dcmKeyStoreName":
                    ks.setName(deserializeString(parser));
                    break;
                case "dcmKeyStoreType":
                    ks.setKeyStoreType(deserializeString(parser));
                    break;
                case "dcmProvider":
                    ks.setProvider(deserializeString(parser));
                    break;
                case "dcmPath":
                    ks.setPath(deserializeString(parser));
                    break;
                case "dcmURL":
                    ks.setURL(deserializeString(parser));
                    break;
                case "dcmPassword":
                    ks.setPassword(deserializeString(parser));
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return ks;
    }

    private KeyManagerConfiguration deserializeKeyManagerConf(JsonParser parser) {
        assertEvent(JsonParser.Event.START_OBJECT, parser.next());
        KeyManagerConfiguration km = new KeyManagerConfiguration();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dcmKeyStoreName":
                    km.setKeyStoreConfiguration(new KeyStoreConfiguration().setName(deserializeString(parser)));
                    break;
                case "dcmAlgorithm":
                    km.setAlgorithm(deserializeString(parser));
                    break;
                case "dcmProvider":
                    km.setProvider(deserializeString(parser));
                    break;
                case "dcmPassword":
                    km.setPassword(deserializeString(parser));
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return km;
    }

    private TrustManagerConfiguration deserializeTrustManagerConf(JsonParser parser) {
        assertEvent(JsonParser.Event.START_OBJECT, parser.next());
        TrustManagerConfiguration tm = new TrustManagerConfiguration();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dcmKeyStoreName":
                    tm.setKeyStoreConfiguration(new KeyStoreConfiguration().setName(deserializeString(parser)));
                    break;
                case "dcmAlgorithm":
                    tm.setAlgorithm(deserializeString(parser));
                    break;
                case "dcmProvider":
                    tm.setProvider(deserializeString(parser));
                    break;
                default:
                    unexpectedKey(key);
            }
        }
        assertEvent(JsonParser.Event.END_OBJECT, event);
        return tm;
    }

    private Class<?> key2class(String key) {
        Class<?> aClass = key2class.get(key);
        if (aClass == null)
            unexpectedKey(key);
        return aClass;
    }

}

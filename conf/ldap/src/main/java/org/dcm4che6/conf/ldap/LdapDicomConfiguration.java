package org.dcm4che6.conf.ldap;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.util.Code;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static org.dcm4che6.conf.ldap.LdapUtils.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
public class LdapDicomConfiguration implements Closeable {

    private static final Rdn DICOM_CONFIGURATION_RDN = rdn("cn", "DICOM Configuration");
    private static final Rdn DEVICES_RDN = rdn("cn", "Devices");
    private static final Rdn UNIQUE_AE_TITLE_REGISTRY_RDN = rdn("cn", "Unique AE Titles Registry");

    private final InitialLdapContext ctx;
    private final Rdn[] ctxRdns;
    private volatile Rdn[] configurationRootRdns;
    private volatile Rdn[] devicesRootRdns;
    private volatile Rdn[] uniqueAETitlesRegistryRootRdns;

    public LdapDicomConfiguration(Hashtable<?, ?> environment) throws NamingException {
        this.ctx = new InitialLdapContext(environment, null);
        this.ctxRdns = new LdapName(ctx.getNameInNamespace()).getRdns().toArray(new Rdn[0]);
        NamingEnumeration<SearchResult> searchConfigurationRoot = ctx.search(
                new CompositeName(),
                "(&(objectclass=dicomConfigurationRoot)(cn=DICOM Configuration))",
                singleResultNoAttributes(SearchControls.SUBTREE_SCOPE));
        try {
            if (searchConfigurationRoot.hasMore()) {
                LdapName configurationRootName = new LdapName(searchConfigurationRoot.next().getName());
                configurationRootRdns = configurationRootName.getRdns().toArray(new Rdn[0]);
                if (contains(ctx, configurationRootName, DEVICES_RDN))
                    devicesRootRdns = append(configurationRootRdns, DEVICES_RDN);
                if (contains(ctx, configurationRootName, UNIQUE_AE_TITLE_REGISTRY_RDN))
                    uniqueAETitlesRegistryRootRdns = append(configurationRootRdns, UNIQUE_AE_TITLE_REGISTRY_RDN);
            }
        } finally {
            searchConfigurationRoot.close();
        }
    }

    public LdapDicomConfiguration() throws IOException, NamingException {
        this(loadEnvironment());
    }

    public void reconnect() throws NamingException {
        ctx.reconnect(ctx.getConnectControls());
    }

    @Override
    public void close() throws IOException {
        try {
            ctx.close();
        } catch (NamingException e) {
            throw new IOException(e);
        }
    }

    public boolean exists() {
        return configurationRootRdns != null;
    }

    public LdapDicomConfiguration purgeDicomConfiguration() throws NamingException {
        if (configurationRootRdns != null) {
            destroySubcontextWithChilds(new LdapName(List.of(configurationRootRdns)));
            configurationRootRdns = null;
            devicesRootRdns = null;
            uniqueAETitlesRegistryRootRdns = null;
        }
        return this;
    }

    public void registerAETitle(String aet) throws NamingException {
        synchronized (ctx) {
            ctx.createSubcontext(
                    toName(append(uniqueAETitlesRegistryRootRdns(), new Rdn("dicomAETitle", aet))),
                    objectClass("dicomUniqueAETitle"))
                .close();
        }
    }

    public void unregisterAETitle(String aet) throws NamingException {
        Rdn[] rdns = uniqueAETitlesRegistryRootRdns;
        if (rdns != null)
            synchronized (ctx) {
                ctx.destroySubcontext(toName(append(rdns, new Rdn("dicomAETitle", aet))));
            }
    }

    public void persist(Device device) throws NamingException {
        synchronized (ctx) {
            Rdn[] deviceRdns = append(devicesRootRdns(), new Rdn("dicomDeviceName", device.getDeviceName()));
            Rdn[] fullDeviceRdns = cat(ctxRdns, deviceRdns);
            ctx.createSubcontext(toName(deviceRdns), toAttrSet(device)).close();
            for (Connection conn : device.getConnections()) {
                ctx.createSubcontext(toName(append(deviceRdns, rdnOf(conn))), toAttrSet(conn)).close();
            }
            for (ApplicationEntity ae : device.getApplicationEntities()) {
                Rdn[] aeRdns = append(deviceRdns, new Rdn("dicomAETitle", ae.getAETitle()));
                ctx.createSubcontext(toName(aeRdns), toAttrSet(ae, fullDeviceRdns)).close();
                for (TransferCapability tc : ae.getTransferCapabilities()) {
                    ctx.createSubcontext(toName(append(aeRdns, rdnOf(tc))), toAttrSet(tc)).close();
                }
            }
        }
    }

    public Optional<Device> loadDevice(String deviceName) throws NamingException {
        if (devicesRootRdns == null)
            return Optional.empty();

        Rdn rdn = new Rdn("dicomDeviceName", deviceName);
        Attributes attrSet = search(ctx, toName(devicesRootRdns), rdn);
        return attrSet != null ? Optional.of(toDevice(attrSet, append(devicesRootRdns, rdn))) : Optional.empty();
    }

    private Device toDevice(Attributes attrSet, Rdn[] deviceRdns) throws NamingException {
        Device device = new Device();
        device.setDeviceName(getValue(attrSet, "dicomDeviceName", Object::toString));
        device.setDescription(getValue(attrSet, "dicomDescription", Object::toString));
        device.setDeviceUID(getValue(attrSet, "dicomDeviceUID", Object::toString));
        device.setManufacturer(getValue(attrSet, "dicomManufacturer", Object::toString));
        device.setManufacturerModelName(getValue(attrSet, "dicomManufacturerModelName", Object::toString));
        device.setSoftwareVersions(getArray(attrSet, "dicomSoftwareVersion", Object::toString, String.class));
        device.setStationName(getValue(attrSet, "dicomStationName", Object::toString));
        device.setDeviceSerialNumber(getValue(attrSet, "dicomDeviceSerialNumber", Object::toString));
        device.setIssuerOfPatientID(getValue(attrSet, "dicomIssuerOfPatientID", issuerMapping));
        device.setIssuerOfAccessionNumber(getValue(attrSet, "dicomIssuerOfAccessionNumber", issuerMapping));
        device.setOrderPlacerIdentifier(getValue(attrSet, "dicomOrderPlacerIdentifier", issuerMapping));
        device.setOrderFillerIdentifier(getValue(attrSet, "dicomOrderFillerIdentifier", issuerMapping));
        device.setIssuerOfAdmissionID(getValue(attrSet, "dicomIssuerOfAdmissionID", issuerMapping));
        device.setIssuerOfServiceEpisodeID(getValue(attrSet, "dicomIssuerOfServiceEpisodeID", issuerMapping));
        device.setIssuerOfContainerIdentifier(getValue(attrSet, "dicomIssuerOfContainerIdentifier", issuerMapping));
        device.setIssuerOfSpecimenIdentifier(getValue(attrSet, "dicomIssuerOfSpecimenIdentifier", issuerMapping));
        device.setInstitutionNames(getArray(attrSet, "dicomInstitutionName", Object::toString, String.class));
        device.setInstitutionCodes(getArray(attrSet, "dicomInstitutionCode", codeMapping, Code.class));
        device.setInstitutionAddresses(getArray(attrSet, "dicomInstitutionAddress", Object::toString, String.class));
        device.setInstitutionalDepartmentNames(getArray(attrSet, "dicomInstitutionDepartmentName", Object::toString, String.class));
        device.setPrimaryDeviceTypes(getArray(attrSet, "dicomPrimaryDeviceType", Object::toString, String.class));
        device.setRelatedDeviceReferences(getArray(attrSet, "dicomRelatedDeviceReference", Object::toString, String.class));
        device.setAuthorizedNodeCertificateReferences(getArray(attrSet, "dicomAuthorizedNodeCertificateReference", Object::toString, String.class));
        device.setThisNodeCertificateReferences(getArray(attrSet, "dicomThisNodeCertificateReference", Object::toString, String.class));
        device.setInstalled(getValue(attrSet, "dicomInstalled", booleanMapping));

        Rdn[] fullDeviceRdns = cat(ctxRdns, deviceRdns);
        Map<String, Connection> connectionMap = new HashMap<>();
        Name name = toName(deviceRdns);
        forEach(ctx, name, "dicomNetworkConnection", attrSet2 ->
                device.addConnection(addTo(connectionMap, toConnection(attrSet2), fullDeviceRdns)));
        forEach(ctx, name, "dicomNetworkAE",
                attrSet2 -> device.addApplicationEntity(toApplicationEntity(attrSet2, connectionMap, deviceRdns)));

        if (Stream.of(objectClassesOf(attrSet)).anyMatch("dcmDevice"::equals)) {
            getInt(attrSet, "dcmLimitOpenAssociations", device::setLimitOpenAssociations);
        }

        return device;
    }

    private static Connection addTo(Map<String, Connection> map, Connection conn, Rdn[] fullDeviceRdns) {
        map.put(dnOf(conn, fullDeviceRdns), conn);
        return conn;
    }

    private Connection toConnection(Attributes attrSet) throws NamingException {
        Connection conn = new Connection();
        conn.setName(getValue(attrSet, "cn", Object::toString));
        conn.setHostname(getValue(attrSet, "dicomHostname", Object::toString));
        getInt(attrSet, "dicomPort", conn::setPort);
        conn.setTlsCipherSuites(getArray(attrSet, "dicomTLSCipherSuite", Object::toString, String.class));
        conn.setInstalled(getValue(attrSet, "dicomInstalled", booleanMapping));
        return conn;
    }

    private ApplicationEntity toApplicationEntity(Attributes attrSet, Map<String, Connection> connectionMap,
            Rdn[] deviceRdns) throws NamingException {
        ApplicationEntity ae = new ApplicationEntity();
        ae.setAETitle(getValue(attrSet, "dicomAETitle", Object::toString));
        ae.setDescription(getValue(attrSet, "dicomDescription", Object::toString));
        ae.setApplicationClusters(getArray(attrSet, "dicomApplicationCluster", Object::toString, String.class));
        ae.setAssociationInitiator(getValue(attrSet, "dicomAssociationInitiator", booleanMapping));
        ae.setAssociationAcceptor(getValue(attrSet, "dicomAssociationAcceptor", booleanMapping));
        ae.setSupportedCharacterSets(getArray(attrSet, "dicomSupportedCharacterSet", Object::toString, String.class));
        for (String dn : getArray(attrSet, "dicomNetworkConnectionReference", Object::toString, String.class)) {
            ae.addConnection(connectionMap.get(dn));
        }
        ae.setInstalled(getValue(attrSet, "dicomInstalled", booleanMapping));
        Name name = toName(append(deviceRdns, new Rdn("dicomAETitle", ae.getAETitle())));
        forEach(ctx, name, "dicomConnection",
                attrSet2 -> ae.addTransferCapability(toTransferCapability(attrSet2)));
        return ae;
    }

    private TransferCapability toTransferCapability(Attributes attrSet) throws NamingException {
        TransferCapability tc = new TransferCapability();
        tc.setName(getValue(attrSet, "cn", Object::toString));
        tc.setSOPClass(getValue(attrSet, "dicomSOPClass", Object::toString));
        tc.setRole(getValue(attrSet, "dicomTransferRole", roleMapping));
        tc.setTransferSyntaxes(getArray(attrSet, "dicomTransferSyntax", Object::toString, String.class));
        return tc;
    }

    private Attributes toAttrSet(Device device) {
        Attributes attrSet = objectClass("dicomDevice");
        putValue(attrSet, "dicomDescription", device.getDescription());
        putValue(attrSet, "dicomDeviceUID", device.getDeviceUID());
        putValue(attrSet, "dicomManufacturer", device.getManufacturer());
        putValue(attrSet, "dicomManufacturerModelName", device.getManufacturerModelName());
        putList(attrSet, "dicomSoftwareVersion", device.getSoftwareVersions());
        putValue(attrSet, "dicomStationName", device.getStationName());
        putValue(attrSet, "dicomDeviceSerialNumber", device.getDeviceSerialNumber());
        putValue(attrSet, "dicomIssuerOfPatientID", device.getIssuerOfPatientID());
        putValue(attrSet, "dicomIssuerOfAccessionNumber", device.getIssuerOfAccessionNumber());
        putValue(attrSet, "dicomOrderPlacerIdentifier", device.getOrderPlacerIdentifier());
        putValue(attrSet, "dicomOrderFillerIdentifier", device.getOrderFillerIdentifier());
        putValue(attrSet, "dicomIssuerOfAdmissionID", device.getIssuerOfAdmissionID());
        putValue(attrSet, "dicomIssuerOfServiceEpisodeID", device.getIssuerOfServiceEpisodeID());
        putValue(attrSet, "dicomIssuerOfContainerIdentifier", device.getIssuerOfContainerIdentifier());
        putValue(attrSet, "dicomIssuerOfSpecimenIdentifier", device.getIssuerOfSpecimenIdentifier());
        putList(attrSet, "dicomInstitutionName", device.getInstitutionNames());
        putList(attrSet, "dicomInstitutionCode", device.getInstitutionCodes());
        putList(attrSet, "dicomInstitutionAddress", device.getInstitutionAddresses());
        putList(attrSet, "dicomInstitutionDepartmentName", device.getInstitutionalDepartmentNames());
        putList(attrSet, "dicomPrimaryDeviceType", device.getPrimaryDeviceTypes());
        putList(attrSet, "dicomRelatedDeviceReference", device.getRelatedDeviceReferences());
        putList(attrSet, "dicomAuthorizedNodeCertificateReference", device.getAuthorizedNodeCertificateReferences());
        putList(attrSet, "dicomThisNodeCertificateReference", device.getThisNodeCertificateReferences());
        putBoolean(attrSet, "dicomInstalled", device.isInstalled());

        if (!device.isStrictDicom()) {
            addObjectClass(attrSet, "dcmDevice");
            putInt(attrSet, "dcmLimitOpenAssociations", device.getLimitOpenAssociations());
        }
        return attrSet;
    }

    private Attributes toAttrSet(Connection conn) {
        Attributes attrSet = objectClass("dicomNetworkConnection");
        putValue(attrSet, "dicomHostname", conn.getHostname());
        putInt(attrSet, "dicomPort", conn.getPort());
        putList(attrSet, "dicomTLSCipherSuite", conn.getTlsCipherSuites());
        putBoolean(attrSet, "dicomInstalled", conn.getInstalled());
        return attrSet;
    }

    private Attributes toAttrSet(ApplicationEntity ae, Rdn[] fullDeviceRdns) {
        Attributes attrSet = objectClass("dicomNetworkAE");
        putValue(attrSet, "dicomDescription", ae.getDescription());
        putList(attrSet, "dicomApplicationCluster", ae.getApplicationClusters());
        putBoolean(attrSet, "dicomAssociationInitiator", ae.isAssociationInitiator());
        putBoolean(attrSet, "dicomAssociationAcceptor", ae.isAssociationAcceptor());
        putList(attrSet, "dicomSupportedCharacterSet", ae.getSupportedCharacterSets());
        putList(attrSet, "dicomNetworkConnectionReference", ae.getConnections(),
                conn -> dnOf(conn, fullDeviceRdns));
        putBoolean(attrSet, "dicomInstalled", ae.getInstalled());
        return attrSet;
    }

    private Attributes toAttrSet(TransferCapability tc) {
        Attributes attrSet = objectClass("dicomTransferCapability");
        putValue(attrSet,"dicomSOPClass", tc.getSOPClass());
        putValue(attrSet,"dicomTransferRole", tc.getRole());
        putList(attrSet,"dicomTransferSyntax", tc.getTransferSyntaxes());
        return attrSet;
    }

    private Rdn[] configurationRootRdns() throws NamingException {
        Rdn[] rdns = configurationRootRdns;
        if (rdns == null) {
            rdns = new Rdn[]{DICOM_CONFIGURATION_RDN};
            synchronized (ctx) {
                ctx.createSubcontext(toName(rdns), objectClass("dicomConfigurationRoot")).close();
            }
            configurationRootRdns = rdns;
        }
        return rdns;
    }

    private Rdn[] devicesRootRdns() throws NamingException {
        Rdn[] rdns = devicesRootRdns;
        if (rdns == null) {
            synchronized (ctx) {
                ctx.createSubcontext(
                        toName(rdns = append(configurationRootRdns(), DEVICES_RDN)),
                        objectClass("dicomDevicesRoot"))
                        .close();
            }
            devicesRootRdns = rdns;
        }
        return rdns;
    }

    private Rdn[] uniqueAETitlesRegistryRootRdns() throws NamingException {
        Rdn[] rdns = uniqueAETitlesRegistryRootRdns;
        if (rdns == null) {
            synchronized (ctx) {
                ctx.createSubcontext(
                        toName(rdns = append(configurationRootRdns(), UNIQUE_AE_TITLE_REGISTRY_RDN)),
                        objectClass("dicomConfigurationRoot"))
                        .close();
            }
            uniqueAETitlesRegistryRootRdns = rdns;
        }
        return rdns;
    }

    private void destroySubcontextWithChilds(Name name) throws NamingException {
        synchronized (ctx) {
            NamingEnumeration<NameClassPair> list = ctx.list(name);
            try {
                while (list.hasMore())
                    destroySubcontextWithChilds(((Name) name.clone()).add(list.next().getName()));
            } finally {
                list.close();
            }
            ctx.destroySubcontext(name);
        }
    }

    private static Hashtable<?, ?> loadEnvironment() throws IOException {
        Properties environment = new Properties();
        try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ldap.properties")) {
            environment.load(resourceAsStream);
        }
        return (environment);
    }
}

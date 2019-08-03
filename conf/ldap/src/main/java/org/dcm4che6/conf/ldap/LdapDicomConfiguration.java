package org.dcm4che6.conf.ldap;

import org.dcm4che6.conf.model.*;
import org.dcm4che6.util.Code;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.dcm4che6.conf.ldap.LdapUtils.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
public class LdapDicomConfiguration implements Closeable {

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
            }
        } finally {
            searchConfigurationRoot.close();
        }
        devicesRootRdns = existsOrNull(cat(configurationRootRdns, rdnOfDevicesRoot()));
        uniqueAETitlesRegistryRootRdns = existsOrNull(cat(configurationRootRdns, rdnOfUniqueAETitelsRegistry()));
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
            destroySubcontextWithChilds(configurationRootRdns);
            configurationRootRdns = null;
            devicesRootRdns = null;
            uniqueAETitlesRegistryRootRdns = null;
        }
        return this;
    }

    public void registerAETitle(String aet) throws NamingException {
        createSubcontext(
                LdapUtils.cat(uniqueAETitlesRegistryRootRdns(), rdnOfAE(aet)),
                objectClass("dicomUniqueAETitle"));
    }

    public void unregisterAETitle(String aet) throws NamingException {
        Rdn[] rdns = uniqueAETitlesRegistryRootRdns;
        if (rdns != null)
            destroySubcontext(LdapUtils.cat(rdns, rdnOfAE(aet)));
    }

    public void persist(Device device) throws NamingException {
        Rdn[] deviceRdns = LdapUtils.cat(devicesRootRdns(), rdnOfDevice(device.getDeviceName()));
        createSubcontext(deviceRdns, toAttrSet(device));
        for (Connection conn : device.getConnections()) {
            persist(conn, deviceRdns);
        }
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            persist(ae, deviceRdns);
        }
        for (KeyStoreConfiguration ks : device.getKeyStoreConfigurations()) {
            persist(ks, deviceRdns);
        }
        if (device.getKeyManagerConfiguration().isPresent()) {
            persist(device.getKeyManagerConfiguration().get(), deviceRdns);
        }
        if (device.getTrustManagerConfiguration().isPresent()) {
            persist(device.getTrustManagerConfiguration().get(), deviceRdns);
        }
    }

    public void createSubcontext(Rdn[] rdns, Attributes attrSet) throws NamingException {
        ctx.createSubcontext(toName(rdns), attrSet).close();
    }

    public void destroySubcontext(Rdn[] rdns) throws NamingException {
        ctx.destroySubcontext(toName(rdns));
    }

    public void destroySubcontextWithChilds(Rdn[] rdns) throws NamingException {
        Name name = toName(rdns);
        NamingEnumeration<NameClassPair> list = ctx.list(name);
        try {
            while (list.hasMore())
                destroySubcontextWithChilds(LdapUtils.cat(rdns, new Rdn(list.next().getName())));
        } finally {
            list.close();
        }
        ctx.destroySubcontext(name);
    }

    public void modifyAttributes(Rdn[] rdns, List<ModificationItem> mods) throws NamingException {
        if (!mods.isEmpty()) {
            ctx.modifyAttributes(toName(rdns), mods.toArray(new ModificationItem[0]));
        }
    }

    public Optional<Device> findDevice(String deviceName) throws NamingException {
        if (devicesRootRdns == null)
            return Optional.empty();

        Rdn rdn = rdnOfDevice(deviceName);
        Attributes attrSet;
        try {
            attrSet = ctx.getAttributes(toName(cat(devicesRootRdns, rdn)));
        } catch (NameNotFoundException e) {
            return Optional.empty();
        }
        return Optional.of(toDevice(attrSet, LdapUtils.cat(devicesRootRdns, rdn)));
    }

    public void merge(Device device) throws NamingException {
        Device prevDevice = findDevice(device.getDeviceName()).get();
        Rdn[] deviceRdns = LdapUtils.cat(devicesRootRdns, rdnOfDevice(device.getDeviceName()));
        modifyAttributes(deviceRdns, diff(prevDevice, device));
        mergeConns(prevDevice, device, deviceRdns);
        mergeAEs(prevDevice, device, deviceRdns);
        mergeKeyStoreConfigs(prevDevice, device, deviceRdns);
        mergeKeyManagerConfig(prevDevice, device, deviceRdns);
        mergeTrustManagerConfig(prevDevice, device, deviceRdns);
    }

    private Rdn[] configurationRootRdns() throws NamingException {
        Rdn[] rdns = configurationRootRdns;
        if (rdns == null) {
            rdns = new Rdn[]{rdn("cn", "DICOM Configuration")};
            createSubcontext(rdns, objectClass("dicomConfigurationRoot"));
            configurationRootRdns = rdns;
        }
        return rdns;
    }

    private Rdn[] devicesRootRdns() throws NamingException {
        Rdn[] rdns = devicesRootRdns;
        if (rdns == null) {
            createSubcontext(
                    rdns = cat(configurationRootRdns(), rdnOfDevicesRoot()),
                    objectClass("dicomDevicesRoot"));
            devicesRootRdns = rdns;
        }
        return rdns;
    }

    private Rdn[] uniqueAETitlesRegistryRootRdns() throws NamingException {
        Rdn[] rdns = uniqueAETitlesRegistryRootRdns;
        if (rdns == null) {
            createSubcontext(
                    rdns = cat(configurationRootRdns(), rdnOfUniqueAETitelsRegistry()),
                    objectClass("dicomConfigurationRoot"));
            uniqueAETitlesRegistryRootRdns = rdns;
        }
        return rdns;
    }

    private static Hashtable<?, ?> loadEnvironment() throws IOException {
        Properties environment = new Properties();
        try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ldap.properties")) {
            environment.load(resourceAsStream);
        }
        return (environment);
    }

    private static Rdn rdnOfUniqueAETitelsRegistry() {
        return rdn("cn", "Unique AE Titles Registry");
    }

    private static Rdn rdnOfDevicesRoot() {
        return rdn("cn", "Devices");
    }

    private Rdn[] existsOrNull(Rdn[] rdns) throws NamingException {
        try {
            ctx.getAttributes(toName(rdns), new String[0]);
            return rdns;
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    private static Rdn rdnOfDevice(String deviceName) {
        return rdn("dicomDeviceName", deviceName);
    }

    private Attributes toAttrSet(Device device) {
        Attributes attrSet = objectClass("dicomDevice");
        putValue(attrSet, "dicomDescription", device.getDescription());
        putValue(attrSet, "dicomDeviceUID", device.getDeviceUID());
        putValue(attrSet, "dicomManufacturer", device.getManufacturer());
        putValue(attrSet, "dicomManufacturerModelName", device.getManufacturerModelName());
        putValues(attrSet, "dicomSoftwareVersion", device.getSoftwareVersions());
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
        putValues(attrSet, "dicomInstitutionName", device.getInstitutionNames());
        putValues(attrSet, "dicomInstitutionCode", device.getInstitutionCodes());
        putValues(attrSet, "dicomInstitutionAddress", device.getInstitutionAddresses());
        putValues(attrSet, "dicomInstitutionDepartmentName", device.getInstitutionalDepartmentNames());
        putValues(attrSet, "dicomPrimaryDeviceType", device.getPrimaryDeviceTypes());
        putValues(attrSet, "dicomRelatedDeviceReference", device.getRelatedDeviceReferences());
        putValues(attrSet, "dicomAuthorizedNodeCertificateReference", device.getAuthorizedNodeCertificateReferences());
        putValues(attrSet, "dicomThisNodeCertificateReference", device.getThisNodeCertificateReferences());
        putBoolean(attrSet, "dicomInstalled", device.isInstalled());

        if (!device.isStrictDicom()) {
            addObjectClass(attrSet, "dcmDevice");
            putInt(attrSet, "dcmLimitOpenAssociations", device.getLimitOpenAssociations());
        }
        return attrSet;
    }

    private Device toDevice(Attributes attrSet, Rdn[] deviceRdns) throws NamingException {
        Device device = new Device();
        ifPresent(attrSet, "dicomDeviceName", Object::toString,
                device::setDeviceName);
        ifPresent(attrSet, "dicomDescription", Object::toString,
                device::setDescription);
        ifPresent(attrSet, "dicomDeviceUID", Object::toString,
                device::setDeviceUID);
        ifPresent(attrSet, "dicomManufacturer", Object::toString,
                device::setManufacturer);
        ifPresent(attrSet, "dicomManufacturerModelName", Object::toString,
                device::setManufacturerModelName);
        ifPresent(attrSet, "dicomSoftwareVersion", Object::toString, String.class,
                device::setSoftwareVersions);
        ifPresent(attrSet, "dicomStationName", Object::toString,
                device::setStationName);
        ifPresent(attrSet, "dicomDeviceSerialNumber", Object::toString,
                device::setDeviceSerialNumber);
        ifPresent(attrSet, "dicomIssuerOfPatientID", issuerMapping,
                device::setIssuerOfPatientID);
        ifPresent(attrSet, "dicomIssuerOfAccessionNumber", issuerMapping,
                device::setIssuerOfAccessionNumber);
        ifPresent(attrSet, "dicomOrderPlacerIdentifier", issuerMapping,
                device::setOrderPlacerIdentifier);
        ifPresent(attrSet, "dicomOrderFillerIdentifier", issuerMapping,
                device::setOrderFillerIdentifier);
        ifPresent(attrSet, "dicomIssuerOfAdmissionID", issuerMapping,
                device::setIssuerOfAdmissionID);
        ifPresent(attrSet, "dicomIssuerOfServiceEpisodeID", issuerMapping,
                device::setIssuerOfServiceEpisodeID);
        ifPresent(attrSet, "dicomIssuerOfContainerIdentifier", issuerMapping,
                device::setIssuerOfContainerIdentifier);
        ifPresent(attrSet, "dicomIssuerOfSpecimenIdentifier", issuerMapping,
                device::setIssuerOfSpecimenIdentifier);
        ifPresent(attrSet, "dicomInstitutionName", Object::toString, String.class,
                device::setInstitutionNames);
        ifPresent(attrSet, "dicomInstitutionCode", codeMapping, Code.class,
                device::setInstitutionCodes);
        ifPresent(attrSet, "dicomInstitutionAddress", Object::toString, String.class,
                device::setInstitutionAddresses);
        ifPresent(attrSet, "dicomInstitutionDepartmentName", Object::toString, String.class,
                device::setInstitutionalDepartmentNames);
        ifPresent(attrSet, "dicomPrimaryDeviceType", Object::toString, String.class,
                device::setPrimaryDeviceTypes);
        ifPresent(attrSet, "dicomRelatedDeviceReference", Object::toString, String.class,
                device::setRelatedDeviceReferences);
        ifPresent(attrSet, "dicomAuthorizedNodeCertificateReference", Object::toString, String.class,
                device::setAuthorizedNodeCertificateReferences);
        ifPresent(attrSet, "dicomThisNodeCertificateReference", Object::toString, String.class,
                device::setThisNodeCertificateReferences);
        ifPresent(attrSet, "dicomInstalled", booleanMapping,
                device::setInstalled);

        Map<String, Connection> dnConnectionMap = new HashMap<>();
        Name name = toName(deviceRdns);
        forEach(ctx, name, "dicomNetworkConnection",
                searchResult -> device.addConnection(
                        toConnection(searchResult, dnConnectionMap)));
        forEach(ctx, name, "dicomNetworkAE",
                searchResult -> device.addApplicationEntity(
                        toApplicationEntity(searchResult.getAttributes(), dnConnectionMap, deviceRdns)));

        if (hasObjectClass(attrSet, "dcmDevice")) {
            LdapUtils.ifPresent(attrSet, "dcmLimitOpenAssociations", device::setLimitOpenAssociations);
            forEach(ctx, name, "dcmKeyStore",
                    searchResult -> device.addKeyStoreConfiguration(
                            toKeyStoreConfiguration(searchResult.getAttributes())));
            ifPresent(ctx, toName(cat(deviceRdns, rdnOfKeyManager())),
                    kmAttrs -> toKeyManagerConfiguration(kmAttrs, device), device::setKeyManagerConfiguration);
            ifPresent(ctx, toName(cat(deviceRdns, rdnOfTrustManager())),
                    tmAttrs -> toTrustManagerConfiguration(tmAttrs, device), device::setTrustManagerConfiguration);
        }
        return device;
    }

    private List<ModificationItem> diff(Device a, Device b) {
        List<ModificationItem> mods = new ArrayList<>();
        diffOptionalValue(mods, "dicomDescription",
                a.getDescription(), b.getDescription());
        diffOptionalValue(mods, "dicomDeviceUID",
                a.getDeviceUID(), b.getDeviceUID());
        diffOptionalValue(mods, "dicomManufacturer",
                a.getManufacturer(), b.getManufacturer());
        diffOptionalValue(mods, "dicomManufacturerModelName",
                a.getManufacturerModelName(), b.getManufacturerModelName());
        diffValues(mods, "dicomSoftwareVersion",
                a.getSoftwareVersions(), b.getSoftwareVersions());
        diffOptionalValue(mods, "dicomStationName",
                a.getStationName(), b.getStationName());
        diffOptionalValue(mods, "dicomDeviceSerialNumber",
                a.getDeviceSerialNumber(), b.getDeviceSerialNumber());
        diffOptionalValue(mods, "dicomIssuerOfPatientID",
                a.getIssuerOfPatientID(), b.getIssuerOfPatientID());
        diffOptionalValue(mods, "dicomIssuerOfAccessionNumber",
                a.getIssuerOfAccessionNumber(), b.getIssuerOfAccessionNumber());
        diffOptionalValue(mods, "dicomOrderPlacerIdentifier",
                a.getOrderPlacerIdentifier(), b.getOrderPlacerIdentifier());
        diffOptionalValue(mods, "dicomOrderFillerIdentifier",
                a.getOrderFillerIdentifier(), b.getOrderFillerIdentifier());
        diffOptionalValue(mods, "dicomIssuerOfAdmissionID",
                a.getIssuerOfAdmissionID(), b.getIssuerOfAdmissionID());
        diffOptionalValue(mods, "dicomIssuerOfServiceEpisodeID",
                a.getIssuerOfServiceEpisodeID(), b.getIssuerOfServiceEpisodeID());
        diffOptionalValue(mods, "dicomIssuerOfContainerIdentifier",
                a.getIssuerOfContainerIdentifier(), b.getIssuerOfContainerIdentifier());
        diffOptionalValue(mods, "dicomIssuerOfSpecimenIdentifier",
                a.getIssuerOfSpecimenIdentifier(), b.getIssuerOfSpecimenIdentifier());
        diffValues(mods, "dicomInstitutionName",
                a.getInstitutionNames(), b.getInstitutionNames());
        diffValues(mods, "dicomInstitutionCode",
                a.getInstitutionCodes(), b.getInstitutionCodes());
        diffValues(mods, "dicomInstitutionAddress",
                a.getInstitutionAddresses(), b.getInstitutionAddresses());
        diffValues(mods, "dicomInstitutionDepartmentName",
                a.getInstitutionalDepartmentNames(), b.getInstitutionalDepartmentNames());
        diffValues(mods, "dicomPrimaryDeviceType",
                a.getPrimaryDeviceTypes(), b.getPrimaryDeviceTypes());
        diffValues(mods, "dicomRelatedDeviceReference",
                a.getRelatedDeviceReferences(), b.getRelatedDeviceReferences());
        diffValues(mods, "dicomAuthorizedNodeCertificateReference",
                a.getAuthorizedNodeCertificateReferences(), b.getAuthorizedNodeCertificateReferences());
        diffValues(mods, "dicomThisNodeCertificateReference",
                a.getThisNodeCertificateReferences(), b.getThisNodeCertificateReferences());
        diffBoolean(mods, "dicomInstalled",
                a.isInstalled(), b.isInstalled());
        diffOptionalInt(mods, "dcmLimitOpenAssociations",
                a.getLimitOpenAssociations(), b.getLimitOpenAssociations());
        return mods;
    }

    private void persist(Connection conn, Rdn[] deviceRdns) throws NamingException {
        createSubcontext(cat(deviceRdns, rdnOf(conn)), toAttrSet(conn));
    }

    private static Rdn rdnOf(Connection conn) {
        if (conn.getName().isPresent())
            return rdn("cn", conn.getName().get());

        Attributes attrSet = new BasicAttributes("dicomHostname", conn.getHostname());
        putInt(attrSet, "dicomPort", conn.getPort());
        return rdn(attrSet);
    }

    private static String dnOf(Connection conn, Rdn[] ctxRdns, Rdn[] deviceRdns) {
        return toName(cat(ctxRdns, deviceRdns, rdnOf(conn))).toString();
    }

    private Attributes toAttrSet(Connection conn) {
        Attributes attrSet = objectClass("dicomNetworkConnection");
        putValue(attrSet, "dicomHostname", conn.getHostname());
        putInt(attrSet, "dicomPort", conn.getPort());
        putValues(attrSet, "dicomTLSCipherSuite", conn.getTlsCipherSuites());
        putBoolean(attrSet, "dicomInstalled", conn.getInstalled());
        return attrSet;
    }

    private Connection toConnection(SearchResult searchResult, Map<String, Connection> dnConnectionMap)
            throws NamingException {
        Connection conn = toConnection(searchResult.getAttributes());
        dnConnectionMap.put(searchResult.getNameInNamespace(), conn);
        return conn;
    }

    private Connection toConnection(Attributes attrSet) throws NamingException {
        Connection conn = new Connection();
        ifPresent(attrSet, "cn", Object::toString,
                conn::setName);
        ifPresent(attrSet, "dicomHostname", Object::toString,
                conn::setHostname);
        ifPresent(attrSet, "dicomPort", intMapping,
                conn::setPort);
        ifPresent(attrSet, "dicomTLSCipherSuite", Object::toString, String.class,
                conn::setTlsCipherSuites);
        ifPresent(attrSet, "dicomInstalled", booleanMapping,
                conn::setInstalled);
        return conn;
    }

    private void mergeConns(Device prevDevice, Device device, Rdn[] deviceRdns) throws NamingException {
        Map<Rdn, Connection> a = toMap(prevDevice.getConnections(), LdapDicomConfiguration::rdnOf);
        Map<Rdn, Connection> b = toMap(device.getConnections(), LdapDicomConfiguration::rdnOf);
        for (Rdn rdn : a.keySet()) {
            if (!b.containsKey(rdn)) {
                destroySubcontext(LdapUtils.cat(deviceRdns, rdn));
            }
        }
        for (Map.Entry<Rdn, Connection> entry : b.entrySet()) {
            Rdn rdn = entry.getKey();
            if (!a.containsKey(rdn)) {
                persist(entry.getValue(), deviceRdns);
            } else {
                modifyAttributes(cat(deviceRdns, rdn), diff(a.get(rdn), b.get(rdn)));
            }
        }
    }

    private List<ModificationItem> diff(Connection a, Connection b) {
        List<ModificationItem> mods = new ArrayList<>();
        LdapUtils.diffValue(mods, "dicomHostname",
                a.getHostname(), b.getHostname());
        diffOptionalInt(mods, "dicomPort",
                a.getPort(), b.getPort());
        diffValues(mods, "dicomTLSCipherSuite",
                a.getTlsCipherSuites(), b.getTlsCipherSuites());
        diffOptionalBoolean(mods, "dicomInstalled",
                a.getInstalled(), b.getInstalled());
        return mods;
    }

    private void persist(ApplicationEntity ae, Rdn[] deviceRdns) throws NamingException {
        Rdn[] aeRdns = LdapUtils.cat(deviceRdns, rdnOfAE(ae.getAETitle()));
        createSubcontext(aeRdns, toAttrSet(ae, deviceRdns));
        for (TransferCapability tc : ae.getTransferCapabilities()) {
            persist(tc, aeRdns);
        }
    }

    private static Rdn rdnOfAE(String aet) {
        return rdn("dicomAETitle", aet);
    }

    private Attributes toAttrSet(ApplicationEntity ae, Rdn[] deviceRdns) {
        Attributes attrSet = objectClass("dicomNetworkAE");
        putValue(attrSet, "dicomDescription", ae.getDescription());
        putValues(attrSet, "dicomApplicationCluster", ae.getApplicationClusters());
        putBoolean(attrSet, "dicomAssociationInitiator", ae.isAssociationInitiator());
        putBoolean(attrSet, "dicomAssociationAcceptor", ae.isAssociationAcceptor());
        putValues(attrSet, "dicomSupportedCharacterSet", ae.getSupportedCharacterSets());
        putValues(attrSet, "dicomNetworkConnectionReference", ae.getConnections(),
                conn -> dnOf(conn, ctxRdns, deviceRdns));
        putBoolean(attrSet, "dicomInstalled", ae.getInstalled());
        return attrSet;
    }

    private ApplicationEntity toApplicationEntity(Attributes attrSet, Map<String, Connection> dnConnectionMap,
            Rdn[] deviceRdns) throws NamingException {
        ApplicationEntity ae = new ApplicationEntity();
        ifPresent(attrSet, "dicomAETitle", Object::toString,
                ae::setAETitle);
        ifPresent(attrSet, "dicomDescription", Object::toString,
                ae::setDescription);
        ifPresent(attrSet, "dicomApplicationCluster", Object::toString, String.class,
                ae::setApplicationClusters);
        ifPresent(attrSet, "dicomAssociationInitiator", booleanMapping,
                ae::setAssociationInitiator);
        ifPresent(attrSet, "dicomAssociationAcceptor", booleanMapping,
                ae::setAssociationAcceptor);
        ifPresent(attrSet, "dicomSupportedCharacterSet", Object::toString, String.class,
                ae::setSupportedCharacterSets);
        forEach(attrSet, "dicomNetworkConnectionReference", o -> dnConnectionMap.get(o.toString()),
                ae::addConnection);
        ifPresent(attrSet, "dicomInstalled", booleanMapping,
                ae::setInstalled);
        forEach(ctx, toName(cat(deviceRdns, rdnOfAE(ae.getAETitle()))), "dicomTransferCapability",
                searchResult -> ae.addTransferCapability(toTransferCapability(searchResult.getAttributes())));
        return ae;
    }

    private void mergeAEs(Device prevDevice, Device device, Rdn[] deviceRdns) throws NamingException {
        Map<String, ApplicationEntity> a = toMap(prevDevice.getApplicationEntities(), ApplicationEntity::getAETitle);
        Map<String, ApplicationEntity> b = toMap(device.getApplicationEntities(), ApplicationEntity::getAETitle);
        for (String aet : a.keySet()) {
            if (!b.containsKey(aet)) {
                destroySubcontext(LdapUtils.cat(deviceRdns, rdnOfAE(aet)));
            }
        }
        for (Map.Entry<String, ApplicationEntity> entry : b.entrySet()) {
            String aet = entry.getKey();
            if (!a.containsKey(aet)) {
                persist(entry.getValue(), deviceRdns);
            } else {
                ApplicationEntity a1 = a.get(aet);
                ApplicationEntity b1 = b.get(aet);
                Rdn[] aeRdns = cat(deviceRdns, rdnOfAE(aet));
                modifyAttributes(aeRdns, diff(a1, b1, deviceRdns));
                mergeTCs(a1, b1, aeRdns);
            }
        }
    }

    private List<ModificationItem> diff(ApplicationEntity a, ApplicationEntity b, Rdn[] deviceRdns) {
        List<ModificationItem> mods = new ArrayList<>();
        diffOptionalValue(mods, "dicomDescription",
                a.getDescription(), b.getDescription());
        diffValues(mods, "dicomApplicationCluster",
                a.getApplicationClusters(), b.getApplicationClusters());
        diffBoolean(mods, "dicomAssociationInitiator",
                a.isAssociationInitiator(), b.isAssociationInitiator());
        diffBoolean(mods, "dicomAssociationAcceptor",
                a.isAssociationAcceptor(), b.isAssociationAcceptor());
        diffValues(mods, "dicomSupportedCharacterSet",
                a.getSupportedCharacterSets(), b.getSupportedCharacterSets());
        diffValues(mods, "dicomNetworkConnectionReference",
                toMap(a.getConnections(), conn -> dnOf(conn, ctxRdns, deviceRdns)).keySet(),
                toMap(b.getConnections(), conn -> dnOf(conn, ctxRdns, deviceRdns)).keySet());
        diffOptionalBoolean(mods, "dicomInstalled",
                a.getInstalled(), b.getInstalled());
        return mods;
    }

    private void persist(TransferCapability tc, Rdn[] aeRdns) throws NamingException {
        createSubcontext(cat(aeRdns, rdnOf(tc)), toAttrSet(tc));
    }

    private static Rdn rdnOf(TransferCapability tc) {
        if (tc.getName().isPresent())
            return rdn("cn", tc.getName().get());
        Attributes attrSet = new BasicAttributes("dicomSOPClass", tc.getSOPClass());
        putValue(attrSet, "dicomTransferRole", tc.getRole());
        return rdn(attrSet);
    }

    private Attributes toAttrSet(TransferCapability tc) {
        Attributes attrSet = objectClass("dicomTransferCapability");
        putValue(attrSet,"dicomSOPClass", tc.getSOPClass());
        putValue(attrSet,"dicomTransferRole", tc.getRole());
        putValues(attrSet,"dicomTransferSyntax", tc.getTransferSyntaxes());
        return attrSet;
    }

    private TransferCapability toTransferCapability(Attributes attrSet) throws NamingException {
        TransferCapability tc = new TransferCapability();
        ifPresent(attrSet, "cn", Object::toString,
                tc::setName);
        ifPresent(attrSet, "dicomSOPClass", Object::toString,
                tc::setSOPClass);
        ifPresent(attrSet, "dicomTransferRole", roleMapping,
                tc::setRole);
        ifPresent(attrSet, "dicomTransferSyntax", Object::toString, String.class,
                tc::setTransferSyntaxes);
        return tc;
    }

    private void mergeTCs(ApplicationEntity prevAE, ApplicationEntity ae, Rdn[] aeRdns) throws NamingException {
        Map<Rdn, TransferCapability> a = toMap(prevAE.getTransferCapabilities(), LdapDicomConfiguration::rdnOf);
        Map<Rdn, TransferCapability> b = toMap(ae.getTransferCapabilities(), LdapDicomConfiguration::rdnOf);
        for (Rdn rdn : a.keySet()) {
            if (!b.containsKey(rdn)) {
                destroySubcontext(LdapUtils.cat(aeRdns, rdn));
            }
        }
        for (Map.Entry<Rdn, TransferCapability> entry : b.entrySet()) {
            Rdn rdn = entry.getKey();
            if (!a.containsKey(rdn)) {
                persist(entry.getValue(), aeRdns);
            } else {
                modifyAttributes(cat(aeRdns, rdn), diff(a.get(rdn), b.get(rdn)));
            }
        }
    }

    private List<ModificationItem> diff(TransferCapability a, TransferCapability b) {
        List<ModificationItem> mods = new ArrayList<>();
        diffValue(mods, "dicomSOPClass",
                a.getSOPClass(), b.getSOPClass());
        diffValue(mods, "dicomTransferRole",
                a.getRole(), b.getRole());
        diffValues(mods, "dicomTransferSyntax",
                a.getTransferSyntaxes(), b.getTransferSyntaxes());
        return mods;
    }

    private void persist(KeyStoreConfiguration ks, Rdn[] deviceRdns) throws NamingException {
        Rdn[] ksRdns = cat(deviceRdns, rdnOfKeyStore(ks.getName()));
        createSubcontext(ksRdns, toAttrSet(ks));
    }

    private static Rdn rdnOfKeyStore(String name) {
        return rdn("dcmKeyStoreName", name);
    }

    private Attributes toAttrSet(KeyStoreConfiguration ks) {
        Attributes attrSet = objectClass("dcmKeyStore");
        putValue(attrSet, "dcmKeyStoreType", ks.getKeyStoreType());
        putValue(attrSet, "dcmProvider", ks.getProvider());
        putValue(attrSet, "dcmPath", ks.getPath());
        putValue(attrSet, "dcmURL", ks.getURL());
        putValue(attrSet, "dcmPassword", ks.getPassword());
        return attrSet;
    }

    private KeyStoreConfiguration toKeyStoreConfiguration(Attributes attrSet) throws NamingException {
        KeyStoreConfiguration ks = new KeyStoreConfiguration();
        ifPresent(attrSet, "dcmKeyStoreName", Object::toString,
                ks::setName);
        ifPresent(attrSet, "dcmKeyStoreType", Object::toString,
                ks::setKeyStoreType);
        ifPresent(attrSet, "dcmProvider", Object::toString,
                ks::setProvider);
        ifPresent(attrSet, "dcmPath", Object::toString,
                ks::setPath);
        ifPresent(attrSet, "dcmURL", Object::toString,
                ks::setURL);
        ifPresent(attrSet, "dcmPassword", Object::toString,
                ks::setPassword);
        return ks;
    }

    private void mergeKeyStoreConfigs(Device prevDevice, Device device, Rdn[] deviceRdns) throws NamingException {
        Map<String, KeyStoreConfiguration> a =
                toMap(prevDevice.getKeyStoreConfigurations(), KeyStoreConfiguration::getName);
        Map<String, KeyStoreConfiguration> b =
                toMap(device.getKeyStoreConfigurations(), KeyStoreConfiguration::getName);
        for (String name : a.keySet()) {
            if (!b.containsKey(name)) {
                destroySubcontext(LdapUtils.cat(deviceRdns, rdnOfKeyStore(name)));
            }
        }
        for (Map.Entry<String, KeyStoreConfiguration> entry : b.entrySet()) {
            String name = entry.getKey();
            if (!a.containsKey(name)) {
                persist(entry.getValue(), deviceRdns);
            } else {
                KeyStoreConfiguration a1 = a.get(name);
                KeyStoreConfiguration b1 = b.get(name);
                Rdn[] ksRdns = cat(deviceRdns, rdnOfKeyStore(name));
                modifyAttributes(ksRdns, diff(a1, b1));
            }
        }
    }

    private List<ModificationItem> diff(KeyStoreConfiguration a, KeyStoreConfiguration b) {
        List<ModificationItem> mods = new ArrayList<>();
        diffValue(mods, "dcmKeyStoreType",
                a.getKeyStoreType(),
                b.getKeyStoreType());
        diffValue(mods, "dcmProvider",
                a.getProvider(),
                b.getProvider());
        diffValue(mods, "dcmPath",
                a.getPath(),
                b.getPath());
        diffValue(mods, "dcmURL",
                a.getURL(),
                b.getURL());
        diffValue(mods, "dcmPassword",
                a.getPassword(),
                b.getPassword());
        return mods;
    }

    private void persist(KeyManagerConfiguration km, Rdn[] deviceRdns) throws NamingException {
        Rdn[] kmRdns = cat(deviceRdns, rdnOfKeyManager());
        createSubcontext(kmRdns, toAttrSet(km));
    }

    private static Rdn rdnOfKeyManager() {
        return rdn("cn", "Key Manager");
    }

    private Attributes toAttrSet(KeyManagerConfiguration km) {
        Attributes attrSet = objectClass("dcmKeyManager");
        putValue(attrSet, "dcmKeyStoreName", km.getKeyStoreConfiguration().getName());
        putValue(attrSet, "dcmAlgorithm", km.getAlgorithm());
        putValue(attrSet, "dcmProvider", km.getProvider());
        putValue(attrSet, "dcmPassword", km.getPassword());
        return attrSet;
    }

    private KeyManagerConfiguration toKeyManagerConfiguration(Attributes attrSet, Device device) {
        KeyManagerConfiguration km = new KeyManagerConfiguration();
        ifPresent(attrSet, "dcmKeyStoreName", o -> device.getKeyStoreConfiguration(o.toString()).orElseThrow(),
                km::setKeyStoreConfiguration);
        ifPresent(attrSet, "dcmAlgorithm", Object::toString,
                km::setAlgorithm);
        ifPresent(attrSet, "dcmProvider", Object::toString,
                km::setProvider);
        ifPresent(attrSet, "dcmPassword", Object::toString,
                km::setPassword);
        return km;
    }

    private void mergeKeyManagerConfig(Device prevDevice, Device device, Rdn[] deviceRdns)
            throws NamingException {
        Optional<KeyManagerConfiguration> a = prevDevice.getKeyManagerConfiguration();
        Optional<KeyManagerConfiguration> b = device.getKeyManagerConfiguration();
        if (b.isPresent()) {
            if (a.isPresent()) {
                Rdn[] ksRdns = cat(deviceRdns, rdnOfKeyManager());
                modifyAttributes(ksRdns, diff(a.get(), b.get()));
            } else {
                persist(b.get(), deviceRdns);
            }
        } else if (a.isPresent()) {
            destroySubcontext(LdapUtils.cat(deviceRdns, rdnOfKeyManager()));
        }
    }

    private List<ModificationItem> diff(KeyManagerConfiguration a, KeyManagerConfiguration b) {
        List<ModificationItem> mods = new ArrayList<>();
        diffValue(mods, "dcmKeyStoreName",
                a.getKeyStoreConfiguration().getName(),
                b.getKeyStoreConfiguration().getName());
        diffValue(mods, "dcmAlgorithm",
                a.getAlgorithm(),
                b.getAlgorithm());
        diffValue(mods, "dcmProvider",
                a.getProvider(),
                b.getProvider());
        diffValue(mods, "dcmPassword",
                a.getPassword(),
                b.getPassword());
        return mods;
    }

    private void persist(TrustManagerConfiguration tm, Rdn[] deviceRdns) throws NamingException {
        Rdn[] tmRdns = cat(deviceRdns, rdnOfTrustManager());
        createSubcontext(tmRdns, toAttrSet(tm));
    }

    private static Rdn rdnOfTrustManager() {
        return rdn("cn", "Trust Manager");
    }

    private Attributes toAttrSet(TrustManagerConfiguration tm) {
        Attributes attrSet = objectClass("dcmTrustManager");
        putValue(attrSet, "dcmKeyStoreName", tm.getKeyStoreConfiguration().getName());
        putValue(attrSet, "dcmAlgorithm", tm.getAlgorithm());
        putValue(attrSet, "dcmProvider", tm.getProvider());
        return attrSet;
    }

    private TrustManagerConfiguration toTrustManagerConfiguration(Attributes attrSet, Device device) {
        TrustManagerConfiguration tm = new TrustManagerConfiguration();
        ifPresent(attrSet, "dcmKeyStoreName", o -> device.getKeyStoreConfiguration(o.toString()).orElseThrow(),
                tm::setKeyStoreConfiguration);
        ifPresent(attrSet, "dcmAlgorithm", Object::toString,
                tm::setAlgorithm);
        ifPresent(attrSet, "dcmProvider", Object::toString,
                tm::setProvider);
        return tm;
    }

    private void mergeTrustManagerConfig(Device prevDevice, Device device, Rdn[] deviceRdns)
            throws NamingException {
        Optional<TrustManagerConfiguration> a = prevDevice.getTrustManagerConfiguration();
        Optional<TrustManagerConfiguration> b = device.getTrustManagerConfiguration();
        if (b.isPresent()) {
            if (a.isPresent()) {
                Rdn[] ksRdns = cat(deviceRdns, rdnOfTrustManager());
                modifyAttributes(ksRdns, diff(a.get(), b.get()));
            } else {
                persist(b.get(), deviceRdns);
            }
        } else if (a.isPresent()) {
            destroySubcontext(LdapUtils.cat(deviceRdns, rdnOfTrustManager()));
        }
    }

    private List<ModificationItem> diff(TrustManagerConfiguration a, TrustManagerConfiguration b) {
        List<ModificationItem> mods = new ArrayList<>();
        diffValue(mods, "dcmKeyStoreName",
                a.getKeyStoreConfiguration().getName(),
                b.getKeyStoreConfiguration().getName());
        diffValue(mods, "dcmAlgorithm",
                a.getAlgorithm(),
                b.getAlgorithm());
        diffValue(mods, "dcmProvider",
                a.getProvider(),
                b.getProvider());
        return mods;
    }
}

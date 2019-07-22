package org.dcm4che6.conf.model;

import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;
import org.dcm4che6.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2019
 */
public class Device {

    private volatile String deviceName = "default";
    private volatile String deviceUID;
    private volatile String description;
    private volatile String manufacturer;
    private volatile String manufacturerModelName;
    private volatile String stationName;
    private volatile String deviceSerialNumber;
    private volatile Issuer issuerOfPatientID;
    private volatile Issuer issuerOfAccessionNumber;
    private volatile Issuer orderPlacerIdentifier;
    private volatile Issuer orderFillerIdentifier;
    private volatile Issuer issuerOfAdmissionID;
    private volatile Issuer issuerOfServiceEpisodeID;
    private volatile Issuer issuerOfContainerIdentifier;
    private volatile Issuer issuerOfSpecimenIdentifier;
    private volatile String[] softwareVersions = {};
    private volatile String[] primaryDeviceTypes = {};
    private volatile String[] institutionNames = {};
    private volatile Code[] institutionCodes = {};
    private volatile String[] institutionAddresses = {};
    private volatile String[] institutionalDepartmentNames = {};
    private volatile String[] relatedDeviceReferences = {};
    private volatile String[] authorizedNodeCertificateReferences = {};
    private volatile String[] thisNodeCertificateReferences = {};

    private volatile boolean installed = true;

    private volatile int limitOpenAssociations = -1;
    private volatile KeyManagerConfiguration keyManagerConfiguration;
    private volatile TrustManagerConfiguration trustManagerConfiguration;

    private final List<Connection> conns = new ArrayList<>();
    private final List<ApplicationEntity> aes = new ArrayList<>();
    private final List<KeyStoreConfiguration> keyStoreConfigurations = new ArrayList<>();
    private final Map<Class,Object> deviceExtensions = new ConcurrentHashMap<>();

    public String getDeviceName() {
        return deviceName;
    }

    public Device setDeviceName(String deviceName) {
        this.deviceName = StringUtils.requireNonBlank(deviceName);
        return this;
    }

    public Optional<String> getDeviceUID() {
        return Optional.ofNullable(deviceUID);
    }

    public Device setDeviceUID(String deviceUID) {
        this.deviceUID = StringUtils.trimAndNullifyEmpty(deviceUID);
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Device setDescription(String description) {
        this.description = StringUtils.trimAndNullifyEmpty(description);
        return this;
    }

    public Optional<String> getManufacturer() {
        return Optional.ofNullable(manufacturer);
    }

    public Device setManufacturer(String manufacturer) {
        this.manufacturer = StringUtils.trimAndNullifyEmpty(manufacturer);
        return this;
    }

    public Optional<String> getManufacturerModelName() {
        return Optional.ofNullable(manufacturerModelName);
    }

    public Device setManufacturerModelName(String manufacturerModelName) {
        this.manufacturerModelName = StringUtils.trimAndNullifyEmpty(manufacturerModelName);
        return this;
    }

    public Optional<String> getStationName() {
        return Optional.ofNullable(stationName);
    }

    public Device setStationName(String stationName) {
        this.stationName = StringUtils.trimAndNullifyEmpty(stationName);
        return this;
    }

    public Optional<String> getDeviceSerialNumber() {
        return Optional.ofNullable(deviceSerialNumber);
    }

    public Device setDeviceSerialNumber(String deviceSerialNumber) {
        this.deviceSerialNumber = StringUtils.trimAndNullifyEmpty(deviceSerialNumber);
        return this;
    }

    public Optional<Issuer> getIssuerOfPatientID() {
        return Optional.ofNullable(issuerOfPatientID);
    }

    public Device setIssuerOfPatientID(Issuer issuerOfPatientID) {
        this.issuerOfPatientID = issuerOfPatientID;
        return this;
    }

    public Optional<Issuer> getIssuerOfAccessionNumber() {
        return Optional.ofNullable(issuerOfAccessionNumber);
    }

    public Device setIssuerOfAccessionNumber(Issuer issuerOfAccessionNumber) {
        this.issuerOfAccessionNumber = issuerOfAccessionNumber;
        return this;
    }

    public Optional<Issuer> getOrderPlacerIdentifier() {
        return Optional.ofNullable(orderPlacerIdentifier);
    }

    public Device setOrderPlacerIdentifier(Issuer orderPlacerIdentifier) {
        this.orderPlacerIdentifier = orderPlacerIdentifier;
        return this;
    }

    public Optional<Issuer> getOrderFillerIdentifier() {
        return Optional.ofNullable(orderFillerIdentifier);
    }

    public Device setOrderFillerIdentifier(Issuer orderFillerIdentifier) {
        this.orderFillerIdentifier = orderFillerIdentifier;
        return this;
    }

    public Optional<Issuer> getIssuerOfAdmissionID() {
        return Optional.ofNullable(issuerOfAdmissionID);
    }

    public Device setIssuerOfAdmissionID(Issuer issuerOfAdmissionID) {
        this.issuerOfAdmissionID = issuerOfAdmissionID;
        return this;
    }

    public Optional<Issuer> getIssuerOfServiceEpisodeID() {
        return Optional.ofNullable(issuerOfServiceEpisodeID);
    }

    public Device setIssuerOfServiceEpisodeID(Issuer issuerOfServiceEpisodeID) {
        this.issuerOfServiceEpisodeID = issuerOfServiceEpisodeID;
        return this;
    }

    public Optional<Issuer> getIssuerOfContainerIdentifier() {
        return Optional.ofNullable(issuerOfContainerIdentifier);
    }

    public Device setIssuerOfContainerIdentifier(Issuer issuerOfContainerIdentifier) {
        this.issuerOfContainerIdentifier = issuerOfContainerIdentifier;
        return this;
    }

    public Optional<Issuer> getIssuerOfSpecimenIdentifier() {
        return Optional.ofNullable(issuerOfSpecimenIdentifier);
    }

    public Device setIssuerOfSpecimenIdentifier(Issuer issuerOfSpecimenIdentifier) {
        this.issuerOfSpecimenIdentifier = issuerOfSpecimenIdentifier;
        return this;
    }

    public List<String> getSoftwareVersions() {
        return List.of(softwareVersions);
    }

    public Device setSoftwareVersions(String... softwareVersions) {
        this.softwareVersions = Objects.requireNonNullElse(softwareVersions, StringUtils.EMPTY_STRINGS);
        return this;
    }

    public List<String> getPrimaryDeviceTypes() {
        return List.of(primaryDeviceTypes);
    }

    public Device setPrimaryDeviceTypes(String... primaryDeviceTypes) {
        this.primaryDeviceTypes = Objects.requireNonNullElse(primaryDeviceTypes, StringUtils.EMPTY_STRINGS);
        return this;
    }

    public List<String> getInstitutionNames() {
        return List.of(institutionNames);
    }

    public Device setInstitutionNames(String... institutionNames) {
        this.institutionNames = institutionNames;
        return this;
    }

    public List<Code> getInstitutionCodes() {
        return List.of(institutionCodes);
    }

    public Device setInstitutionCodes(Code... institutionCodes) {
        this.institutionCodes = institutionCodes;
        return this;
    }

    public List<String> getInstitutionAddresses() {
        return List.of(institutionAddresses);
    }

    public Device setInstitutionAddresses(String... institutionAddresses) {
        this.institutionAddresses = institutionAddresses;
        return this;
    }

    public List<String> getInstitutionalDepartmentNames() {
        return List.of(institutionalDepartmentNames);
    }

    public Device setInstitutionalDepartmentNames(String... institutionalDepartmentNames) {
        this.institutionalDepartmentNames = institutionalDepartmentNames;
        return this;
    }

    public List<String> getRelatedDeviceReferences() {
        return List.of(relatedDeviceReferences);
    }

    public Device setRelatedDeviceReferences(String... relatedDeviceReferences) {
        this.relatedDeviceReferences = relatedDeviceReferences;
        return this;
    }

    public List<String> getAuthorizedNodeCertificateReferences() {
        return List.of(authorizedNodeCertificateReferences);
    }

    public Device setAuthorizedNodeCertificateReferences(String... authorizedNodeCertificateReferences) {
        this.authorizedNodeCertificateReferences = authorizedNodeCertificateReferences;
        return this;
    }

    public List<String> getThisNodeCertificateReferences() {
        return List.of(thisNodeCertificateReferences);
    }

    public Device setThisNodeCertificateReferences(String... thisNodeCertificateReferences) {
        this.thisNodeCertificateReferences = thisNodeCertificateReferences;
        return this;
    }

    public boolean isInstalled() {
        return installed;
    }

    public Device setInstalled(boolean installed) {
        this.installed = installed;
        return this;
    }

    public OptionalInt getLimitOpenAssociations() {
        return limitOpenAssociations >= 0 ? OptionalInt.of(limitOpenAssociations) : OptionalInt.empty();
    }

    public Device setLimitOpenAssociations(int limitOpenAssociations) {
        this.limitOpenAssociations = limitOpenAssociations;
        return this;
    }

    public Optional<KeyManagerConfiguration> getKeyManagerConfiguration() {
        return Optional.ofNullable(keyManagerConfiguration);
    }

    public Device setKeyManagerConfiguration(KeyManagerConfiguration keyManagerConfiguration) {
        this.keyManagerConfiguration = keyManagerConfiguration;
        return this;
    }

    public Optional<TrustManagerConfiguration> getTrustManagerConfiguration() {
        return Optional.ofNullable(trustManagerConfiguration);
    }

    public Device setTrustManagerConfiguration(TrustManagerConfiguration trustManagerConfiguration) {
        this.trustManagerConfiguration = trustManagerConfiguration;
        return this;
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(conns);
    }

    public Optional<Connection> getConnection(Connection ref) {
        return conns.stream().filter(ref::match).findFirst();
    }

    public Device addConnection(Connection conn) {
        conns.add(Objects.requireNonNull(conn));
        return this;
    }

    public Device removeConnection(Connection conn) {
        conns.remove(Objects.requireNonNull(conn));
        return this;
    }

    public List<ApplicationEntity> getApplicationEntities() {
        return Collections.unmodifiableList(aes);
    }

    public Device addApplicationEntity(ApplicationEntity ae) {
        aes.add(Objects.requireNonNull(ae));
        return this;
    }

    public Device removeApplicationEntity(ApplicationEntity ae) {
        aes.remove(Objects.requireNonNull(ae));
        return this;
    }

    public List<KeyStoreConfiguration> getKeyStoreConfigurations() {
        return Collections.unmodifiableList(keyStoreConfigurations);
    }

    public Device addKeyStoreConfiguration(KeyStoreConfiguration keyStoreReference) {
        keyStoreConfigurations.add(Objects.requireNonNull(keyStoreReference));
        return this;
    }

    public Device removeKeyStoreConfiguration(KeyStoreConfiguration keyStoreReference) {
        keyStoreConfigurations.remove(Objects.requireNonNull(keyStoreReference));
        return this;
    }

    public Optional<KeyStoreConfiguration> getKeyStoreConfiguration(String name) {
        return keyStoreConfigurations.stream().filter(ks -> name.equals(ks.getName())).findAny();
    }

    public Collection<Object> getDeviceExtensions() {
        return Collections.unmodifiableCollection(deviceExtensions.values());
    }

    public <T> T getDeviceExtension(Class<T> aClass) {
        return aClass.cast(deviceExtensions.get(aClass));
    }

    public Device addDeviceExtension(Object o) {
        deviceExtensions.put(o.getClass(), o);
        return this;
    }

    public boolean isStrictDicom() {
        return limitOpenAssociations < 0
                && keyStoreConfigurations.isEmpty()
                && deviceExtensions.isEmpty();
    }
}

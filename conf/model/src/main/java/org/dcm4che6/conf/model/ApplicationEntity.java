package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public class ApplicationEntity {

    private volatile String aeTitle = "*";
    private volatile String description;
    private volatile String[] applicationClusters = {};
    private volatile String[] supportedCharacterSets = {};
    private volatile boolean acceptor = true;
    private volatile boolean initiator = true;
    private volatile Boolean installed;
    private volatile Device device;

    private final List<Connection> conns = new ArrayList<>();
    private final List<TransferCapability> tcs = new ArrayList<>();

    public Optional<Device> getDevice() {
        return Optional.ofNullable(device);
    }

    public ApplicationEntity setDevice(Device device) {
        if (this.device != device) {
            if (this.device != null && device != null)
                throw new IllegalStateException("ApplicationEntity already contained by " + device);
            if (device != null)
                conns.forEach(conn -> conn.setDevice(device));
            this.device = device;
        }
        return this;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public ApplicationEntity setAETitle(String title) {
        this.aeTitle = StringUtils.requireNonBlank(title);
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public ApplicationEntity setDescription(String description) {
        this.description = StringUtils.trimAndNullifyEmpty(description);
        return this;
    }

    public List<String> getApplicationClusters() {
        return List.of(applicationClusters);
    }

    public ApplicationEntity setApplicationClusters(String... applicationClusters) {
        this.applicationClusters = Objects.requireNonNullElse(applicationClusters, StringUtils.EMPTY_STRINGS);
        return this;
    }

    public List<String> getSupportedCharacterSets() {
        return List.of(supportedCharacterSets);
    }

    public ApplicationEntity setSupportedCharacterSets(String... supportedCharacterSets) {
        this.supportedCharacterSets = Objects.requireNonNullElse(supportedCharacterSets, StringUtils.EMPTY_STRINGS);
        return this;
    }

    public boolean isAssociationAcceptor() {
        return acceptor;
    }

    public ApplicationEntity setAssociationAcceptor(boolean acceptor) {
        this.acceptor = acceptor;
        return this;
    }

    public boolean isAssociationInitiator() {
        return initiator;
    }

    public ApplicationEntity setAssociationInitiator(boolean initiator) {
        this.initiator = initiator;
        return this;
    }

    public Optional<Boolean> getInstalled() {
        return Optional.ofNullable(installed);
    }

    public ApplicationEntity setInstalled(Boolean installed) {
        this.installed = installed;
        return this;
    }

    public boolean isInstalled() {
        return (device == null || device.isInstalled()) && installed != Boolean.FALSE;
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(conns);
    }

    public ApplicationEntity removeConnection(Connection conn) {
        conns.remove(Objects.requireNonNull(conn));
        return this;
    }

    public ApplicationEntity clearConnections() {
        conns.clear();
        return this;
    }

    public ApplicationEntity addConnection(Connection conn) {
        conns.add(conn.setDevice(device));
        return this;
    }

    public boolean hasConnection(Connection conn) {
        return conns.contains(conn);
    }

    public List<TransferCapability> getTransferCapabilities() {
        return Collections.unmodifiableList(tcs);
    }

    public Optional<TransferCapability> getTransferCapabilityOrDefault(
            TransferCapability.Role role, String abstractSyntax) {
        return getTransferCapability(role, abstractSyntax).or(() -> getDefaultTransferCapability(role));
    }

    public Optional<TransferCapability> getDefaultTransferCapability(TransferCapability.Role role) {
        return getTransferCapability(role, "*");
    }

    public Optional<TransferCapability> getTransferCapability(TransferCapability.Role role, String abstractSyntax) {
        return tcs.stream()
                .filter(tc -> tc.getRole().equals(role) && tc.getSOPClass().equals(abstractSyntax))
                .findFirst();
    }

    public ApplicationEntity removeTransferCapability(TransferCapability tc) {
        tcs.remove(Objects.requireNonNull(tc));
        return this;
    }

    public ApplicationEntity addTransferCapability(TransferCapability tc) {
        tcs.add(Objects.requireNonNull(tc));
        return this;
    }
}

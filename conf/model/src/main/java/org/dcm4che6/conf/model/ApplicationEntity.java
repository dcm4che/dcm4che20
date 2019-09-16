package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public class ApplicationEntity {

    private String aeTitle = "*";
    private String description;
    private String[] applicationClusters = {};
    private String[] supportedCharacterSets = {};
    private boolean acceptor = true;
    private boolean initiator = true;
    private Boolean installed;

    private final List<Connection> conns = new ArrayList<>();
    private final List<TransferCapability> tcs = new ArrayList<>();

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
        conns.add(Objects.requireNonNull(conn));
        return this;
    }

    public List<TransferCapability> getTransferCapabilities() {
        return Collections.unmodifiableList(tcs);
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

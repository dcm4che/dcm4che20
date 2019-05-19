package org.dcm4che6.conf.model;

import org.dcm4che6.data.UID;
import org.dcm4che6.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class TransferCapability {
    public enum Role { SCU, SCP }

    private String name;
    private String sopClass = UID.VerificationSOPClass;
    private Role role = Role.SCP;
    private String[] transferSyntaxes = {};

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public TransferCapability setName(String name) {
        this.name = StringUtils.trimAndNullifyEmpty(name);
        return this;
    }

    public String getSOPClass() {
        return sopClass;
    }

    public TransferCapability setSOPClass(String sopClass) {
        this.sopClass = StringUtils.requireNonBlank(sopClass);
        return this;
    }

    public Role getRole() {
        return role;
    }

    public TransferCapability setRole(Role role) {
        this.role = Objects.requireNonNull(role);
        return this;
    }

    public List<String> getTransferSyntaxes() {
        return List.of(transferSyntaxes);
    }

    public TransferCapability setTransferSyntaxes(String... transferSyntaxes) {
        this.transferSyntaxes = Objects.requireNonNullElse(transferSyntaxes, StringUtils.EMPTY_STRINGS);
        return this;
    }
}

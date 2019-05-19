package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class Connection {
    public static final int NOT_LISTENING = -1;
    public static final String TLS_RSA_WITH_NULL_SHA = "SSL_RSA_WITH_NULL_SHA";
    public static final String TLS_RSA_WITH_3DES_EDE_CBC_SHA = "SSL_RSA_WITH_3DES_EDE_CBC_SHA";
    public static final String TLS_RSA_WITH_AES_128_CBC_SHA = "TLS_RSA_WITH_AES_128_CBC_SHA";

    private String name;
    private String hostname = "localhost";
    private int port = NOT_LISTENING;
    private String[] tlsCipherSuites = {};
    private Boolean installed;

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Connection setName(String name) {
        this.name = StringUtils.trimAndNullifyEmpty(name);
        return this;
    }

    public String getHostname() {
        return hostname;
    }

    public Connection setHostname(String hostname) {
        this.hostname = StringUtils.requireNonBlank(hostname);
        return this;
    }

    public OptionalInt getPort() {
        return port >= 0 ? OptionalInt.of(port) : OptionalInt.empty();
    }

    public Connection setPort(int port) {
        if (port < NOT_LISTENING)
            throw new IllegalArgumentException("port < 0");
        this.port = port;
        return this;
    }

    public List<String> getTlsCipherSuites() {
        return List.of(tlsCipherSuites);
    }

    public Connection setTlsCipherSuites(String... tlsCipherSuites) {
        this.tlsCipherSuites = Objects.requireNonNullElse(tlsCipherSuites, StringUtils.EMPTY_STRINGS);
        return this;
    }

    public Optional<Boolean> getInstalled() {
        return Optional.ofNullable(installed);
    }

    public Connection setInstalled(Boolean installed) {
        this.installed = installed;
        return this;
    }

    boolean match(Connection other) {
        return name != null
                ? name.equals(other.name)
                : hostname.equals(other.hostname) && port == other.port;
    }
}

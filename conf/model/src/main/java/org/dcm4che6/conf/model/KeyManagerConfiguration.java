package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public class KeyManagerConfiguration {
    private String algorithm;
    private String provider;
    private KeyStoreConfiguration keyStoreConfiguration;
    private String password = "changeit";

    public Optional<String> getAlgorithm() {
        return Optional.ofNullable(algorithm);
    }

    public KeyManagerConfiguration setAlgorithm(String algorithm) {
        this.algorithm = StringUtils.trimAndNullifyEmpty(algorithm);
        return this;
    }

    public Optional<String> getProvider() {
        return Optional.ofNullable(provider);
    }

    public KeyManagerConfiguration setProvider(String provider) {
        this.provider = StringUtils.trimAndNullifyEmpty(provider);
        return this;
    }

    public KeyStoreConfiguration getKeyStoreConfiguration() {
        return Objects.requireNonNull(keyStoreConfiguration);
    }

    public KeyManagerConfiguration setKeyStoreConfiguration(KeyStoreConfiguration keyStoreConfiguration) {
        this.keyStoreConfiguration = Objects.requireNonNull(keyStoreConfiguration);
        return this;
    }

    public String getPassword() {
        return password;
    }

    public KeyManagerConfiguration setPassword(String password) {
        this.password = StringUtils.requireNonBlank(password);
        return this;
    }
}

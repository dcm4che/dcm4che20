package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public class TrustManagerConfiguration {
    private String algorithm;
    private String provider;
    private KeyStoreConfiguration keyStoreConfiguration;

    public Optional<String> getAlgorithm() {
        return Optional.ofNullable(algorithm);
    }

    public TrustManagerConfiguration setAlgorithm(String algorithm) {
        this.algorithm = StringUtils.trimAndNullifyEmpty(algorithm);
        return this;
    }

    public Optional<String> getProvider() {
        return Optional.ofNullable(provider);
    }

    public TrustManagerConfiguration setProvider(String provider) {
        this.provider = StringUtils.trimAndNullifyEmpty(provider);
        return this;
    }

    public KeyStoreConfiguration getKeyStoreConfiguration() {
        return Objects.requireNonNull(keyStoreConfiguration);
    }

    public TrustManagerConfiguration setKeyStoreConfiguration(KeyStoreConfiguration keyStoreConfiguration) {
        this.keyStoreConfiguration = Objects.requireNonNull(keyStoreConfiguration);
        return this;
    }
}

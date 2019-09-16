package org.dcm4che6.conf.model;

import org.dcm4che6.util.StringUtils;

import java.util.Optional;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class KeyStoreConfiguration {
    private String name = "default";
    private String keyStoreType;
    private String provider;
    private String path;
    private String url;
    private String password = "changeit";

    public String getName() {
        return name;
    }

    public KeyStoreConfiguration setName(String name) {
        this.name = StringUtils.requireNonBlank(name);
        return this;
    }

    public Optional<String> getKeyStoreType() {
        return Optional.ofNullable(keyStoreType);
    }

    public KeyStoreConfiguration setKeyStoreType(String keyStoreType) {
        this.keyStoreType = StringUtils.trimAndNullifyEmpty(keyStoreType);
        return this;
    }

    public Optional<String> getProvider() {
        return Optional.ofNullable(provider);
    }

    public KeyStoreConfiguration setProvider(String provider) {
        this.provider = StringUtils.trimAndNullifyEmpty(provider);
        return this;
    }

    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    public KeyStoreConfiguration setPath(String path) {
        this.path = StringUtils.trimAndNullifyEmpty(path);
        return this;
    }

    public Optional<String> getURL() {
        return Optional.ofNullable(url);
    }

    public KeyStoreConfiguration setURL(String url) {
        this.url = StringUtils.trimAndNullifyEmpty(url);
        return this;
    }

    public String getPassword() {
        return password;
    }

    public KeyStoreConfiguration setPassword(String password) {
        this.password = StringUtils.requireNonBlank(password);
        return this;
    }
}

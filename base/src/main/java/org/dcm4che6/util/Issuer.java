package org.dcm4che6.util;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public final class Issuer {
    private final String[] values;

    public Issuer(String ce) {
        this(StringUtils.split(ce, ce.length(), '&'));
    }

    private Issuer(String... values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return StringUtils.join(values, 0, values.length, '&');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Issuer issuer = (Issuer) o;
        return Arrays.equals(values, issuer.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}

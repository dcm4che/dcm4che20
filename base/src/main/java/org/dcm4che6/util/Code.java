package org.dcm4che6.util;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public final class Code {
    private final String[] values;

    public Code(String ce) {
        this(StringUtils.split(ce, ce.length(), '^'));
    }

    private Code(String... values) {
        this.values = values;
    }

    @Override
    public String toString() {
        return StringUtils.join(values, 0, values.length, '^');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Code code = (Code) o;
        return Arrays.equals(values, code.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}

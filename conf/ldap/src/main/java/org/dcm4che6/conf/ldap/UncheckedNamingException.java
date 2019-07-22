package org.dcm4che6.conf.ldap;

import javax.naming.NamingException;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
public class UncheckedNamingException extends RuntimeException {

    public UncheckedNamingException(String message, NamingException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    public UncheckedNamingException(NamingException cause) {
        super(Objects.requireNonNull(cause));
    }

    @Override
    public NamingException getCause() {
        return (NamingException) super.getCause();
    }
}

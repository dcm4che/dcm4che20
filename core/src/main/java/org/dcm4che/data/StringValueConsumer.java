package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2019
 */
@FunctionalInterface
public interface StringValueConsumer<E extends Throwable> {
    void accept(String value, int number) throws E;
}

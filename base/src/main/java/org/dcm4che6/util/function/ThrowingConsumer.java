package org.dcm4che6.util.function;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T value) throws E;
}

package org.dcm4che6.util.function;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2019
 */
@FunctionalInterface
public interface FloatConsumer {
    void accept(float value);
}

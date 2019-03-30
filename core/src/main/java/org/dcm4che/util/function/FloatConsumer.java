package org.dcm4che.util.function;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
@FunctionalInterface
public interface FloatConsumer {
    void accept(float value);
}

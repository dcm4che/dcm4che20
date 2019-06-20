package org.dcm4che6.util.function;

import org.dcm4che6.data.DicomObject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2019
 */
@FunctionalInterface
public interface ItemConsumer<E extends Throwable> {
    void accept(DicomObject item, int number) throws E;
}

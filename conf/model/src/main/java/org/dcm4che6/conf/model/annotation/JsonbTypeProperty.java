package org.dcm4che6.conf.model.annotation;

import java.lang.annotation.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonbTypeProperty {
    String value();
}

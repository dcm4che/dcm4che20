package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.annotation.JsonbTypeProperty;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
@JsonbTypeProperty("dcmExtension")
class Extension {
    String value;

    Extension setValue(String value) {
        this.value = value;
        return this;
    }
}

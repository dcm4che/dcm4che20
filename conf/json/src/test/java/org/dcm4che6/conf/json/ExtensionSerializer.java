package org.dcm4che6.conf.json;

import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
class ExtensionSerializer implements JsonbSerializer<Extension> {
    @Override
    public void serialize(Extension ext, JsonGenerator gen, SerializationContext ctx) {
        gen.writeStartObject();
        gen.write("dcmValue", ext.value);
        gen.writeEnd();
    }
}

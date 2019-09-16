package org.dcm4che6.conf.json;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
class ExtensionDeserializer implements JsonbDeserializer<Extension> {
    @Override
    public Extension deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        Extension ext = new Extension();
        JsonParser.Event event;
        String key;
        while ((event = parser.next()) == JsonParser.Event.KEY_NAME) {
            switch (key = parser.getString()) {
                case "dcmValue":
                    ext.value = DeserializerUtils.deserializeString(parser);
                    break;
                default:
                    DeserializerUtils.unexpectedKey(key);
            }
        }
        DeserializerUtils.assertEvent(JsonParser.Event.END_OBJECT, event);
        return ext;
    }
}

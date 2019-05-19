package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.annotation.JsonbTypeProperty;

import javax.json.stream.JsonGenerator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class SerializerUtils {
    public static <T> void serializeValue(String name, Optional<T> value, JsonGenerator gen) {
        value.ifPresent(x -> gen.write(name, x.toString()));
    }

    public static void serializeBoolean(String name, Optional<Boolean> value, JsonGenerator gen) {
        value.ifPresent(x -> gen.write(name, x.booleanValue()));
    }

    public static <T> void serializeArray(String name, List<T> values, JsonGenerator gen) {
        if (!values.isEmpty()) {
            gen.writeStartArray(name);
            values.forEach(value -> gen.write(value.toString()));
            gen.writeEnd();
        }
    }

    public static void serializeInt(String name, OptionalInt value, JsonGenerator gen) {
        value.ifPresent(x -> gen.write(name, x));
    }

    public static String jsonPropertyOf(Class<?> aClass) {
        JsonbTypeProperty annotation = aClass.getAnnotation(JsonbTypeProperty.class);
        return annotation != null ? annotation.value() : aClass.getSimpleName();
    }
}

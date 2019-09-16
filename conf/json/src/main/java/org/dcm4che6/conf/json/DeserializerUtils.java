package org.dcm4che6.conf.json;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.KeyStoreConfiguration;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;

import javax.json.JsonString;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonParser;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since May 2019
 */
public class DeserializerUtils {
    public static int deserializeInt(JsonParser parser) {
        assertEvent(JsonParser.Event.VALUE_NUMBER, parser.next());
        return parser.getInt();
    }

    public static boolean deserializeBoolean(JsonParser parser) {
        JsonParser.Event event;
        switch(event = parser.next()) {
            case VALUE_TRUE:
                return true;
            case VALUE_FALSE:
                return false;
        }
        throw new JsonbException(String.format("expected event: VALUE_TRUE or VALUE_FALSE but was: %s", event));
    }

    public static String deserializeString(JsonParser parser) {
        assertEvent(JsonParser.Event.VALUE_STRING, parser.next());
        return parser.getString();
    }

    public static Issuer deserializeIssuer(JsonParser parser) {
        return deserializeValue(parser, Issuer::new);
    }

    public static Code deserializeCode(JsonParser parser) {
        return deserializeValue(parser, Code::new);
    }

    public static <T> T deserializeValue(JsonParser parser, Function<String,T> mapper) {
        return mapper.apply(deserializeString(parser));
    }

    public static String[] deserializeStringArray(JsonParser parser) {
        return deserializeArray(parser, Function.identity(), String[]::new);
    }

    public static Code[] deserializeCodeArray(JsonParser parser) {
        return deserializeArray(parser, Code::new, Code[]::new);
    }

    public static <T> T[] deserializeArray(JsonParser parser, Function<String, T> mapper, IntFunction<T[]> generator) {
        assertEvent(JsonParser.Event.START_ARRAY, parser.next());
        return parser.getArrayStream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(mapper)
                .toArray(generator);
    }

    public static void unexpectedKey(String key) {
        throw new JsonbException(String.format("unexpectedKey key: %s", key));
    }

    public static Connection replaceConnection(Connection ref, Device device) {
        return device.getConnection(ref)
                .orElseThrow(() -> new JsonbException(String.format("no such connection: %s", ref)));
    }

    public static List<Connection> replaceConnections(List<Connection> refs, Device device) {
        return refs.stream().map(ref -> replaceConnection(ref, device)).collect(Collectors.toList());
    }

    public static KeyStoreConfiguration replaceKeyStoreConf(KeyStoreConfiguration ks, Device device) {
        return device.getKeyStoreConfiguration(ks.getName())
                .orElseThrow(() -> new JsonbException(String.format("no such keystore: %s", ks.getName())));
    }

    public static void assertEvent(JsonParser.Event expected, JsonParser.Event event) {
        if (expected != event)
            throw new JsonbException(String.format("expected event: %s but was: %s", expected, event));
    }
}

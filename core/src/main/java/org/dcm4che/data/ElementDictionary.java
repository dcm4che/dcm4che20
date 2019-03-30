package org.dcm4che.data;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public abstract class ElementDictionary {
    private static final ServiceLoader<ElementDictionary> loader =
            ServiceLoader.load(ElementDictionary.class);
    private final String privateCreator;
    private final Class<?> tagClass;

    protected ElementDictionary(String privateCreator, Class<?> tagClass) {
        this.privateCreator = privateCreator;
        this.tagClass = tagClass;
    }

    public static ElementDictionary standardElementDictionary() {
        return StandardElementDictionary.INSTANCE;
    }

    public static ElementDictionary elementDictionaryOf(Optional<String> privateCreator) {
        return !privateCreator.isPresent() ? StandardElementDictionary.INSTANCE
                : loader.stream()
                .map(ServiceLoader.Provider::get)
                .filter(x -> privateCreator.equals(x.getPrivateCreator()))
                .findAny()
                .orElse(StandardElementDictionary.INSTANCE);
    }

    public static void reload() {
        synchronized (loader) {
            loader.reload();
        }
    }

    public static VR vrOf(int tag, Optional<String> privateCreator) {
        return elementDictionaryOf(privateCreator).vrOf(tag);
    }

    public static String keywordOf(int tag, Optional<String> privateCreator) {
        return elementDictionaryOf(privateCreator).keywordOf(tag);
    }

    public static int tagForKeyword(String keyword, Optional<String> privateCreatorID) {
        return elementDictionaryOf(privateCreatorID).tagForKeyword(keyword);
    }

    public final String getPrivateCreator() {
        return privateCreator;
    }

    public abstract VR vrOf(int tag);

    public abstract String keywordOf(int tag);

    public int tmTagOf(int daTag) {
        return 0;
    }

    public int daTagOf(int tmTag) {
        return 0;
    }

    public int tagForKeyword(String keyword) {
        if (tagClass != null)
            try {
                return tagClass.getField(keyword).getInt(null);
            } catch (Exception ignore) { }
        return -1;
    }
}

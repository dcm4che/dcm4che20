package org.dcm4che6.conf.ldap;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;
import org.dcm4che6.util.function.ThrowingConsumer;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
public class LdapUtils {

    static final Function<Object, Issuer> issuerMapping =
            ((Function<Object, String>) Object::toString).andThen(Issuer::new);

    static final Function<Object, Code> codeMapping =
            ((Function<Object, String>) Object::toString).andThen(Code::new);

    static final Function<Object, Boolean> booleanMapping =
            ((Function<Object, String>) Object::toString).andThen(Boolean::valueOf);

    static final Function<Object, Integer> intMapping =
            ((Function<Object, String>) Object::toString).andThen(Integer::valueOf);

    static final Function<Object, TransferCapability.Role> roleMapping =
            ((Function<Object, String>) Object::toString).andThen(TransferCapability.Role::valueOf);

    static SearchControls singleResultNoAttributes(int scope) {
        return new SearchControls(scope, 1, 0, new String[0], false, false);
    }

    static void forEach(DirContext ctx, Name name, String objectClass,
            ThrowingConsumer<SearchResult, NamingException> action) throws NamingException {
        NamingEnumeration<SearchResult> search =
                ctx.search(name, new Rdn("objectclass", objectClass).toString(), new SearchControls());
        try {
            while (search.hasMore())
                action.accept(search.next());
        } finally {
            search.close();
        }
    }

    static <T> void ifPresent(DirContext ctx, Name name, Function<Attributes, T> mapping, Consumer<T> action)
            throws NamingException {
        try {
            action.accept(mapping.apply(ctx.getAttributes(name)));
        } catch (NameNotFoundException e) {
        }
    }

    static Rdn rdn(String type, Object value) {
        try {
            return new Rdn(type, value);
        } catch (InvalidNameException e) {
            throw new UncheckedNamingException(e);
        }
    }

    static Rdn rdn(Attributes attrSet) {
        try {
            return new Rdn(attrSet);
        } catch (InvalidNameException e) {
            throw new UncheckedNamingException(e);
        }
    }

    static String valueOf(boolean b) {
        return b ? "TRUE" : "FALSE";
    }

    static <T> void ifPresent(Attributes attrSet, String attrID, Function<Object, T> mapping, Consumer<T> action) {
        Attribute attr = attrSet.get(attrID);
        if (attr != null) {
            try {
                action.accept(mapping.apply(attr.get()));
            } catch (NamingException e) {
                throw new UncheckedNamingException(e);
            }
        }
    }

    static void ifPresent(Attributes attrSet, String attrID, IntConsumer action) throws NamingException {
        Attribute attr = attrSet.get(attrID);
        if (attr != null)
            action.accept(Integer.parseInt(attr.get().toString()));
    }

    static <T> void ifPresent(Attributes attrSet, String attrID, Function<Object, T> mapping, Class<T> clazz,
            Consumer<T[]> action) throws NamingException {
        Attribute attr = attrSet.get(attrID);
        if (attr != null) {
            T[] a = (T[]) Array.newInstance(clazz, attr.size());
            for (int i = 0; i < a.length; i++) {
                a[i] = mapping.apply(attr.get(i));
            }
            action.accept(a);
        }
    }

    static <T> void forEach(Attributes attrSet, String attrID, Function<Object, T> mapping,
            Consumer<T> action) throws NamingException {
        Attribute attr = attrSet.get(attrID);
        if (attr != null) {
            for (int i = 0; i < attr.size(); i++) {
                action.accept(mapping.apply(attr.get(i)));
            }
        }
    }

    static <T> boolean anyMatch(Attributes attrSet, String attrID, Predicate<Object> predicate) {
        Attribute attr = attrSet.get(attrID);
        if (attr != null) {
            try {
                for (int i = 0; i < attr.size(); i++) {
                        if (predicate.test(attr.get(i)))
                            return true;
                }
            } catch (NamingException e) {
                throw new UncheckedNamingException(e);
            }
        }
        return false;
    }

    static boolean hasObjectClass(Attributes attrSet, String objectClass) {
        return anyMatch(attrSet, "objectclass", objectClass::equals);
    }

    static void putBoolean(Attributes attrSet, String attrID, boolean b) {
        attrSet.put(new BasicAttribute(attrID, valueOf(b)));
    }

    static void putBoolean(Attributes attrSet, String attrID, Optional<Boolean> b) {
        b.ifPresent(v -> putValue(attrSet, attrID, v, LdapUtils::valueOf));
    }

    static <T> void putValue(Attributes attrSet, String attrID, Optional<T> optional) {
        optional.ifPresent(v -> putValue(attrSet, attrID, v));
    }

    static void putInt(Attributes attrSet, String attrID, OptionalInt optional) {
        optional.ifPresent(v -> putValue(attrSet, attrID, Integer.toString(v), Function.identity()));
    }

    static <T> void putValue(Attributes attrSet, String attrID, T value) {
        putValue(attrSet, attrID, value, Objects::toString);
    }

    static <T> void putValue(Attributes attrSet, String attrID, T value, Function<T,String> toString) {
        attrSet.put(new BasicAttribute(attrID, toString.apply(value)));
    }

    static <T> void putValues(Attributes attrSet, String attrID, List<T> list) {
        putValues(attrSet, attrID, list, Objects::toString);
    }

    static <T> void putValues(Attributes attrSet, String attrID, List<T> list, Function<T,String> toString) {
        if (!list.isEmpty())
            attrSet.put(newAttribute(attrID, list, toString));
    }

    static <T> void diffOptionalValue(List<ModificationItem> mods, String attrID, Optional<T> a, Optional<T> b) {
        diffOptionalValue(mods, attrID, a, b, Objects::toString);
    }

    static void diffOptionalInt(List<ModificationItem> mods, String attrID, OptionalInt a, OptionalInt b) {
        if (!a.equals(b))
            mods.add(b.isEmpty()
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrID))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute(attrID, Integer.toString(b.getAsInt()))));
    }

    static <T> void diffOptionalValue(List<ModificationItem> mods, String attrID, Optional<T> a, Optional<T> b,
            Function<T,String> toString) {
        if (!a.equals(b))
            mods.add(b.isEmpty()
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrID))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute(attrID, toString.apply(b.get()))));
    }

    static <T> void diffValue(List<ModificationItem> mods, String attrID, T a, T b) {
        diffValue(mods, attrID, a, b, Objects::toString);
    }

    static <T> void diffValue(List<ModificationItem> mods, String attrID, T a, T b, Function<T,String> toString) {
        if (!a.equals(b))
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute(attrID, toString.apply(b))));
    }

    static void diffBoolean(List<ModificationItem> mods, String attrID, boolean a, boolean b) {
        if (a != b)
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(attrID, valueOf(b))));
    }

    static void diffOptionalBoolean(List<ModificationItem> mods, String attrID,
            Optional<Boolean> a, Optional<Boolean> b) {
        diffOptionalValue(mods, attrID, a, b, LdapUtils::valueOf);
    }

    static <T> void diffValues(List<ModificationItem> mods, String attrID, Collection<T> a, Collection<T> b) {
        diffValues(mods, attrID, a, b, Objects::toString);
    }

    static <T> void diffValues(List<ModificationItem> mods, String attrID, Collection<T> a, Collection<T> b,
            Function<T,String> toString) {
        if (!equalsIgnoreOrder(a, b))
            mods.add(diffValues(attrID, a, b, toString));
    }

    static <T> boolean equalsIgnoreOrder(Collection<T> a, Collection<T> b) {
        return a.equals(b)
                || a.size() == b.size() && a.size() > 1 && !(a instanceof Set) && a.stream().allMatch(b::contains);
    }

    private static <T> ModificationItem diffValues(String attrID, Collection<T> a, Collection<T> b,
            Function<T, String> toString) {
        if (b.isEmpty())
            return new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrID));

        Set<T> add = new HashSet(b);
        add.removeAll(a);

        Set<T> remove = new HashSet(a);
        remove.removeAll(b);

        return add.isEmpty()
                ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, newAttribute(attrID, remove, toString))
                : remove.isEmpty()
                ? new ModificationItem(DirContext.ADD_ATTRIBUTE, newAttribute(attrID, add, toString))
                : new ModificationItem(DirContext.REPLACE_ATTRIBUTE, newAttribute(attrID, b, toString));
    }

    static <T> Attribute newAttribute(String attrID, Collection<T> list, Function<T, String> toString) {
        BasicAttribute attr = new BasicAttribute(attrID);
        list.forEach(v -> attr.add(toString.apply(v)));
        return attr;
    }

    static Attributes objectClass(String type) {
        return new BasicAttributes("objectclass", type);
    }

    static void addObjectClass(Attributes attrSet, String type) {
        attrSet.get("objectclass").add(type);
    }

    static Name toName(Rdn[] rdns) {
        return new LdapName(List.of(rdns));
    }

    static Rdn[] cat(Rdn[] prefix, Rdn rdn) {
        Rdn[] result = new Rdn[prefix.length + 1];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        result[prefix.length] = rdn;
        return result;
    }

    static Rdn[] cat(Rdn[] rdns1, Rdn[] rdns2, Rdn rdn) {
        Rdn[] result = new Rdn[rdns1.length + rdns2.length + 1];
        System.arraycopy(rdns1, 0, result, 0, rdns1.length);
        System.arraycopy(rdns2, 0, result, rdns1.length, rdns2.length);
        result[result.length - 1] = rdn;
        return result;
    }

    static <K, T> Map<K, T> toMap(Collection<T> a, Function<? super T, ? extends K> keyMapper) {
        return a.stream().collect(Collectors.toMap(keyMapper, Function.identity()));
    }
}

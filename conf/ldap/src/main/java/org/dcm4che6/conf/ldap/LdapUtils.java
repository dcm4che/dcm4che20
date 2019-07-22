package org.dcm4che6.conf.ldap;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.util.Code;
import org.dcm4che6.util.Issuer;
import org.dcm4che6.util.function.ThrowingConsumer;

import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntConsumer;

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

    static final Function<Object, TransferCapability.Role> roleMapping =
            ((Function<Object, String>) Object::toString).andThen(TransferCapability.Role::valueOf);

    static SearchControls singleResultNoAttributes(int scope) {
        return new SearchControls(scope, 1, 0, new String[0], false, false);
    }

    static SearchControls singleResult() {
        return new SearchControls(SearchControls.ONELEVEL_SCOPE, 1, 0, null, false, false);
    }

    static boolean contains(DirContext ctx, Name name, Rdn rdn) throws NamingException {
        NamingEnumeration<SearchResult> search = ctx.search(name, rdn.toString(),
                singleResultNoAttributes(SearchControls.ONELEVEL_SCOPE));
        try {
            return search.hasMore();
        } finally {
            search.close();
        }
    }

    static Attributes search(DirContext ctx, Name name, Rdn rdn) throws NamingException {
        NamingEnumeration<SearchResult> search = ctx.search(name, rdn.toString(), singleResult());
        try {
            return search.hasMore() ? search.next().getAttributes() : null;
        } finally {
            search.close();
        }
    }

    static void forEach(DirContext ctx, Name name, String objectClass,
            ThrowingConsumer<Attributes, NamingException> action) throws NamingException {
        NamingEnumeration<SearchResult> search =
                ctx.search(name, new Rdn("objectclass", objectClass).toString(), new SearchControls());
        try {
            while (search.hasMore())
                action.accept(search.next().getAttributes());
        } finally {
            search.close();
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

    static Rdn rdnOf(Connection conn) {
        if (conn.getName().isPresent())
            return rdn("cn", conn.getName().get());
        Attributes attrSet = new BasicAttributes("dicomHostname", conn.getHostname());
        putInt(attrSet, "dicomPort", conn.getPort());
        return rdn(attrSet);
    }

    static Rdn rdnOf(TransferCapability tc) {
        if (tc.getName().isPresent())
            return rdn("cn", tc.getName().get());
        Attributes attrSet = new BasicAttributes("dicomSOPClass", tc.getSOPClass());
        putValue(attrSet, "dicomTransferRole", tc.getRole());
        return rdn(attrSet);
    }

    static String dnOf(Connection conn, Rdn[] fullDeviceRdns) {
        return toName(append(fullDeviceRdns, rdnOf(conn))).toString();
    }

    static String valueOf(boolean b) {
        return b ? "TRUE" : "FALSE";
    }

    static <T> T getValue(Attributes attrSet, String attrID, Function<Object, T> mapping) throws NamingException {
        Attribute attr = attrSet.get(attrID);
        return attr != null ? mapping.apply(attr.get()) : null;
    }

    static void getInt(Attributes attrSet, String attrID, IntConsumer action) {
        Attribute attr = attrSet.get(attrID);
        if (attr != null) {
            try {
                action.accept(Integer.parseInt(attr.get().toString()));
            } catch (NamingException e) {
                throw new UncheckedNamingException(e);
            }
        }
    }

    static <T> T[] getArray(Attributes attrSet, String attrID, Function<Object, T> mapping, Class<T> clazz)
            throws NamingException {
        Attribute attr = attrSet.get(attrID);
        T[] a = (T[]) Array.newInstance(clazz, attr != null ? attr.size() : 0);
        for (int i = 0; i < a.length; i++)
            a[i] = mapping.apply(attr.get(i));

        return a;
    }

    static String[] objectClassesOf(Attributes attrSet) throws NamingException {
        return getArray(attrSet, "objectclass", Object::toString, String.class);
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

    static <T> void putList(Attributes attrSet, String attrID, List<T> list) {
        putList(attrSet, attrID, list, Objects::toString);
    }

    static <T> void putList(Attributes attrSet, String attrID, List<T> list, Function<T,String> toString) {
        if (!list.isEmpty()) {
            BasicAttribute attr = new BasicAttribute(attrID);
            list.forEach(v -> attr.add(toString.apply(v)));
            attrSet.put(attr);
        }
    }

    static Attributes objectClass(String type) {
        return new BasicAttributes("objectclass", type);
    }

    static void addObjectClass(Attributes attrSet, String type) {
        attrSet.get("objectclass").add(type);
    }

    static Rdn uncheckedRdn(String type, Object value) {
        try {
            return new Rdn(type, value);
        } catch (InvalidNameException e) {
            throw new UncheckedNamingException(e);
        }
    }

    static Name toName(Rdn[] rdns) {
        return new LdapName(List.of(rdns));
    }

    static Rdn[] append(Rdn[] appendTo, Rdn rdn) {
        Rdn[] result = new Rdn[appendTo.length + 1];
        System.arraycopy(appendTo, 0, result, 0, appendTo.length);
        result[appendTo.length] = rdn;
        return result;
    }

    static Rdn[] cat(Rdn[] rdns1, Rdn[] rdns2) {
        Rdn[] result = new Rdn[rdns1.length + rdns2.length];
        System.arraycopy(rdns1, 0, result, 0, rdns1.length);
        System.arraycopy(rdns2, 0, result, rdns1.length, rdns2.length);
        return result;
    }
}

package org.dcm4che6.internal;

import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.OptionalFloat;
import org.dcm4che6.util.StringUtils;
import org.dcm4che6.util.function.StringValueConsumer;
import org.dcm4che6.data.SpecificCharacterSet;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2018
 */
public enum StringVR implements VRType {
    ASCII("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            null, null, null, null),
    STRING("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, DicomObject::specificCharacterSet,
            null, null, null, null),
    TEXT("\r\n\t\f", VM.SINGLE, StringUtils.Trim.TRAILING, DicomObject::specificCharacterSet,
            null, null, null, null),
    DS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            StringVR::parseDoubleAsInt, Double::parseDouble, Integer::toString, StringVR::doubleToString),
    IS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            Integer::parseInt, StringVR::parseIntAsDouble, Integer::toString, null),
    PN("\\^=", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, DicomObject::specificCharacterSet,
            null, null, null, null),
    UC("\\", VM.MULTI, StringUtils.Trim.TRAILING, StringVR::ascii,
            null, null, null, null),
    UR("", VM.SINGLE, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            null, null, null, null);

    private final String delimiters;
    private final VM vm;
    private final StringUtils.Trim trim;
    private final Function<DicomObject, SpecificCharacterSet> asciiOrCS;
    private final ToIntFunction<String> stringToInt;
    private final ToDoubleFunction<String> stringToDouble;
    private final IntFunction<String> intToString;
    private final DoubleFunction<String> doubleToString;

    StringVR(String delimiters, VM vm, StringUtils.Trim trim,
            Function<DicomObject, SpecificCharacterSet> asciiOrCS,
            ToIntFunction<String> stringToInt, ToDoubleFunction<String> stringToDouble,
            IntFunction<String> intToString, DoubleFunction<String> doubleToString) {
        this.delimiters = delimiters;
        this.vm = vm;
        this.trim = trim;
        this.asciiOrCS = asciiOrCS;
        this.stringToInt = stringToInt;
        this.stringToDouble = stringToDouble;
        this.intToString = intToString;
        this.doubleToString = doubleToString;
    }

    private static SpecificCharacterSet ascii(DicomObject dicomObject) {
        return SpecificCharacterSet.getDefaultCharacterSet();
    }

    @Override
    public String delimiters() {
        return delimiters;
    }

    @Override
    public StringBuilder appendValue(DicomInput input, long valuePos, int valueLen, DicomObject dcmobj,
                                     StringBuilder appendTo, int maxLength) {
        if (valueLen > 0) {
            String str = input.stringAt(valuePos, Math.min(valueLen, maxLength << 1), asciiOrCS.apply(dcmobj));
            int remaining = maxLength - appendTo.length();
            int len = str.length();
            if (len > remaining)
                len = remaining;
            else if (str.charAt(len - 1) <= ' ')
                len--;
            appendTo.append(str.substring(0, len));
        }
        return appendTo;
    }

    @Override
    public Optional<String> stringValue(DicomInput input, long valuePos, int valueLen, int index, DicomObject dcmobj) {
        return stringValue(input.stringAt(valuePos, valueLen, asciiOrCS.apply(dcmobj)), index);
    }

    @Override
    public String[] stringValues(DicomInput input, long valuePos, int valueLen, DicomObject dcmobj) {
        return stringValues(input.stringAt(valuePos, valueLen, asciiOrCS.apply(dcmobj)));
    }

    @Override
    public Optional<String> stringValue(String value, int index) {
        return vm.cut(value, index, trim);
    }

    @Override
    public String[] stringValues(String value) {
        return vm.split(value, trim);
    }

    @Override
    public <E extends Throwable> void forEachStringValue(DicomElement dcmElm, StringValueConsumer<E> action) throws E {
        String[] values = dcmElm.stringValues();
        for (int i = 0; i < values.length;) {
            action.accept(values[i], ++i);
        }
    }

    @Override
    public void forEachIntValue(DicomElement dcmElm, IntConsumer action) {
        if (stringToInt == null)
            return;

        for (String s : dcmElm.stringValues()) {
            action.accept(stringToInt.applyAsInt(s));
        }
    }

    @Override
    public void forEachDoubleValue(DicomElement dcmElm, DoubleConsumer action) {
        if (stringToDouble == null)
            return;

        for (String s : dcmElm.stringValues()) {
            action.accept(stringToDouble.applyAsDouble(s));
        }
    }

    @Override
    public OptionalInt intValue(String value, int index) {
        if (stringToInt == null)
            return OptionalInt.empty();

        return stringValue(value, index).stream().mapToInt(stringToInt).findFirst();
    }

    @Override
    public int[] intValues(String value) {
        if (stringToInt == null)
            return DicomElement.EMPTY_INTS;

        String[] ss = stringValues(value);
        int[] ints = new int[ss.length];
        for (int i = 0; i < ss.length; i++) {
            ints[i] = stringToInt.applyAsInt(ss[i]);
        }
        return ints;
    }

    @Override
    public OptionalFloat floatValue(String value, int index) {
        if (stringToDouble == null)
            return OptionalFloat.empty();

        Optional<String> s = stringValue(value, index);
        return s.isPresent() ? OptionalFloat.of((float) stringToDouble.applyAsDouble(s.get())) : OptionalFloat.empty();
    }

    @Override
    public float[] floatValues(String value) {
        if (stringToDouble == null)
            return DicomElement.EMPTY_FLOATS;

        String[] ss = stringValues(value);
        float[] floats = new float[ss.length];
        for (int i = 0; i < ss.length; i++) {
            floats[i] = (float) stringToDouble.applyAsDouble(ss[i]);
        }
        return floats;
    }

    @Override
    public OptionalDouble doubleValue(String value, int index) {
        if (stringToDouble == null)
            return OptionalDouble.empty();

        Optional<String> s = stringValue(value, index);
        return s.isPresent() ? OptionalDouble.of(stringToDouble.applyAsDouble(s.get())) : OptionalDouble.empty();
    }

    @Override
    public double[] doubleValues(String value) {
        if (stringToDouble == null)
            return DicomElement.EMPTY_DOUBLES;

        String[] ss = stringValues(value);
        double[] doubles = new double[ss.length];
        for (int i = 0; i < ss.length; i++) {
            doubles[i] = stringToDouble.applyAsDouble(ss[i]);
        }
        return doubles;
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, int[] vals) {
        if (intToString == null)
            throw new UnsupportedOperationException();

        String[] ss = new String[vals.length];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = intToString.apply(vals[i]);
        }
        return elementOf(dcmObj, tag, vr, ss);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, float[] vals) {
        if (doubleToString == null)
            throw new UnsupportedOperationException();

        String[] ss = new String[vals.length];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = doubleToString.apply(vals[i]);
        }
        return elementOf(dcmObj, tag, vr, ss);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, double[] vals) {
        if (doubleToString == null)
            throw new UnsupportedOperationException();

        String[] ss = new String[vals.length];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = doubleToString.apply(vals[i]);
        }
        return elementOf(dcmObj, tag, vr, ss);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String val) {
        if (val.isEmpty()) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        return new StringElement(dcmObj, tag, vr, val);
    }

    @Override
    public DicomElement elementOf(DicomObject dcmObj, int tag, VR vr, String[] vals) {
        if (vals.length == 0) {
            return VRType.super.elementOf(dcmObj, tag, vr);
        }
        return elementOf(dcmObj, tag, vr, vm.join(vr, vals));
    }

    private static double parseIntAsDouble(String s) {
        return Integer.parseInt(s);
    }

    private static int parseDoubleAsInt(String s) {
        return (int) Double.parseDouble(s);
    }

    private static String doubleToString(double value) {
        return StringUtils.trimDS(Double.toString(value));
    }

    private static String floatToString(float value, long pos) {
        return StringUtils.trimDS(Float.toString(value));
    }

    enum VM {
        SINGLE {
            @Override
            Optional<String> cut(String s, int index, StringUtils.Trim trim) {
                return index == 0
                        ? StringUtils.optionalOf(StringUtils.trim(s, trim))
                        : Optional.empty();
            }

            @Override
            String[] split(String s, StringUtils.Trim trim) {
                return s.isEmpty()
                        ? StringUtils.EMPTY_STRINGS
                        : new String[]{StringUtils.trim(s, trim)};
            }

            @Override
            String join(VR vr, String[] s) {
                if (s.length == 1) {
                    return s[0];
                }
                throw new IllegalArgumentException(String.format("VR: %s does not allow multiple values", vr));
            }
        },
        MULTI {
            @Override
            Optional<String> cut(String s, int index, StringUtils.Trim trim) {
                return StringUtils.optionalOf(StringUtils.cut(s, s.length(), '\\', index, trim));
            }

            @Override
            String[] split(String s, StringUtils.Trim trim) {
                return StringUtils.split(s, s.length(), '\\', trim);
            }

            @Override
            String join(VR vr, String[] ss) {
                return StringUtils.join(ss, 0, ss.length, '\\');
            }
        };

        abstract Optional<String> cut(String s, int index, StringUtils.Trim trim);

        abstract String[] split(String s, StringUtils.Trim trim);

        abstract String join(VR vr, String[] s);
    }
}

package org.dcm4che.data;

import org.dcm4che.util.StringUtils;

import java.util.Optional;
import java.util.function.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
enum StringVR implements VRType {
    ASCII("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            null, null),
    STRING("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, DicomObject::specificCharacterSet,
            null, null),
    TEXT("\r\n\t\f", VM.SINGLE, StringUtils.Trim.TRAILING, DicomObject::specificCharacterSet,
            null, null),
    DS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            StringVR::parseDoubleAsInt, Double::parseDouble),
    IS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            Integer::parseInt, StringVR::parseIntAsDouble),
    PN("\\^=", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, DicomObject::specificCharacterSet,
            null, null),
    UC("\\", VM.MULTI, StringUtils.Trim.TRAILING, StringVR::ascii,
            null, null),
    UR("", VM.SINGLE, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii,
            null, null);

    private final String delimiters;
    private final VM vm;
    private final StringUtils.Trim trim;
    private final Function<DicomObject,SpecificCharacterSet> asciiOrCS;
    private final ToIntFunction<String> stringToInt;
    private final ToDoubleFunction<String> stringToDouble;

    StringVR(String delimiters, VM vm, StringUtils.Trim trim, Function<DicomObject, SpecificCharacterSet> asciiOrCS,
            ToIntFunction<String> stringToInt, ToDoubleFunction<String> stringToDouble) {
        this.delimiters = delimiters;
        this.vm = vm;
        this.trim = trim;
        this.asciiOrCS = asciiOrCS;
        this.stringToInt = stringToInt;
        this.stringToDouble = stringToDouble;
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
    public <E extends Throwable> void forEach(DicomElement dcmElm, StringValueConsumer<E> action) throws E {
        String[] values = dcmElm.stringValues();
        for (int i = 0; i < values.length;) {
            action.accept(values[i], ++i);
        }
    }

    @Override
    public void forEach(DicomElement dcmElm, IntConsumer action) {
        if (stringToInt == null)
            return;

        for (String s : dcmElm.stringValues()) {
            action.accept(stringToInt.applyAsInt(s));
        }
    }

    @Override
    public void forEach(DicomElement dcmElm, DoubleConsumer action) {
        if (stringToDouble == null)
            return;

        for (String s : dcmElm.stringValues()) {
            action.accept(stringToDouble.applyAsDouble(s));
        }
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

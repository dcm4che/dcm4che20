package org.dcm4che.data;

import org.dcm4che.util.StringUtils;

import java.util.function.UnaryOperator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
enum StringVR implements VRType {
    AE("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    AS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    CS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    DA("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    DS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    DT("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    IS("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    LO("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, UnaryOperator.identity()),
    LT("\r\n\t\f", VM.SINGLE, StringUtils.Trim.TRAILING, UnaryOperator.identity()),
    PN("\\^=", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, UnaryOperator.identity()),
    SH("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, UnaryOperator.identity()),
    ST("\r\n\t\f", VM.SINGLE, StringUtils.Trim.TRAILING, UnaryOperator.identity()),
    TM("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    UC("\\", VM.MULTI, StringUtils.Trim.TRAILING, StringVR::ascii),
    UI("\\", VM.MULTI, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    UR("", VM.SINGLE, StringUtils.Trim.LEADING_AND_TRAILING, StringVR::ascii),
    UT("\r\n\t\f", VM.SINGLE, StringUtils.Trim.TRAILING, UnaryOperator.identity());

    private final String delimiters;
    private final VM vm;
    private final StringUtils.Trim trim;
    private final UnaryOperator<SpecificCharacterSet> asciiOrCS;

    StringVR(String delimiters, VM vm, StringUtils.Trim trim, UnaryOperator<SpecificCharacterSet> asciiOrCS) {
        this.delimiters = delimiters;
        this.vm = vm;
        this.trim = trim;
        this.asciiOrCS = asciiOrCS;
    }

    private static SpecificCharacterSet ascii(SpecificCharacterSet cs) {
        return SpecificCharacterSet.getDefaultCharacterSet();
    }

    @Override
    public String delimiters() {
        return delimiters;
    }

    @Override
    public String stringValue(DicomInput input, long valuePos, int valueLen, int index, SpecificCharacterSet cs,
                              String defaultValue) {
        return stringValue(input.stringAt(valuePos, valueLen, cs), index, defaultValue);
    }

    @Override
    public String[] stringValues(DicomInput input, long valuePos, int valueLen, SpecificCharacterSet cs) {
        return stringValues(input.stringAt(valuePos, valueLen, cs));
    }

    @Override
    public String stringValue(String value, int index, String defaultValue) {
        return vm.cut(value, index, trim, defaultValue);
    }

    @Override
    public String[] stringValues(String value) {
        return vm.split(value, trim);
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

    enum VM {
        SINGLE {
            @Override
            String cut(String s, int index, StringUtils.Trim trim, String defaultValue) {
                return index == 0
                        ? StringUtils.requireNonEmptyElse(StringUtils.trim(s, trim), defaultValue)
                        : defaultValue;
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
            String cut(String s, int index, StringUtils.Trim trim, String defaultValue) {
                return StringUtils.requireNonEmptyElse(
                        StringUtils.cut(s, s.length(), '\\', index, trim), defaultValue);
            }

            @Override
            String[] split(String s, StringUtils.Trim trim) {
                return StringUtils.split(s, s.length(), '\\', trim);
            }

            @Override
            String join(VR vr, String[] ss) {
                return StringUtils.join(ss, '\\');
            }
        };

        abstract String cut(String s, int index, StringUtils.Trim trim, String defaultValue);

        abstract String[] split(String s, StringUtils.Trim trim);

        abstract String join(VR vr, String[] s);
    }
}
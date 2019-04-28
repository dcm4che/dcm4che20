/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che.json;

import org.dcm4che.data.DicomElement;
import org.dcm4che.data.DicomObject;
import org.dcm4che.data.VR;
import org.dcm4che.util.StringUtils;
import org.dcm4che.util.TagUtils;

import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 *
 */
public class JSONReader implements Closeable {

    private final JsonParser parser;
    private DicomObject fmi;
    private Event event;
    private String s;

    public JSONReader(JsonParser parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private Event next() {
        s = null;
        return event = parser.next();
    }

    private String getString() {
        if (s == null)
            s = parser.getString();
        return s;
    }

    private void expect(Event expected) {
        if (this.event != expected)
            throw new JsonParsingException("Unexpected " + event + ", expected " + expected, parser.getLocation());
    }

    private String valueString() {
        next();
        expect(Event.VALUE_STRING);
        return getString();
    }

    public DicomObject readDataset(DicomObject dcmobj) {
        boolean wrappedInArray = next() == Event.START_ARRAY;
        if (wrappedInArray) next();
        expect(Event.START_OBJECT);
        fmi = null;
        next();
        readItem(dcmobj);
        if (wrappedInArray) next();
        return fmi;
    }

    public void readDatasets(BiConsumer<DicomObject, DicomObject> callback) {
        next();
        expect(Event.START_ARRAY);
        DicomObject dcmobj;
        while (next() == Event.START_OBJECT) {
            fmi = null;
            dcmobj = DicomObject.newDicomObject();
            next();
            readItem(dcmobj);
            callback.accept(fmi, dcmobj);
        }
        expect(Event.END_ARRAY);
    }

    private DicomObject readItem(DicomObject dcmobj) {
        while (event == Event.KEY_NAME) {
            readAttribute(dcmobj);
            next();
        }
        expect(Event.END_OBJECT);
        return dcmobj;
    }

    private void readAttribute(DicomObject dcmobj) {
        int tag = (int) Long.parseLong(getString(), 16);
        if (TagUtils.isFileMetaInformation(tag)) {
            if (fmi == null)
                fmi = DicomObject.newDicomObject();
            dcmobj = fmi;
        }
        next();
        expect(Event.START_OBJECT);
        Element el = new Element();
        while (next() == Event.KEY_NAME) {
            switch (getString()) {
                case "vr":
                    try {
                        el.vr = VR.valueOf(valueString());
                    } catch (IllegalArgumentException e) {
                        throw new JsonParsingException("Invalid vr: " + getString(), parser.getLocation());
                    }
                    break;
                case "Value":
                    el.values = readValues();
                    break;
                case "InlineBinary":
                    el.bytes = readInlineBinary();
                    break;
                case "BulkDataURI":
                    el.bulkDataURI = valueString();
                    break;
                default:
                    throw new JsonParsingException("Unexpected \"" + getString() + "\", expected \"Value\" or " +
                            "\"InlineBinary\" or \"BulkDataURI\"", parser.getLocation());
            }
        }
        expect(Event.END_OBJECT);
        if (el.vr == null)
            throw new JsonParsingException("Missing property: vr", parser.getLocation());

        if (el.isEmpty())
            dcmobj.setNull(tag, el.vr);
        else if (el.bulkDataURI != null) {
            dcmobj.setBulkData(tag, el.vr, el.bulkDataURI, null);
        } else switch (el.vr) {
            case AE:
            case AS:
            case AT:
            case CS:
            case DA:
            case DS:
            case DT:
            case LO:
            case LT:
            case PN:
            case IS:
            case SH:
            case ST:
            case TM:
            case UC:
            case UI:
            case UR:
            case UT:
                dcmobj.setString(tag, el.vr, el.toStrings());
                break;
            case FL:
            case FD:
                dcmobj.setDouble(tag, el.vr, el.toDoubles());
                break;
            case SL:
            case SS:
            case UL:
            case US:
                dcmobj.setInt(tag, el.vr, el.toInts());
                break;
            case SQ:
                el.toItems(dcmobj.newDicomSequence(tag));
                break;
            case OB:
            case OD:
            case OF:
            case OL:
            case OW:
            case UN:
                dcmobj.setBytes(tag, el.vr, el.bytes);
        }
    }

    private List<Object> readValues() {
        ArrayList<Object> list = new ArrayList<>();
        next();
        expect(Event.START_ARRAY);
        while (next() != Event.END_ARRAY) {
            switch (event) {
                case START_OBJECT:
                    list.add(readItemOrPersonName());
                    break;
                case VALUE_STRING:
                    list.add(parser.getString());
                    break;
                case VALUE_NUMBER:
                    list.add(parser.getBigDecimal());
                    break;
                case VALUE_NULL:
                    list.add(null);
                    break;
                default:
                    throw new JsonParsingException("Unexpected " + event, parser.getLocation());
            }
        }
        return list;
    }

    private Object readItemOrPersonName() {
        if (next() != Event.KEY_NAME)
            return null;

        return (getString().length() == 8)
                ? readItem(DicomObject.newDicomObject())
                : readPersonName();
    }

    private String readPersonName() {
        int len = 0;
        String[] ss = new String[3];
        while (event == Event.KEY_NAME) {
            switch (getString()) {
                case "Alphabetic":
                    ss[0] = valueString();
                    if (len < 1) len = 1;
                    break;
                case "Ideographic":
                    ss[1] = valueString();
                    if (len < 2) len = 2;
                    break;
                case "Phonetic":
                    ss[2] = valueString();
                    if (len < 3) len = 3;
                    break;
                default:
                    throw new JsonParsingException("Unexpected \"" + getString()
                            + "\", expected \"Alphabetic\" or \"Ideographic\""
                            + " or \"Phonetic\"", parser.getLocation());
            }
            next();
        }
        expect(Event.END_OBJECT);
        return StringUtils.join(ss, 0, len, '=');
    }

    private byte[] readInlineBinary() {
        return Base64.getDecoder().decode(valueString());
    }

    private static class Element {
        VR vr;
        List<Object> values;
        byte[] bytes;
        String bulkDataURI;

        boolean isEmpty() {
            return (values == null || values.isEmpty()) && (bytes == null || bytes.length == 0) && bulkDataURI == null;
        }

        String[] toStrings() {
            String[] ss = new String[values.size()];
            for (int i = 0; i < ss.length; i++) {
                Object value = values.get(i);
                ss[i] = value != null ? value.toString() : null;
            }
            return ss;
        }

        double[] toDoubles() {
            double[] ds = new double[values.size()];
            for (int i = 0; i < ds.length; i++) {
                Number number = (Number) values.get(i);
                double d;
                if (number == null) {
                    d = Double.NaN;
                } else {
                    d = number.doubleValue();
                    if (d == -Double.MAX_VALUE) {
                        d = Double.NEGATIVE_INFINITY;
                    } else if (d == Double.MAX_VALUE) {
                        d = Double.POSITIVE_INFINITY;
                    }
                }
                ds[i] = d;
            }
            return ds;
        }

        int[] toInts() {
            int[] is = new int[values.size()];
            for (int i = 0; i < is.length; i++) {
                is[i] = ((Number) values.get(i)).intValue();
            }
            return is;
        }

        void toItems(DicomElement seq) {
            for (Object value : values) {
                seq.addItem(value != null ? (DicomObject) value : DicomObject.newDicomObject());
            }
        }

    }
}

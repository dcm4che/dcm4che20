package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.dcm4che6.util.TagUtils;
import org.dcm4che6.util.UIDUtils;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public enum Dimse {
    C_STORE_RSP (0x8001, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    C_STORE_RQ (0x0001, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageID,
            Status.SOPclassNotSupported, C_STORE_RSP, Association::onDimseRQ),
    C_GET_RSP (0x8010, Tag.AffectedSOPClassUID, 0, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    C_GET_RQ (0x0010, Tag.AffectedSOPClassUID, 0, Tag.MessageID,
            Status.SOPclassNotSupported, C_GET_RSP, Association::onDimseRQ),
    C_FIND_RSP (0x8020, Tag.AffectedSOPClassUID, 0, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    C_FIND_RQ (0x0020, Tag.AffectedSOPClassUID, 0, Tag.MessageID,
            Status.SOPclassNotSupported, C_FIND_RSP, Association::onDimseRQ),
    C_MOVE_RSP (0x8021, Tag.AffectedSOPClassUID, 0, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    C_MOVE_RQ (0x0021, Tag.AffectedSOPClassUID, 0, Tag.MessageID,
            Status.SOPclassNotSupported, C_MOVE_RSP, Association::onDimseRQ),
    C_ECHO_RSP (0x8030, Tag.AffectedSOPClassUID, 0, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    C_ECHO_RQ (0x0030, Tag.AffectedSOPClassUID, 0, Tag.MessageID,
            Status.SOPclassNotSupported, C_ECHO_RSP, Association::onDimseRQ),
    N_EVENT_REPORT_RSP (0x8100, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_EVENT_REPORT_RQ (0x0100, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_EVENT_REPORT_RSP, Association::onDimseRQ),
    N_GET_RSP (0x8110, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_GET_RQ (0x0110, Tag.RequestedSOPClassUID, Tag.RequestedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_GET_RSP, Association::onDimseRQ),
    N_SET_RSP (0x8120, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_SET_RQ (0x0120, Tag.RequestedSOPClassUID, Tag.RequestedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_SET_RSP, Association::onDimseRQ),
    N_ACTION_RSP (0x8130, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_ACTION_RQ (0x0130, Tag.RequestedSOPClassUID, Tag.RequestedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_ACTION_RSP, Association::onDimseRQ),
    N_CREATE_RSP (0x8140, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_CREATE_RQ (0x0140, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_CREATE_RSP, Association::onDimseRQ),
    N_DELETE_RSP (0x8150, Tag.AffectedSOPClassUID, Tag.AffectedSOPInstanceUID, Tag.MessageIDBeingRespondedTo,
            0, null, AbstractDimseHandler.onDimseRSP::accept),
    N_DELETE_RQ (0x0150, Tag.RequestedSOPClassUID, Tag.RequestedSOPInstanceUID, Tag.MessageID,
            Status.NoSuchSOPclass, N_CREATE_RSP, Association::onDimseRQ),
    C_CANCEL_RQ (0x0FFF, 0, 0, Tag.MessageIDBeingRespondedTo,
            0, null, Association::onCancelRQ);

    static int WITH_DATASET = 0;
    public static int NO_DATASET = 0x0101;
    public final int commandField;
    public final int tagOfSOPClassUID;
    public final int tagOfSOPInstanceUID;
    public final int tagOfMessageID;
    public final int noSuchSOPClass;
    public final Dimse rsp;
    public final DimseHandler handler;

    Dimse(int commandField, int tagOfSOPClassUID, int tagOfSOPInstanceUID, int tagOfMessageID, int noSuchSOPClass, Dimse rsp,
            DimseHandler handler) {
        this.commandField = commandField;
        this.tagOfSOPClassUID = tagOfSOPClassUID;
        this.tagOfSOPInstanceUID = tagOfSOPInstanceUID;
        this.tagOfMessageID = tagOfMessageID;
        this.noSuchSOPClass = noSuchSOPClass;
        this.rsp = rsp;
        this.handler = handler;
    }

    public static Dimse of(DicomObject commandSet) {
        int commandField = commandSet.getIntOrElseThrow(Tag.CommandField);
        switch (commandField) {
            case 0x8001:
                return C_STORE_RSP;
            case 0x0001:
                return C_STORE_RQ;
            case 0x8010:
                return C_GET_RSP;
            case 0x0010:
                return C_GET_RQ;
            case 0x8020:
                return C_FIND_RSP;
            case 0x0020:
                return C_FIND_RQ;
            case 0x8021:
                return C_MOVE_RSP;
            case 0x0021:
                return C_MOVE_RQ;
            case 0x8030:
                return C_ECHO_RSP;
            case 0x0030:
                return C_ECHO_RQ;
            case 0x8100:
                return N_EVENT_REPORT_RSP;
            case 0x0100:
                return N_EVENT_REPORT_RQ;
            case 0x8110:
                return N_GET_RSP;
            case 0x0110:
                return N_GET_RQ;
            case 0x8120:
                return N_SET_RSP;
            case 0x0120:
                return N_SET_RQ;
            case 0x8130:
                return N_ACTION_RSP;
            case 0x0130:
                return N_ACTION_RQ;
            case 0x8140:
                return N_CREATE_RSP;
            case 0x0140:
                return N_CREATE_RQ;
            case 0x8150:
                return N_DELETE_RSP;
            case 0x0150:
                return N_DELETE_RQ;
            case 0x0FFF:
                return C_CANCEL_RQ;
        }
        throw new IllegalArgumentException(
                String.format("Invalid Command Field (0000,0100): %4XH", commandField));
    }

    DicomObject mkRQ(int msgID, String sopClassUID, String sopInstanceUID, int dataSetType) {
        DicomObject commandSet = DicomObject.newDicomObject();
        commandSet.setString(tagOfSOPClassUID, VR.UI, sopClassUID);
        commandSet.setInt(Tag.CommandField, VR.US, commandField);
        commandSet.setInt(Tag.MessageID, VR.US, msgID);
        commandSet.setInt(Tag.CommandDataSetType, VR.US, dataSetType);
        if (tagOfSOPInstanceUID != 0)
            commandSet.setString(tagOfSOPInstanceUID, VR.UI, sopInstanceUID);
        return commandSet;
    }

    public DicomObject mkRSP(DicomObject commandSet) {
        return mkRSP(commandSet, NO_DATASET, Status.Success);
    }

    public DicomObject mkRSP(DicomObject rq, int dataSetType, int status) {
        DicomObject commandSet = DicomObject.newDicomObject();
        commandSet.setString(Tag.AffectedSOPClassUID, VR.UI, rq.getStringOrElseThrow(tagOfSOPClassUID));
        commandSet.setInt(Tag.CommandField, VR.US, rsp.commandField);
        commandSet.setInt(Tag.MessageIDBeingRespondedTo, VR.US, rq.getIntOrElseThrow(Tag.MessageID));
        commandSet.setInt(Tag.CommandDataSetType, VR.US, dataSetType);
        commandSet.setInt(Tag.Status, VR.US, status);
        if (tagOfSOPInstanceUID != 0)
            commandSet.setString(Tag.AffectedSOPInstanceUID, VR.UI, rq.getStringOrElseThrow(tagOfSOPInstanceUID));
        return commandSet;
    }

    static boolean hasDataSet(DicomObject commandSet) {
        return commandSet.getIntOrElseThrow(Tag.CommandDataSetType) != NO_DATASET;
    }

    public Object toString(Byte pcid, DicomObject commandSet, String tsuid) {
        return new Object(){
            @Override
            public String toString() {
                return promptTo(pcid, commandSet, tsuid, new StringBuilder(256)).toString();
            }
        };
    }

    private StringBuilder promptTo(Byte pcid, DicomObject commandSet, String tsuid, StringBuilder sb) {
        promptHeaderTo(commandSet, sb);
        sb.append("[pcid: ").append(pcid & 0xff);
        commandSet.getInt(Tag.Status)
                .ifPresent(status -> sb
                        .append(", status: ")
                        .append(Integer.toHexString(status))
                        .append('H'));
        commandSet.getString(tagOfSOPClassUID)
                .ifPresent(uid -> UIDUtils.promptTo(uid, sb
                        .append(System.lineSeparator())
                        .append("  sop-class: ")));
        if (tagOfSOPInstanceUID != 0) {
            commandSet.getString(tagOfSOPInstanceUID)
                    .ifPresent(uid -> UIDUtils.promptTo(uid, sb
                            .append(System.lineSeparator())
                            .append("  sop-instance: ")));
        }
        UIDUtils.promptTo(tsuid, sb
                        .append(System.lineSeparator())
                        .append("  transfer-syntax: "));
        return sb.append(']');
    }

    private StringBuilder promptHeaderTo(DicomObject commandSet, StringBuilder sb) {
        return sb.append(commandSet.getIntOrElseThrow(tagOfMessageID))
                .append(':')
                .append(name().replace('_', '-'));
    }
}

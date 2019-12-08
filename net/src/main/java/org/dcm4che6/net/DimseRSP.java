package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2019
 */
public class DimseRSP {
    public final Dimse dimse;
    public final DicomObject command;
    public final DicomObject data;

    public DimseRSP(Dimse dimse, DicomObject command, DicomObject data) {
        this.dimse = dimse;
        this.command = command;
        this.data = data;
    }
}

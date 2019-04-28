package org.dcm4che.internal;

import org.dcm4che.data.DicomObject;
import org.dcm4che.data.VR;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
public enum SequenceVR implements VRType {
    SQ {
        @Override
        public DicomElementImpl elementOf(DicomObject dcmObj, int tag, VR vr) {
            return new DicomSequence(dcmObj, tag);
        }
    }
}

package org.dcm4che.data;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
enum SequenceVR implements VRType {
    SQ {
        @Override
        public BaseDicomElement elementOf(DicomObject dcmObj, int tag, VR vr) {
            return new DicomSequence(dcmObj, tag);
        }
    }
}

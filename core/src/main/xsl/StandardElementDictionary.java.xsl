<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text"></xsl:output>
  <xsl:template match="/elements">
    <xsl:text>
package org.dcm4che.data;

/**
 * @author Gunter Zeilinger &lt;gunterze@gmail.com&gt;
 */
public class StandardElementDictionary extends ElementDictionary {

    public static final ElementDictionary INSTANCE =
            new StandardElementDictionary();

    private StandardElementDictionary() {
        super(null, Tag.class);
    }

    @Override
    public String keywordOf(int tag) {
        return Keyword.valueOf(tag);
    }

    @Override
    public int tmTagOf(int daTag) {
        switch (daTag) {
        case Tag.InstanceCreationDate:
            return Tag.InstanceCreationTime;
        case Tag.StudyDate:
            return Tag.StudyTime;
        case Tag.SeriesDate:
            return Tag.SeriesTime;
        case Tag.AcquisitionDate:
            return Tag.AcquisitionTime;
        case Tag.ContentDate:
            return Tag.ContentTime;
        case Tag.OverlayDate:
            return Tag.OverlayTime;
        case Tag.CurveDate:
            return Tag.CurveTime;
        case Tag.PatientBirthDate:
            return Tag.PatientBirthTime;
        case Tag.DateOfSecondaryCapture:
            return Tag.TimeOfSecondaryCapture;
        case Tag.DateOfLastCalibration:
            return Tag.TimeOfLastCalibration;
        case Tag.DateOfLastDetectorCalibration:
            return Tag.TimeOfLastDetectorCalibration;
        case Tag.ModifiedImageDate:
            return Tag.ModifiedImageTime;
        case Tag.StudyVerifiedDate:
            return Tag.StudyVerifiedTime;
        case Tag.StudyReadDate:
            return Tag.StudyReadTime;
        case Tag.ScheduledStudyStartDate:
            return Tag.ScheduledStudyStartTime;
        case Tag.ScheduledStudyStopDate:
            return Tag.ScheduledStudyStopTime;
        case Tag.StudyArrivalDate:
            return Tag.StudyArrivalTime;
        case Tag.StudyCompletionDate:
            return Tag.StudyCompletionTime;
        case Tag.ScheduledAdmissionDate:
            return Tag.ScheduledAdmissionTime;
        case Tag.ScheduledDischargeDate:
            return Tag.ScheduledDischargeTime;
        case Tag.AdmittingDate:
            return Tag.AdmittingTime;
        case Tag.DischargeDate:
            return Tag.DischargeTime;
        case Tag.ScheduledProcedureStepStartDate:
            return Tag.ScheduledProcedureStepStartTime;
        case Tag.ScheduledProcedureStepEndDate:
            return Tag.ScheduledProcedureStepEndTime;
        case Tag.PerformedProcedureStepStartDate:
            return Tag.PerformedProcedureStepStartTime;
        case Tag.PerformedProcedureStepEndDate:
            return Tag.PerformedProcedureStepEndTime;
        case Tag.IssueDateOfImagingServiceRequest:
            return Tag.IssueTimeOfImagingServiceRequest;
        case Tag.Date:
            return Tag.Time;
        case Tag.PresentationCreationDate:
            return Tag.PresentationCreationTime;
        case Tag.CreationDate:
            return Tag.CreationTime;
        case Tag.StructureSetDate:
            return Tag.StructureSetTime;
        case Tag.TreatmentControlPointDate:
            return Tag.TreatmentControlPointTime;
        case Tag.SafePositionExitDate:
            return Tag.SafePositionExitTime;
        case Tag.SafePositionReturnDate:
            return Tag.SafePositionReturnTime;
        case Tag.TreatmentDate:
            return Tag.TreatmentTime;
        case Tag.RTPlanDate:
            return Tag.RTPlanTime;
        case Tag.SourceStrengthReferenceDate:
            return Tag.SourceStrengthReferenceTime;
        case Tag.ReviewDate:
            return Tag.ReviewTime;
        case Tag.InterpretationRecordedDate:
            return Tag.InterpretationRecordedTime;
        case Tag.InterpretationTranscriptionDate:
            return Tag.InterpretationTranscriptionTime;
        case Tag.InterpretationApprovalDate:
            return Tag.InterpretationApprovalTime;
        }
        return 0;
    }

    @Override
    public int daTagOf(int tmTag) {
        switch (tmTag) {
        case Tag.InstanceCreationTime:
            return Tag.InstanceCreationDate;
        case Tag.StudyTime:
            return Tag.StudyDate;
        case Tag.SeriesTime:
            return Tag.SeriesDate;
        case Tag.AcquisitionTime:
            return Tag.AcquisitionDate;
        case Tag.ContentTime:
            return Tag.ContentDate;
        case Tag.OverlayTime:
            return Tag.OverlayDate;
        case Tag.CurveTime:
            return Tag.CurveDate;
        case Tag.PatientBirthTime:
            return Tag.PatientBirthDate;
        case Tag.TimeOfSecondaryCapture:
            return Tag.DateOfSecondaryCapture;
        case Tag.TimeOfLastCalibration:
            return Tag.DateOfLastCalibration;
        case Tag.TimeOfLastDetectorCalibration:
            return Tag.DateOfLastDetectorCalibration;
        case Tag.ModifiedImageTime:
            return Tag.ModifiedImageDate;
        case Tag.StudyVerifiedTime:
            return Tag.StudyVerifiedDate;
        case Tag.StudyReadTime:
            return Tag.StudyReadDate;
        case Tag.ScheduledStudyStartTime:
            return Tag.ScheduledStudyStartDate;
        case Tag.ScheduledStudyStopTime:
            return Tag.ScheduledStudyStopDate;
        case Tag.StudyArrivalTime:
            return Tag.StudyArrivalDate;
        case Tag.StudyCompletionTime:
            return Tag.StudyCompletionDate;
        case Tag.ScheduledAdmissionTime:
            return Tag.ScheduledAdmissionDate;
        case Tag.ScheduledDischargeTime:
            return Tag.ScheduledDischargeDate;
        case Tag.AdmittingTime:
            return Tag.AdmittingDate;
        case Tag.DischargeTime:
            return Tag.DischargeDate;
        case Tag.ScheduledProcedureStepStartTime:
            return Tag.ScheduledProcedureStepStartDate;
        case Tag.ScheduledProcedureStepEndTime:
            return Tag.ScheduledProcedureStepEndDate;
        case Tag.PerformedProcedureStepStartTime:
            return Tag.PerformedProcedureStepStartDate;
        case Tag.PerformedProcedureStepEndTime:
            return Tag.PerformedProcedureStepEndDate;
        case Tag.IssueTimeOfImagingServiceRequest:
            return Tag.IssueDateOfImagingServiceRequest;
        case Tag.Time:
            return Tag.Date;
        case Tag.PresentationCreationTime:
            return Tag.PresentationCreationDate;
        case Tag.CreationTime:
            return Tag.CreationDate;
        case Tag.StructureSetTime:
            return Tag.StructureSetDate;
        case Tag.TreatmentControlPointTime:
            return Tag.TreatmentControlPointDate;
        case Tag.SafePositionExitTime:
            return Tag.SafePositionExitDate;
        case Tag.SafePositionReturnTime:
            return Tag.SafePositionReturnDate;
        case Tag.TreatmentTime:
            return Tag.TreatmentDate;
        case Tag.RTPlanTime:
            return Tag.RTPlanDate;
        case Tag.SourceStrengthReferenceTime:
            return Tag.SourceStrengthReferenceDate;
        case Tag.ReviewTime:
            return Tag.ReviewDate;
        case Tag.InterpretationRecordedTime:
            return Tag.InterpretationRecordedDate;
        case Tag.InterpretationTranscriptionTime:
            return Tag.InterpretationTranscriptionDate;
        case Tag.InterpretationApprovalTime:
            return Tag.InterpretationApprovalDate;
        }
        return 0;
    }

    @Override
    public VR vrOf(int tag) {
        if ((tag &amp; 0x0000FFFF) == 0)
            return VR.UL;
        if ((tag &amp; 0x00010000) != 0)
            return ((tag &amp; 0x0000FF00) == 0
                    &amp;&amp; (tag &amp; 0x000000F0) != 0)
                  ? VR.LO
                  : VR.UN;
        if ((tag &amp; 0xFFFFFF00) == Tag.SourceImageIDs)
            return VR.CS;
        int tmp = tag &amp; 0xFFE00000;
        if (tmp == 0x50000000 || tmp == 0x60000000)
            tag &amp;= 0xFFE0FFFF;
        else if ((tag &amp; 0xFF000000) == 0x7F000000
                &amp;&amp; (tag &amp; 0xFFFF0000) != 0x7FE00000)
            tag &amp;= 0xFF00FFFF;
        switch (tag) {</xsl:text>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='AE']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='AS']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='AT']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='CS' and @keyword!='SourceImageIDs']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='DA']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='DS']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='DT']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='FL']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='FD']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='IS']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='LO']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='LT']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='OB']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='OD']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='OF']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='OL']"/>
    <xsl:apply-templates select="//el[@keyword!='' and contains(@vr,'OW')]">
        <xsl:with-param name="vr">OW</xsl:with-param>
    </xsl:apply-templates>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='PN']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='SH']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='SL']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='SQ']"/>
    <xsl:apply-templates select="//el[@keyword!='' and contains(@vr,'SS') and not(contains(@vr,'OW'))]">
        <xsl:with-param name="vr">SS</xsl:with-param>
    </xsl:apply-templates>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='ST']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='TM']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='UC']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='UI']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='UL']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='UR']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='US']"/>
    <xsl:apply-templates select="//el[@keyword!='' and @vr='UT']"/>
<xsl:text>
        }
        return VR.UN;
    }
}
</xsl:text>
  </xsl:template>

  <xsl:template match="el">
    <xsl:param name="vr" select="@vr" />
    <xsl:if test="not(starts-with(@tag,'002804x'))">
      <xsl:text>
        case Tag.</xsl:text>
      <xsl:value-of select="@keyword"/>
      <xsl:text>:</xsl:text>
    </xsl:if>
    <xsl:if test="position()=last()">
      <xsl:text>
           return VR.</xsl:text>
      <xsl:value-of select="$vr"/>
      <xsl:text>;</xsl:text>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
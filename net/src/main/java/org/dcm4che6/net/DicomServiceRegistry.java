package org.dcm4che6.net;

import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.StandardElementDictionary;
import org.dcm4che6.data.UID;
import org.dcm4che6.util.TagUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Nov 2019
 */
public class DicomServiceRegistry implements DimseHandler {
    private final Map<String, DimseHandler> map = new HashMap<>();
    private DimseHandler defaultRQHandler = (as, pcid, dimse, commandSet, dataStream) -> {
        throw new DicomServiceException(dimse.noSuchSOPClass);
    };

    public DicomServiceRegistry() {
        map.put(UID.VerificationSOPClass, new AbstractDimseHandler(dimse -> dimse == Dimse.C_ECHO_RQ) {
            @Override
            protected void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, DicomObject dataSet)
                    throws IOException {
                as.writeDimse(pcid, Dimse.C_ECHO_RSP, dimse.mkRSP(commandSet));
            }
        });
    }

    @Override
    public void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream) throws IOException {
        handlerOf(as, commandSet.getStringOrElseThrow(dimse.tagOfSOPClassUID))
                .accept(as, pcid, dimse, commandSet, dataStream);
    }

    private DimseHandler handlerOf(Association as, String cuid) {
        DimseHandler handler = map.get(cuid);
        if (handler != null) {
            return handler;
        }
        AAssociate.CommonExtendedNegotation commonExtendedNegotation = as.commonExtendedNegotationFor(cuid);
        if (commonExtendedNegotation != null) {
            Optional<DimseHandler> first = commonExtendedNegotation.relatedSOPClassesStream()
                    .map(map::get)
                    .filter(Objects::nonNull)
                    .findFirst();
            if (first.isPresent()) {
                return first.get();
            }
            handler = map.get(commonExtendedNegotation.serviceClass);
            if (handler != null) {
                return handler;
            }
        }
        return defaultRQHandler;
    }

}

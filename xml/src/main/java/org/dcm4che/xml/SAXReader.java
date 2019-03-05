package org.dcm4che.xml;

import org.dcm4che.data.DicomObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
public class SAXReader {
    public static final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    public static DicomObject parse(String uri, DicomObject dicomObject)
            throws ParserConfigurationException, SAXException, IOException {
        SAXHandler dh = new SAXHandler(dicomObject);
        saxParserFactory.newSAXParser().parse(uri, dh);
        return dh.getFileMetaInformation();
    }

    public static DicomObject parse(InputStream is, DicomObject dicomObject)
            throws ParserConfigurationException, SAXException, IOException {
        SAXHandler dh = new SAXHandler(dicomObject);
        saxParserFactory.newSAXParser().parse(is, dh);
        return dh.getFileMetaInformation();
    }
}

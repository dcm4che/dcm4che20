package org.dcm4che.io;

import org.dcm4che.data.*;
import org.dcm4che.internal.*;

import java.io.*;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class DicomInputStream extends InputStream {
    private final DicomParser parser;
    private DicomObject fmi;

    public DicomInputStream(InputStream in) {
        this.parser = new DicomParser(this, in);
    }

    public DicomEncoding getEncoding() {
        return parser.getEncoding();
    }

    public DicomInputStream withEncoding(DicomEncoding encoding) throws IOException {
        parser.setEncoding(encoding);
        return this;
    }

    public DicomInputStream withLimit(int limit) throws IOException {
        parser.setLimit(limit);
        return this;
    }

    public DicomInputStream withParseItems(Predicate<DicomElement> parseItemsPredicate) {
        parser.setParseItems(parseItemsPredicate);
        return this;
    }

    public DicomInputStream withParseItemsLazy(int seqTag) {
        return withParseItems(x -> x.tag() != seqTag);
    }

    public DicomInputStream withBulkData(Predicate<DicomElement> bulkDataPredicate) {
        parser.setBulkData(bulkDataPredicate);
        return this;
    }

    public DicomInputStream withBulkDataURIProducer(Function<DicomInputStream, String> bulkDataURIProducer) {
        parser.setBulkDataURIProducer(bulkDataURIProducer);
        return this;
    }

    public DicomInputStream withBulkDataURI(Path sourcePath) {
        return withBulkDataURIProducer(x -> x.parser.bulkDataURI(sourcePath));
    }

    public DicomInputStream withInputHandler(DicomInputHandler handler) {
        parser.setInputHandler(handler);
        return this;
    }

    public DicomInputHandler getInputHandler() {
        return parser.getInputHandler();
    }

    public DicomInputStream spoolBulkDataTo(Path path) {
        return spoolBulkData(() -> path);
    }

    public DicomInputStream spoolBulkData(Supplier<Path> bulkDataSpoolPathSupplier) {
        parser.setBulkDataSpoolPathSupplier(bulkDataSpoolPathSupplier);
        parser.setBulkDataURIProducer(DicomInputStream::bulkDataSpoolPathURI);
        return this;
    }

    public long getStreamPosition() {
        return parser.getStreamPosition();
    }

    @Override
    public int read() throws IOException {
        return parser.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return parser.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    public DicomObject readFileMetaInformation() throws IOException {
        return fmi = parser.readFileMetaInformation();
    }

    public DicomObject getFileMetaInformation() {
        return fmi;
    }

    public DicomObject readCommandSet() throws IOException {
        return parser.readCommandSet();
    }

    public DicomObject readDataSet() throws IOException {
        return parser.readDataSet();
    }

    public boolean readDataSet(DicomObject dcmObj) throws IOException {
        return parser.readDataSet(dcmObj);
    }

    public StringBuilder promptFilePreambleTo(StringBuilder appendTo, int maxLength) {
        return parser.promptFilePreambleTo(appendTo, maxLength);
    }

    public StringBuilder promptTo(DicomElement dcmElm, StringBuilder appendTo, int maxLength)
            throws IOException {
        return parser.promptTo(dcmElm, appendTo, maxLength);
    }

    public StringBuilder promptTo(DataFragment dataFragment, StringBuilder appendTo, int maxLength)
            throws IOException {
        return parser.promptTo(dataFragment, appendTo, maxLength);
    }

    public void loadValueFromStream() throws IOException {
        parser.loadValueFromStream();
    }

    public void writeValueTo(DicomElement dcmElm, DicomOutputStream dos) throws IOException {
        parser.writeValueTo(dcmElm, dos);
    }

    public void skipBytes(int off, int length, OutputStream out) throws IOException {
        parser.skipBytes(off, length, out);
    }

    private String bulkDataSpoolPathURI() {
        return parser.bulkDataSpoolPathURI();
    }

    public String bulkDataURI() throws IOException {
        return parser.bulkDataURI();
    }

    public static boolean isBulkData(DicomElement el) {
        switch (el.tag()) {
            case Tag.PixelData:
            case Tag.FloatPixelData:
            case Tag.DoubleFloatPixelData:
            case Tag.SpectroscopyData:
            case Tag.EncapsulatedDocument:
                return !el.containedBy().hasParent();
            case Tag.WaveformData:
                return isWaveformSequenceItem(el.containedBy());
        }
        switch (el.tag() & 0xFF00FFFF) {
            case Tag.AudioSampleData:
            case Tag.CurveData:
            case Tag.OverlayData:
                return !el.containedBy().hasParent();
        }
        return false;
    }

    private static boolean isWaveformSequenceItem(DicomObject item) {
        return item.containedBy()
                .filter(seq -> seq.tag() == Tag.WaveformSequence && !seq.containedBy().hasParent())
                .isPresent();
    }
}

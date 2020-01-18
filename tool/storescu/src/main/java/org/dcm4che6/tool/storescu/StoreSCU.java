package org.dcm4che6.tool.storescu;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.DicomElement;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.io.DicomInputHandler;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.TCPConnector;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.PhantomReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2019
 */
@CommandLine.Command(
        name = "storescu",
        mixinStandardHelpOptions = true,
        versionProvider = StoreSCU.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The storescu application implements a Service Class User (SCU) for the Storage SOP Class.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ storescu --called DCM4CHEE localhost 11112 image.dcm",
                "Sends image.dcm to Application Entity DCM4CHEE listening on port 11112 at localhost" }
)
public class StoreSCU implements Callable<Integer> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{StoreSCU.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(
            description = "hostname of DICOM peer",
            index = "0")
    String peer;

    @CommandLine.Parameters(
            description = "tcp/ip port number of peer",
            showDefaultValue = CommandLine.Help.Visibility.NEVER,
            index = "1")
    int port;

    @CommandLine.Parameters(
            description = "DICOM file or directory to be transmitted",
            arity = "1",
            index = "2..*")
    List<Path> file;

    @CommandLine.Option(names = "--calling", paramLabel = "<aetitle>",
            description = "set my calling AE title")
    String calling = "STORESCU";

    @CommandLine.Option(names = "--called", paramLabel = "<aetitle>",
            description = "set called AE title of peer")
    String called = "STORESCP";

    @CommandLine.Option(names = "--max-ops-invoked", paramLabel = "<no>",
            description = "maximum number of outstanding operations invoked asynchronously, 0 = unlimited")
    int maxOpsInvoked;

    private final List<FileInfo> fileInfos = new ArrayList<>();

    public static void main(String[] args) {
        new CommandLine(new StoreSCU()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        for (Path path : file) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(file -> Files.isRegularFile(file)).forEach(this::scanFile);
            }
        }
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        AAssociate.RQ rq = new AAssociate.RQ();
        rq.setCallingAETitle(calling);
        rq.setCalledAETitle(called);
        if (maxOpsInvoked != 1) {
            rq.setAsyncOpsWindow(maxOpsInvoked, 1);
        }
        fileInfos.forEach(info -> rq.findOrAddPresentationContext(info.sopClassUID, info.transferSyntax));
        TCPConnector<Association> inst = new TCPConnector<>(
                (connector, role) -> new Association(connector, role, serviceRegistry));
        CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
        long t1 = System.currentTimeMillis();
        Association as = inst.connect(new Connection(), new Connection().setHostname(peer).setPort(port)).join();
        long t2 = System.currentTimeMillis();
        System.out.format("Open TCP connection in %d ms%n", t2 - t1);
        as.open(rq).join();
        long t3 = System.currentTimeMillis();
        System.out.format("Open DICOM association in %d ms%n", t3 - t2);
        long totLength = 0;
        for (FileInfo fileInfo : fileInfos) {
            as.cstore(fileInfo.sopClassUID, fileInfo.sopInstanceUID, fileInfo, fileInfo.transferSyntax);
            totLength += fileInfo.length;
        }
        as.release().join();
        long t4 = System.currentTimeMillis();
        long dt = t4 - t3;
        System.out.format("Send %d objects (%f MB) in %d ms (%f MB/s)%n",
                fileInfos.size(), totLength / 1000000.f, dt, totLength / (dt * 1000.f));
        as.onClose().join();
        task.cancel(true);
        return 0;
    }

    private void scanFile(Path path) {
        try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(path))) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.path = path;
            fileInfo.length = Files.size(path);
            DicomObject fmi = dis.readFileMetaInformation();
            if (fmi != null) {
                fileInfo.sopClassUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPClassUID);
                fileInfo.sopInstanceUID = fmi.getStringOrElseThrow(Tag.MediaStorageSOPInstanceUID);
                fileInfo.transferSyntax = fmi.getStringOrElseThrow(Tag.TransferSyntaxUID);
                fileInfo.position = dis.getStreamPosition();
                fileInfo.length -= fileInfo.position;
            } else {
                dis.withInputHandler(fileInfo).readDataSet();
            }
            fileInfos.add(fileInfo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class FileInfo implements Association.DataWriter, DicomInputHandler {
        Path path;
        String sopClassUID;
        String sopInstanceUID;
        String transferSyntax;
        long position;
        long length;

        @Override
        public void writeTo(OutputStream out, String tsuid) throws IOException {
            try (InputStream in = Files.newInputStream(path)) {
                in.skipNBytes(position);
                in.transferTo(out);
            }
        }

        @Override
        public boolean endElement(DicomInputStream dis, DicomElement dcmElm, boolean bulkData) throws IOException {
            switch (dcmElm.tag()) {
                case Tag.SOPInstanceUID:
                    sopInstanceUID = dcmElm.stringValue(0).get();
                    transferSyntax = dis.getEncoding().transferSyntaxUID;
                    return false;
                case Tag.SOPClassUID:
                    sopClassUID = dcmElm.stringValue(0).get();
            }
            return true;
        }
    }
}

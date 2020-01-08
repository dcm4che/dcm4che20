package org.dcm4che6.tool.storescp;

import org.dcm4che6.conf.model.ApplicationEntity;
import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.conf.model.Device;
import org.dcm4che6.conf.model.TransferCapability;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.UID;
import org.dcm4che6.io.DicomOutputStream;
import org.dcm4che6.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2019
 */
@CommandLine.Command(
        name = "storescp",
        mixinStandardHelpOptions = true,
        versionProvider = StoreSCP.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The storescp application implements a Service Class Provider (SCP) for the Storage Service Class.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ storescp 11112",
                "Starts server listening on port 11112" }
)
public class StoreSCP implements Callable<Integer>, DimseHandler {
    static final Logger LOG = LoggerFactory.getLogger(StoreSCP.class);

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{StoreSCP.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(
            description = "tcp/ip port number to listen on",
            showDefaultValue = CommandLine.Help.Visibility.NEVER,
            index = "0")
    int port;

    @CommandLine.Parameters(
            description = "directory to which received DICOM Composite Objects are stored",
            arity = "0..1",
            index = "1")
    Path directory;

    @CommandLine.Option(names = "--called", paramLabel = "<aetitle>",
            description = "accepted called AE title")
    String called = "*";

    @CommandLine.Option(names = "--max-ops-invoked", paramLabel = "<no>",
            description = "maximum number of outstanding operations it allows the Association-requester " +
                    "to invoke asynchronously, 0 = unlimited")
    int maxOpsInvoked;

    public static void main(String[] args) {
        new CommandLine(new StoreSCP()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        if (directory != null) {
            Files.createDirectories(directory);
        }
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry().setDefaultRQHandler(this);
        TCPConnector<Association> inst = new TCPConnector<>(
                (connector, role) -> new Association(connector, role, serviceRegistry));
        CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
        Connection local = new Connection().setPort(port);
        ApplicationEntity ae = new ApplicationEntity().setAETitle(called).addConnection(local);
        ae.addTransferCapability(new TransferCapability()
                .setSOPClass("*")
                .setTransferSyntaxes("*")
                .setRole(TransferCapability.Role.SCP));
        new Device().addApplicationEntity(ae);
        inst.bind(local);
        task.join();
        return 0;
    }

    @Override
    public void accept(Association as, Byte pcid, Dimse dimse, DicomObject commandSet, InputStream dataStream)
            throws IOException {
        if (dimse != Dimse.C_STORE_RQ) {
            throw new DicomServiceException(Status.UnrecognizedOperation);
        }
        if (directory == null) {
            dataStream.transferTo(OutputStream.nullOutputStream());
        } else {
            Path file = directory.resolve(commandSet.getStringOrElseThrow(Tag.AffectedSOPInstanceUID));
            LOG.info("Start M-WRITE {}", file);
            try (DicomOutputStream dos = new DicomOutputStream(
                    Files.newOutputStream(file))) {
                dos.writeFileMetaInformation(DicomObject.createFileMetaInformation(
                        commandSet.getStringOrElseThrow(Tag.AffectedSOPClassUID),
                        commandSet.getStringOrElseThrow(Tag.AffectedSOPInstanceUID),
                        as.getTransferSyntax(pcid)));
                dataStream.transferTo(dos);
            }
            LOG.info("Finished M-WRITE {}", file);
        }
        as.writeDimse(pcid, Dimse.C_STORE_RSP, dimse.mkRSP(commandSet));
    }

}

package org.dcm4che6.tool.echoscu;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.UID;
import org.dcm4che6.io.DicomInputStream;
import org.dcm4che6.net.AAssociate;
import org.dcm4che6.net.Association;
import org.dcm4che6.net.DicomServiceRegistry;
import org.dcm4che6.net.TCPConnector;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Dec 2019
 */
@CommandLine.Command(
        name = "echoscu",
        mixinStandardHelpOptions = true,
        versionProvider = EchoSCU.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The echoscu application implements a Service Class User (SCU) for the Verification SOP Class.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ echoscu --called DCM4CHEE localhost 11112",
                "Sends a DICOM C-ECHO message to Application Entity DCM4CHEE listening on port 11112 at localhost" }
)
public class EchoSCU implements Callable<Integer> {

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{EchoSCU.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    @CommandLine.Parameters(
            description = "hostname of DICOM peer",
            index = "0")
    String peer;

    @CommandLine.Parameters(
            description = "tcp/ip port number of peer",
            index = "1")
    int port;

    @CommandLine.Option(names = "--calling", paramLabel = "aetitle",
            description = "set my calling AE title")
    String calling = "ECHOSCU";

    @CommandLine.Option(names = "--called", paramLabel = "aetitle",
            description = "set called AE title of peer")
    String called = "ECHOSCP";

    public static void main(String[] args) {
        new CommandLine(new EchoSCU()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        TCPConnector<Association> inst = new TCPConnector<>(
                (connector, role) -> new Association(connector, role, serviceRegistry));
        CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
        AAssociate.RQ rq = new AAssociate.RQ();
        rq.setCallingAETitle(calling);
        rq.setCalledAETitle(called);
        rq.putPresentationContext((byte) 1, UID.VerificationSOPClass, UID.ImplicitVRLittleEndian);
        Association as = inst.connect(new Connection(), new Connection().setHostname(peer).setPort(port))
                .thenCompose(as1 -> as1.open(rq))
                .join();
        as.cecho().join();
        as.release().join();
        task.cancel(true);
        return 0;
    }
}

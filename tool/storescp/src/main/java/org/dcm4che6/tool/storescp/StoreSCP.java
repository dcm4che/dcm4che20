package org.dcm4che6.tool.storescp;

import org.dcm4che6.conf.model.Connection;
import org.dcm4che6.data.UID;
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
public class StoreSCP implements Callable<Integer> {

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

    public static void main(String[] args) {
        new CommandLine(new StoreSCP()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        TCPConnector<Association> inst = new TCPConnector<>(
                (connector, role) -> new Association(connector, role, serviceRegistry));
        CompletableFuture<Void> task = CompletableFuture.runAsync(inst);
        inst.bind(new Connection().setPort(port));
        task.join();
        return 0;
    }
}

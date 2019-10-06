package org.dcm4che6.tool.wadors;

import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2019
 */
@CommandLine.Command(
        name = "wadors",
        mixinStandardHelpOptions = true,
        versionProvider = WadoRS.ModuleVersionProvider.class,
        descriptionHeading = "%n",
        description = "The wadors utility.",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        showDefaultValues = true,
        footerHeading = "%nExample:%n",
        footer = { "$ wadors http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3.4/metadata",
                "Retrieve Metadata of Study from WADO-RS service provided at specified URL." }
)
public class WadoRS implements Callable<Integer> {

    @CommandLine.Parameters(
            description = "Service URL.",
            index = "0")
    URI url;

    @CommandLine.Option(names = { "-v", "--verbose" },
            description = "Include sent and received HTTP headers in the output.")
    boolean verbose;

    @CommandLine.Option(names = { "-a", "--accept" },
            description = "Specify Acceptable Media Types for the response payload.",
            defaultValue = "*/*")
    List<String> type = new ArrayList<>();

    @CommandLine.Option(names = { "--oauth2-bearer" },
            description = "Specify the Bearer Token for OAuth 2.0 server authentication.")
    String token;

    static class ModuleVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() {
            return new String[]{WadoRS.class.getModule().getDescriptor().rawVersion().orElse("6")};
        }
    }

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new WadoRS());
        cl.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        authorizationHeader(builder);
        acceptHeader(builder);
        HttpRequest request = builder
                .GET()
                .uri(url)
                .build();
        if (verbose) {
            System.out.println("> GET " + request.uri().getRawPath() + " HTTP/1.1");
            System.out.println("> Host: " + request.uri().getHost() + ":" + request.uri().getPort());
            promptHeaders("> ", request.headers().map());
        }
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (verbose) {
            System.out.println("< HTTP/1.1 " + response.statusCode());
            promptHeaders("< ", response.headers().map());
        }
        String mediaType = contentType(response.headers().map());
        try (InputStream body = response.body()) {
            if (beforeFirst(mediaType, '/').equals("multipart")) {
                String boundary = boundary(mediaType);
                MultipartBody multipartBody = new MultipartBody(body, boundary);
                Map<String, List<String>> part;
                int number = 1;
                while ((part = multipartBody.nextPart()) != null) {
                    if (verbose) {
                        System.out.println("< --" + boundary);
                        promptHeaders("< ", part);
                    }
                    storeTo(multipartBody, toPath(contentType(part), number++));
                }
                if (verbose) {
                    System.out.println("< --" + boundary + "--");
                }
            } else {
                storeTo(body, toPath(mediaType));
            }
        }
        return 0;
    }

    private static String contentType(Map<String, List<String>> headers) {
        return headers.getOrDefault("Content-Type", List.of())
                .stream().findFirst()
                .orElse("application/octet-stream")
                .toLowerCase();
    }

    private static String boundary(String mediaType) {
        int beginIndex = mediaType.indexOf("boundary=");
        if (beginIndex < 0)
            throw new NoSuchElementException("boundary");

        char delim = mediaType.charAt(beginIndex += 9);
        if (delim == '"')
            beginIndex++;
        else
            delim = ';';
        int endIndex = mediaType.indexOf(delim, beginIndex);
        return endIndex < 0 ? mediaType.substring(beginIndex) : mediaType.substring(beginIndex, endIndex);
    }

    private static void storeTo(InputStream in, Path path) throws IOException {
        System.out.println("* " + path.toAbsolutePath());
        Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path toPath(String mediaType) {
        return Paths.get(String.format("%s.%s",
                afterLast(url.getPath(), '/'),
                toFileExtension(mediaType)));
    }

    private Path toPath(String mediaType, int number) {
        return Paths.get(String.format("%s-%03d.%s",
                afterLast(url.getPath(), '/'),
                number,
                toFileExtension(mediaType)));
    }

    private String toFileExtension(String mediaType) {
        String ext = afterLast(beforeFirst(mediaType, ';'), '/');
        switch (ext) {
            case "dicom":
                return "dcm";
            case "dicom+xml":
                return "xml";
            case "dicom+json":
                return "json";
            case "jpeg":
                return "jpg";
            case "octet-stream":
                return "bin";
            default:
                return ext;
        }
    }

    private static String afterLast(String s, char ch) {
        return s.substring(s.lastIndexOf(ch) + 1);
    }

    private static String beforeFirst(String s, char ch) {
        int endIndex = s.indexOf(ch);
        return endIndex < 0 ? s : s.substring(0, endIndex);
    }

    private void authorizationHeader(HttpRequest.Builder builder) {
        if (token != null)
            builder.header("Authorization", "Bearer " + token);
    }

    private void acceptHeader(HttpRequest.Builder builder) {
        for (String t : type)
            builder.header("Accept", t);
    }

    private static void promptHeaders(String prefix, Map<String, List<String>> headers) {
        headers.forEach((k,v) -> v.stream().forEach(v1 -> System.out.println(prefix + k + ": " + v1)));
        System.out.println(prefix);
    }
}

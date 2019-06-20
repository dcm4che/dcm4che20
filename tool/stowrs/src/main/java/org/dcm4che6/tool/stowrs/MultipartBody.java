package org.dcm4che6.tool.stowrs;

import java.io.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2019
 */
public class MultipartBody {
    private List<Part> parts = new ArrayList<>();
    private final String boundary;
    private final String delimiter;

    public MultipartBody(String boundary) {
        this.boundary = boundary;
        this.delimiter = "\r\n--" + boundary;
    }

    public HttpRequest.BodyPublisher bodyPublisher() {
        return HttpRequest.BodyPublishers.ofInputStream(() -> new SequenceInputStream(enumeration()));
    }

    private Enumeration<? extends InputStream> enumeration() {
        return new Enumeration<>() {
            Iterator<Part> iter = new ArrayList<>(parts).iterator();
            Part part;
            boolean closed;

            @Override
            public boolean hasMoreElements() {
                return !closed;
            }

            @Override
            public InputStream nextElement() {
                InputStream stream;
                if (part != null) {
                    stream = part.newInputStream();
                    part = null;
                } else if (iter.hasNext()) {
                    part = iter.next();
                    stream = new ByteArrayInputStream(part.header.getBytes(StandardCharsets.UTF_8));
                } else if (!closed) {
                    stream = new ByteArrayInputStream(closeDelimiter().getBytes(StandardCharsets.UTF_8));
                    closed = true;
                } else {
                    throw new NoSuchElementException();
                }
                return stream;
            }
        };
    }

    private String closeDelimiter() {
        return delimiter + "--";
    }

    public void addPart(String type, final byte[] b, String location) {
        addPart(type, new Payload() {
            @Override
            public long size() {
                return b.length;
            }

            @Override
            public InputStream newInputStream() {
                return new ByteArrayInputStream(b);
            }
        }, location);
    }

    public void addPart(String type, final Path path, String location) {
        addPart(type, new Payload() {
            @Override
            public long size() {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public InputStream newInputStream() {
                try {
                    return Files.newInputStream(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }, location);
    }

    private void addPart(String type, Payload payload, String location) {
        parts.add(new Part(payload, header(type, payload.size(), location), type));
    }

    private String header(String type, long length, String location) {
        StringBuilder sb = new StringBuilder(256)
                .append(delimiter).append("\r\nContent-Type: ").append(type)
                .append("\r\nContent-Length: ").append(length);
        if (location != null)
            sb.append("\r\nContent-Location: ").append(location);
        return sb.append("\r\n\r\n").toString();
    }

    public String contentType() {
        return "multipart/related;type=\"" + parts.iterator().next().type + "\";boundary=" + boundary;
    }

    private static class Part {
        final String type;
        final String header;
        final Payload payload;

        public Part(Payload payload, String header, String type) {
            this.type = type;
            this.payload = payload;
            this.header = header;
        }

        public InputStream newInputStream() {
            return payload.newInputStream();
        }

    }

    private interface Payload {
        long size();
        InputStream newInputStream();
    }
}

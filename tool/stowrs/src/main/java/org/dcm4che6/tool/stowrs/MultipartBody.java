package org.dcm4che6.tool.stowrs;

import java.io.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2019
 */
public class MultipartBody {
    private List<Part> parts = new ArrayList<>();
    private final String boundary;

    public MultipartBody(String boundary) {
        this.boundary = boundary;
    }

    public HttpRequest.BodyPublisher bodyPublisher() {
        return HttpRequest.BodyPublishers.ofInputStream(() -> new SequenceInputStream(enumeration()));
    }

    private Enumeration<? extends InputStream> enumeration() {
        return new Enumeration<>() {
            Iterator<Part> iter = parts.iterator();
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
                    stream = new ByteArrayInputStream(part.header().getBytes(StandardCharsets.UTF_8));
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
        return "\r\n--" + boundary + "--";
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
        parts.add(new Part(payload, type, location));
    }

    public String contentType() {
        return "multipart/related;type=\"" + firstPart().type + "\";boundary=" + boundary;
    }

    private Part firstPart() {
        return parts.iterator().next();
    }

    private class Part {
        final String type;
        final String location;
        final Payload payload;

        public Part(Payload payload, String type, String location) {
            this.type = type;
            this.location = location;
            this.payload = payload;
        }

        public InputStream newInputStream() {
            return payload.newInputStream();
        }

        String header() {
            StringBuilder sb = new StringBuilder(256)
                    .append("\r\n--").append(boundary)
                    .append("\r\nContent-Type: ").append(type)
                    .append("\r\nContent-Length: ").append(payload.size());
            if (location != null)
                sb.append("\r\nContent-Location: ").append(location);
            return sb.append("\r\n\r\n").toString();
        }

        void prompt() {
            System.out.println("> --" + boundary);
            System.out.println("> Content-Type: " + type);
            System.out.println("> Content-Length: " + payload.size());
            if (location != null)
                System.out.println("> Content-Location: " + location);
            System.out.println(">");
            System.out.println("> [...]");
        }
    }

    private interface Payload {
        long size();
        InputStream newInputStream();
    }

    void prompt() {
        parts.stream().forEach(Part::prompt);
        System.out.println("> --" + boundary + "--");
        System.out.println(">");
    }
}

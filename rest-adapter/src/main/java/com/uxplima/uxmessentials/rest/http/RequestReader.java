package com.uxplima.uxmessentials.rest.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the bytes on a socket into an {@link HttpRequest}, and refuses anything outside the bounds.
 *
 * <p>The bounds are the point of this class. A listener on a port answers whoever reaches it, including somebody
 * sending a request line that never ends, ten thousand headers, or a {@code Content-Length} of two gigabytes. Each
 * of those is a memory problem rather than a parsing one, so each has a number here and a status to send back.
 *
 * <p>What it deliberately does not do: chunked bodies (rejected with {@code 501}), pipelining, and keep-alive. One
 * request per connection with a declared length is the whole shape, which leaves nothing to get out of step.
 */
public final class RequestReader {

    /** The request line plus every header, together. Eight kilobytes is more than any real client sends. */
    static final int MAX_HEAD_BYTES = 8 * 1024;

    /** How many headers is too many. */
    static final int MAX_HEADERS = 100;

    /** The largest body this listener reads. Every documented body is a few hundred bytes. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private RequestReader() {}

    /** Read one request, or throw an {@link HttpException} carrying the status to answer with. */
    public static HttpRequest read(InputStream stream) throws IOException {
        String head = readHead(stream);
        String[] lines = head.split("\r\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "empty request line");
        }
        RequestLine line = parseRequestLine(lines[0]);
        Map<String, String> headers = parseHeaders(lines);
        String body = readBody(stream, headers);
        return new HttpRequest(line.method(), line.path(), line.query(), headers, body);
    }

    /** Read up to the blank line that ends the header block, refusing a head that never ends. */
    private static String readHead(InputStream stream) throws IOException {
        StringBuilder head = new StringBuilder();
        int consecutive = 0;
        for (int read = 0; read < MAX_HEAD_BYTES; read++) {
            int next = stream.read();
            if (next < 0) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "the connection ended mid-request");
            }
            head.append((char) next);
            if (next == '\n') {
                consecutive++;
                if (consecutive == 2) {
                    return head.toString();
                }
            } else if (next != '\r') {
                consecutive = 0;
            }
        }
        throw new HttpException(HttpStatus.PAYLOAD_TOO_LARGE, "the request head is longer than " + MAX_HEAD_BYTES);
    }

    private static RequestLine parseRequestLine(String line) {
        String[] parts = line.trim().split(" ", -1);
        if (parts.length != 3) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "malformed request line");
        }
        String target = parts[1];
        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);
        Map<String, String> query = question < 0 ? Map.of() : parseQuery(target.substring(question + 1));
        if (!path.startsWith("/")) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "the path must start with a slash");
        }
        return new RequestLine(parts[0].toUpperCase(Locale.ROOT), path, query);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new HashMap<>();
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            query.put(decode(name), decode(value));
        }
        return query;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "malformed percent-encoding in the query string");
        }
    }

    private static Map<String, String> parseHeaders(String[] lines) {
        Map<String, String> headers = new HashMap<>();
        for (int at = 1; at < lines.length; at++) {
            String line = lines[at];
            if (line.isEmpty()) {
                continue;
            }
            if (headers.size() >= MAX_HEADERS) {
                throw new HttpException(HttpStatus.PAYLOAD_TOO_LARGE, "more than " + MAX_HEADERS + " headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "malformed header line");
            }
            headers.put(
                    line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    line.substring(colon + 1).trim());
        }
        return headers;
    }

    private static String readBody(InputStream stream, Map<String, String> headers) throws IOException {
        if (headers.containsKey("transfer-encoding")) {
            throw new HttpException(HttpStatus.NOT_IMPLEMENTED, "send a body with Content-Length, not chunked");
        }
        String declared = headers.get("content-length");
        if (declared == null) {
            return "";
        }
        int length = parseLength(declared);
        if (length > MAX_BODY_BYTES) {
            throw new HttpException(HttpStatus.PAYLOAD_TOO_LARGE, "the body is longer than " + MAX_BODY_BYTES);
        }
        byte[] body = stream.readNBytes(length);
        if (body.length < length) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "the body was shorter than Content-Length said");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static int parseLength(String declared) {
        try {
            int length = Integer.parseInt(declared.trim());
            if (length < 0) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "a negative Content-Length");
            }
            return length;
        } catch (NumberFormatException notANumber) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Content-Length is not a number");
        }
    }

    private record RequestLine(String method, String path, Map<String, String> query) {}
}

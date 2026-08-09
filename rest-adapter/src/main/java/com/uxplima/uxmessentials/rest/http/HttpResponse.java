package com.uxplima.uxmessentials.rest.http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One answer: a status, a body, and the headers that go with it.
 *
 * <p>Always with a {@code Content-Length}, never chunked. The connection closes after it, which is what makes the
 * framing something a reader can verify by looking at it.
 *
 * @param status the HTTP status
 * @param contentType the media type of the body
 * @param body the body text, UTF-8
 * @param headers any extra headers beyond content type and length
 */
public record HttpResponse(int status, String contentType, String body, Map<String, String> headers) {

    private static final String JSON = "application/json; charset=utf-8";

    public HttpResponse {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(body, "body");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
    }

    /** A JSON answer with this status. */
    public static HttpResponse json(int status, String body) {
        return new HttpResponse(status, JSON, body, Map.of());
    }

    /** A JSON answer with this status and one extra header, which is as many as anything here needs. */
    public static HttpResponse json(int status, String body, String headerName, String headerValue) {
        return new HttpResponse(status, JSON, body, Map.of(headerName, headerValue));
    }

    /** The whole response as bytes, ready to write to a socket. */
    public byte[] toBytes() {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Content-Type", contentType);
        all.put("Content-Length", Integer.toString(payload.length));
        all.put("Connection", "close");
        all.putAll(headers);

        StringBuilder head = new StringBuilder("HTTP/1.1 ")
                .append(status)
                .append(' ')
                .append(HttpStatus.reason(status))
                .append("\r\n");
        all.forEach(
                (name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("\r\n");

        byte[] headBytes = head.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] whole = new byte[headBytes.length + payload.length];
        System.arraycopy(headBytes, 0, whole, 0, headBytes.length);
        System.arraycopy(payload, 0, whole, headBytes.length, payload.length);
        return whole;
    }
}

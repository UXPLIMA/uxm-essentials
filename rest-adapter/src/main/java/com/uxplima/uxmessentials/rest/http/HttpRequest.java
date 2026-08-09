package com.uxplima.uxmessentials.rest.http;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One request, as far as this listener cares about it.
 *
 * <p>Header names are lower-cased on the way in, because a client may send them in any case and no caller should
 * have to remember that. The query string is already split and decoded. The body is text: every documented body is
 * JSON, and a request with none carries an empty string rather than a null nobody would check for.
 *
 * @param method the verb, upper-cased
 * @param path the path with no query string, always starting with a slash
 * @param query the decoded query parameters
 * @param headers the headers, keyed by lower-cased name
 * @param body the body as UTF-8 text, empty when there is none
 */
public record HttpRequest(
        String method, String path, Map<String, String> query, Map<String, String> headers, String body) {

    public HttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        query = Map.copyOf(Objects.requireNonNull(query, "query"));
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        Objects.requireNonNull(body, "body");
    }

    /** The value of {@code name}, whatever case the client sent it in. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    /** The value of the {@code name} query parameter. */
    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(query.get(name));
    }

    /** Whether this request is asking to become a WebSocket. */
    public boolean isWebSocketUpgrade() {
        return header("upgrade")
                .map(value -> value.equalsIgnoreCase("websocket"))
                .orElse(false);
    }
}

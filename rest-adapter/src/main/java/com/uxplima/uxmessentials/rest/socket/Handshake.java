package com.uxplima.uxmessentials.rest.socket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;

/**
 * The upgrade half of RFC 6455: check what the client sent, and answer with the one header that proves the server
 * understood it.
 *
 * <p>The check is worth doing properly. {@code Sec-WebSocket-Accept} is a hash of the client's key and a constant,
 * and a client library will hang up on a server that gets it wrong, which is a much worse failure than a refusal:
 * a refusal says what was missing, a wrong hash says nothing at all.
 *
 * <p>Only version 13 is accepted, which is the only version anything still speaks. A client asking for an older one
 * is told which version to use in the header the specification reserves for exactly that.
 */
public final class Handshake {

    /**
     * The constant every WebSocket server appends to the client's key before hashing.
     *
     * <p>Fixed by RFC 6455 section 4.2.2. It exists so a server cannot accidentally complete a handshake by echoing
     * something back; there is nothing to configure here.
     */
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** The only version of the protocol this speaks. */
    private static final String VERSION = "13";

    private Handshake() {}

    /**
     * Read the upgrade request.
     *
     * @return the bytes of the {@code 101} to write, or empty when {@link #refusalFor(HttpRequest)} says why not
     */
    public static Optional<byte[]> acceptFor(HttpRequest request) {
        if (refusalFor(request).isPresent()) {
            return Optional.empty();
        }
        String key = request.header("sec-websocket-key").orElseThrow();
        String head = "HTTP/1.1 " + HttpStatus.SWITCHING_PROTOCOLS + " "
                + HttpStatus.reason(HttpStatus.SWITCHING_PROTOCOLS) + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + signatureOf(key) + "\r\n\r\n";
        return Optional.of(head.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** What to answer instead, when the request is not a handshake this can complete. */
    public static Optional<HttpResponse> refusalFor(HttpRequest request) {
        if (!request.isWebSocketUpgrade()) {
            return Optional.of(Json.error(
                    HttpStatus.UPGRADE_REQUIRED,
                    "upgrade-required",
                    "this path is a WebSocket endpoint: connect with ws:// rather than http://"));
        }
        String version = request.header("sec-websocket-version").orElse("");
        if (!VERSION.equals(version.trim())) {
            return Optional.of(Json.error(
                    HttpStatus.BAD_REQUEST,
                    "bad-websocket-version",
                    "this server speaks WebSocket version " + VERSION,
                    "Sec-WebSocket-Version",
                    VERSION));
        }
        if (request.header("sec-websocket-key").orElse("").isBlank()) {
            return Optional.of(
                    Json.error(HttpStatus.BAD_REQUEST, "bad-handshake", "the handshake had no Sec-WebSocket-Key"));
        }
        return Optional.empty();
    }

    /** The value of {@code Sec-WebSocket-Accept} for a client key. */
    static String signatureOf(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key.trim() + MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime ships SHA-1; there is no configuration under which this is reachable.
            throw new IllegalStateException("SHA-1 is missing from this runtime", impossible);
        }
    }
}

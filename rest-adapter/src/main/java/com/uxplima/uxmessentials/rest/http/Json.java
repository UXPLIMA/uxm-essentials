package com.uxplima.uxmessentials.rest.http;

import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * The one shape every answer has, and the only place JSON is written or read.
 *
 * <p>Success and refusal share an envelope, because a consumer should not have to guess which it got:
 *
 * <pre>{@code
 * { "ok": true,  "data": ... }
 * { "ok": false, "code": "insufficient-funds", "message": "..." }
 * }</pre>
 *
 * <p>The code is the same constant the Java API returns, unchanged, so a consumer branches on the same string over
 * HTTP as in process. The message is English and for a log line.
 */
public final class Json {

    private static final Gson GSON = new Gson();

    private Json() {}

    /** An empty object, to fill in. */
    public static JsonObject object() {
        return new JsonObject();
    }

    /** {@code payload} as the data of a successful answer. */
    public static HttpResponse ok(JsonElement payload) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", true);
        envelope.add("data", Objects.requireNonNull(payload, "payload"));
        return HttpResponse.json(HttpStatus.OK, GSON.toJson(envelope));
    }

    /** A successful answer with nothing to hand back. */
    public static HttpResponse done() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", true);
        return HttpResponse.json(HttpStatus.OK, GSON.toJson(envelope));
    }

    /**
     * An operation the server understood and declined.
     *
     * <p>Sent as {@code 200}: the request was fine and the answer is a real one. A consumer branching on the
     * status alone would call this a success, which is why {@code ok} is in the body of every answer.
     */
    public static HttpResponse refused(String code, String message) {
        return HttpResponse.json(HttpStatus.OK, GSON.toJson(failureBody(code, message)));
    }

    /** A request that never made sense, or a listener that broke, with the status that says which. */
    public static HttpResponse error(int status, String code, String message) {
        return HttpResponse.json(status, GSON.toJson(failureBody(code, message)));
    }

    /** An error carrying a header, which the auth and rate-limit answers need. */
    public static HttpResponse error(int status, String code, String message, String header, String value) {
        return HttpResponse.json(status, GSON.toJson(failureBody(code, message)), header, value);
    }

    /** Render any element, for a body built somewhere else. */
    public static String write(JsonElement element) {
        return GSON.toJson(Objects.requireNonNull(element, "element"));
    }

    /** Read a request body as an object, or {@code 400} when it is not one. */
    public static JsonObject parse(String body) {
        if (body.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = GSON.fromJson(body, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "the body must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException malformed) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "the body is not valid JSON");
        }
    }

    private static JsonObject failureBody(String code, String message) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", false);
        envelope.addProperty("code", Objects.requireNonNull(code, "code"));
        envelope.addProperty("message", Objects.requireNonNull(message, "message"));
        return envelope;
    }
}

package com.uxplima.uxmessentials.rest.http;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A request that has found its route, so the parts of the path that varied now have names.
 *
 * <p>The accessors here read a value and say what is wrong with it in the same breath: a uuid that is not one, a
 * number that is not a number, a field a body left out. Each throws an {@link HttpException} carrying {@code 400},
 * because a handler asking for something the request did not supply has nothing to answer with.
 *
 * <p>It also carries who is asking. Every write the API publishes is attributed, and over HTTP the name is the
 * token's label, so an operator reading {@code /baninfo} sees {@code panel} rather than {@code the API}.
 *
 * @param http the request as it arrived
 * @param parameters the path parameters the route filled in
 * @param caller the label of the token behind the request
 */
public record RestRequest(HttpRequest http, Map<String, String> parameters, String caller) {

    public RestRequest {
        Objects.requireNonNull(http, "http");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(caller, "caller");
    }

    /** The same request, attributed to whoever authenticated. */
    public RestRequest withCaller(String name) {
        return new RestRequest(http, parameters, name);
    }

    /** The named path parameter, which the route guarantees is there. */
    public String parameter(String name) {
        String value = parameters.get(name);
        if (value == null) {
            throw new IllegalStateException("no path parameter named " + name + " on this route");
        }
        return value;
    }

    /** The named path parameter as a uuid, or {@code 400} when it is not one. */
    public UUID uuidParameter(String name) {
        String raw = parameter(name);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            throw new HttpException(HttpStatus.BAD_REQUEST, name + " is not a uuid: " + raw);
        }
    }

    /** The named path parameter as a whole number, or {@code 400} when it is not one. */
    public int intParameter(String name) {
        String raw = parameter(name);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            throw new HttpException(HttpStatus.BAD_REQUEST, name + " is not a number: " + raw);
        }
    }

    /** A query parameter read as a positive integer, or {@code fallback} when it was not sent. */
    public int intQuery(String name, int fallback) {
        Optional<String> raw = http.queryParam(name);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.get());
        } catch (NumberFormatException notANumber) {
            throw new HttpException(HttpStatus.BAD_REQUEST, name + " is not a number: " + raw.get());
        }
    }
}

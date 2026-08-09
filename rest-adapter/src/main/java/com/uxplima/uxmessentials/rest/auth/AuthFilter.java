package com.uxplima.uxmessentials.rest.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestServer;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * What every request goes through before it reaches a route: who is asking, whether they may, and how often.
 *
 * <p>Two header forms are accepted. {@code Bearer} is what an API client sends; {@code Basic} is what half the
 * tools an operator already owns send, with the label as the user and the secret as the password. Both end at the
 * same lookup.
 *
 * <p>What the refusals say is deliberate. A missing or unknown token is {@code 401} with nothing about whether the
 * label exists; a real token without the scope is {@code 403} naming the scope it needed, because that one is a
 * configuration mistake its holder can fix.
 *
 * <p>What comes out of it is the label, which the server hands to the handler: a write over HTTP is attributed to
 * the token that asked for it rather than to "the API".
 */
public final class AuthFilter implements RestServer.RequestFilter {

    private final TokenStore tokens;
    private final RateLimiter limiter;

    public AuthFilter(TokenStore tokens, RateLimiter limiter) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    @Override
    public RestServer.RequestFilter.Decision before(HttpRequest request, Route route) {
        Optional<String> presented = secretOf(request);
        if (presented.isEmpty()) {
            return refuse(unauthorized("send a token as Authorization: Bearer or Basic"));
        }
        Optional<ApiToken> token = tokens.authenticate(presented.get());
        if (token.isEmpty()) {
            return refuse(unauthorized("that token was not issued here"));
        }
        ApiToken holder = token.get();
        if (!matchesLabel(request, holder)) {
            return refuse(unauthorized("that token was not issued here"));
        }
        if (!holder.allows(route.scope())) {
            return refuse(Json.error(
                    HttpStatus.FORBIDDEN, "missing-scope", "this token needs the " + route.scope() + " scope"));
        }
        if (!limiter.allow(holder.label())) {
            return refuse(Json.error(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limited",
                    "this token is over its per-minute limit",
                    "Retry-After",
                    Long.toString(limiter.secondsUntilReset())));
        }
        return RestServer.RequestFilter.Decision.accept(holder.label());
    }

    private static RestServer.RequestFilter.Decision refuse(HttpResponse response) {
        return RestServer.RequestFilter.Decision.refuse(response);
    }

    /** The secret from either header form, or empty when there is no usable one. */
    private static Optional<String> secretOf(HttpRequest request) {
        Optional<String> header = request.header("authorization");
        if (header.isEmpty()) {
            return Optional.empty();
        }
        String value = header.get();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return nonBlank(value.substring(7).trim());
        }
        if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
            return basicSecret(value.substring(6).trim());
        }
        return Optional.empty();
    }

    private static Optional<String> basicSecret(String encoded) {
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon < 0 ? Optional.empty() : nonBlank(decoded.substring(colon + 1));
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    /** With Basic, a username was sent; it has to be the label the secret belongs to. */
    private static boolean matchesLabel(HttpRequest request, ApiToken token) {
        Optional<String> header = request.header("authorization");
        if (header.isEmpty() || !header.get().regionMatches(true, 0, "Basic ", 0, 6)) {
            return true;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.get().substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon >= 0 && decoded.substring(0, colon).equalsIgnoreCase(token.label());
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private static Optional<String> nonBlank(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static HttpResponse unauthorized(String message) {
        return Json.error(
                HttpStatus.UNAUTHORIZED, "unauthorized", message, "WWW-Authenticate", "Bearer realm=\"uxmEssentials\"");
    }
}

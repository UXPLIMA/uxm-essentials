package com.uxplima.uxmessentials.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestServer;
import com.uxplima.uxmessentials.rest.http.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthFilterTest {

    private static final Route READING = Route.of("GET", "/api/v1/status", Scopes.READ, request -> Json.done());
    private static final Route WRITING =
            Route.of("POST", "/api/v1/economy/deposit", Scopes.WRITE, request -> Json.done());

    @Test
    void aRequestWithNoTokenIsUnauthorized(@TempDir Path folder) {
        AuthFilter filter = filterOver(TokenStore.open(folder), 10);

        HttpResponse refusal = refusalOf(filter.before(get(Map.of()), READING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refusal.headers()).containsKey("WWW-Authenticate");
    }

    @Test
    void aTokenNobodyIssuedIsUnauthorized(@TempDir Path folder) {
        AuthFilter filter = filterOver(TokenStore.open(folder), 10);

        HttpResponse refusal = refusalOf(filter.before(get(bearer("uxm_madeup")), READING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aGoodTokenIsLetThroughUnderItsOwnName(@TempDir Path folder) {
        TokenStore tokens = TokenStore.open(folder);
        String secret = tokens.create("panel", Set.of(Scopes.READ));

        RestServer.RequestFilter.Decision decision = filterOver(tokens, 10).before(get(bearer(secret)), READING);

        assertThat(decision.refusal()).isEmpty();
        assertThat(decision.caller()).isEqualTo("panel");
    }

    @Test
    void basicAuthIsAcceptedForTheToolsThatOnlySpeakIt(@TempDir Path folder) {
        TokenStore tokens = TokenStore.open(folder);
        String secret = tokens.create("panel", Set.of(Scopes.READ));

        RestServer.RequestFilter.Decision decision =
                filterOver(tokens, 10).before(get(basic("panel", secret)), READING);

        assertThat(decision.refusal()).isEmpty();
        assertThat(decision.caller()).isEqualTo("panel");
    }

    @Test
    void basicAuthUnderTheWrongLabelIsUnauthorized(@TempDir Path folder) {
        TokenStore tokens = TokenStore.open(folder);
        String secret = tokens.create("panel", Set.of(Scopes.READ));

        HttpResponse refusal = refusalOf(filterOver(tokens, 10).before(get(basic("somebody", secret)), READING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aRealTokenWithoutTheScopeIsToldWhichScopeItNeeded(@TempDir Path folder) {
        TokenStore tokens = TokenStore.open(folder);
        String secret = tokens.create("panel", Set.of(Scopes.READ));

        HttpResponse refusal = refusalOf(filterOver(tokens, 10).before(post(bearer(secret)), WRITING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusal.body()).contains("write");
    }

    @Test
    void aTokenOverItsLimitIsToldWhenToComeBack(@TempDir Path folder) {
        TokenStore tokens = TokenStore.open(folder);
        String secret = tokens.create("panel", Set.of(Scopes.READ));
        AuthFilter filter = filterOver(tokens, 1);
        filter.before(get(bearer(secret)), READING);

        HttpResponse refusal = refusalOf(filter.before(get(bearer(secret)), READING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refusal.headers()).containsKey("Retry-After");
    }

    @Test
    void aHeaderInSomeOtherSchemeIsUnauthorizedRatherThanIgnored(@TempDir Path folder) {
        AuthFilter filter = filterOver(TokenStore.open(folder), 10);

        HttpResponse refusal = refusalOf(filter.before(get(Map.of("authorization", "Digest nope")), READING));

        assertThat(refusal.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static AuthFilter filterOver(TokenStore tokens, int perMinute) {
        return new AuthFilter(tokens, new RateLimiter(perMinute, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
    }

    private static HttpResponse refusalOf(RestServer.RequestFilter.Decision decision) {
        return decision.refusal().orElseThrow(() -> new AssertionError("expected the request to be refused"));
    }

    private static Map<String, String> bearer(String secret) {
        return Map.of("authorization", "Bearer " + secret);
    }

    private static Map<String, String> basic(String label, String secret) {
        String pair = label + ":" + secret;
        return Map.of(
                "authorization", "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
    }

    private static HttpRequest get(Map<String, String> headers) {
        return new HttpRequest("GET", "/api/v1/status", Map.of(), headers, "");
    }

    private static HttpRequest post(Map<String, String> headers) {
        return new HttpRequest("POST", "/api/v1/economy/deposit", Map.of(), headers, "");
    }
}

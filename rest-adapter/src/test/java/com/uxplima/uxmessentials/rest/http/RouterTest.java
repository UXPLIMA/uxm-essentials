package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RouterTest {

    private static final RestHandler NOTHING = request -> Json.done();

    @Test
    void findsTheRouteAndFillsInItsParameters() {
        Router router = new Router().add(Route.of("GET", "/api/v1/players/{uuid}", "read", NOTHING));

        Optional<Router.Match> match = router.find(get("/api/v1/players/7"));

        assertThat(match).isPresent();
        assertThat(match.orElseThrow().request().parameter("uuid")).isEqualTo("7");
    }

    @Test
    void anUnknownPathMatchesNothing() {
        Router router = new Router().add(Route.of("GET", "/api/v1/status", "read", NOTHING));

        assertThat(router.find(get("/api/v1/nowhere"))).isEmpty();
    }

    @Test
    void aKnownPathOnTheWrongVerbSaysSoRatherThanClaimingItIsMissing() {
        Router router = new Router().add(Route.of("POST", "/api/v1/economy/deposit", "write", NOTHING));

        assertThatThrownBy(() -> router.find(get("/api/v1/economy/deposit")))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void theFirstMatchingRouteWins() {
        Router router = new Router()
                .add(Route.of("GET", "/api/v1/warps/{name}", "read", NOTHING))
                .add(Route.of("GET", "/api/v1/warps/count", "read", NOTHING));

        assertThat(router.find(get("/api/v1/warps/count"))
                        .orElseThrow()
                        .route()
                        .path()
                        .source())
                .isEqualTo("/api/v1/warps/{name}");
    }

    @Test
    void everyRouteDescribesItsMethodPathAndScope() {
        Router router = new Router().add(Route.of("post", "/api/v1/economy/deposit", "write", NOTHING));

        assertThat(router.routes().getFirst().describe()).isEqualTo("POST /api/v1/economy/deposit [write]");
    }

    private static HttpRequest get(String path) {
        return new HttpRequest("GET", path, Map.of(), Map.of(), "");
    }
}

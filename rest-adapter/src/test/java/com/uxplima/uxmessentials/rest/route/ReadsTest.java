package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import org.junit.jupiter.api.Test;

class ReadsTest {

    @Test
    void aModuleThatIsOnIsHandedBack() {
        assertThat(Reads.module(Optional.of("query"), "homes")).isEqualTo("query");
    }

    @Test
    void aModuleThatIsOffIsAServiceThisServerDoesNotRun() {
        assertThatThrownBy(() -> Reads.module(Optional.empty(), "homes"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anAnswerThatArrivesIsReturned() {
        assertThat(Reads.await(CompletableFuture.completedFuture("here"))).isEqualTo("here");
    }

    @Test
    void anAnswerThatNeverComesTimesOutRatherThanHoldingTheConnectionOpen() {
        assertThatThrownBy(() -> Reads.await(new CompletableFuture<>(), 10, TimeUnit.MILLISECONDS))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void aQueryThatFailsSurfacesItsCauseRatherThanItsWrapper() {
        CompletableFuture<String> broken = CompletableFuture.failedFuture(new IllegalArgumentException("no database"));

        assertThatThrownBy(() -> Reads.await(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no database");
    }

    @Test
    void aValueThatIsThereIsRendered() {
        assertThat(Reads.found(Optional.of("spawn"), "warp", (String name) -> new JsonPrimitive(name))
                        .body())
                .isEqualTo("{\"ok\":true,\"data\":\"spawn\"}");
    }

    @Test
    void aValueThatIsNotThereSaysWhatWasLookedFor() {
        assertThatThrownBy(() -> Reads.found(
                        Optional.<String>empty(), "warp named nether", (String name) -> new JsonPrimitive(name)))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("warp named nether")
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anEmptyListIsAnEmptyArrayRatherThanNothing() {
        assertThat(Reads.list(List.<String>of(), (String name) -> new JsonPrimitive(name))
                        .body())
                .isEqualTo("{\"ok\":true,\"data\":[]}");
    }

    @Test
    void theLimitDefaultsIsCappedAndMustBePositive() {
        assertThat(Reads.limit(request(Map.of()), 10, 100)).isEqualTo(10);
        assertThat(Reads.limit(request(Map.of("limit", "5")), 10, 100)).isEqualTo(5);
        assertThat(Reads.limit(request(Map.of("limit", "9999")), 10, 100)).isEqualTo(100);
        assertThatThrownBy(() -> Reads.limit(request(Map.of("limit", "0")), 10, 100))
                .isInstanceOf(HttpException.class);
    }

    @Test
    void aUuidQueryIsOptionalButMustBeAUuidWhenItIsThere() {
        UUID somebody = UUID.randomUUID();

        assertThat(Reads.uuidQuery(request(Map.of()), "player")).isEmpty();
        assertThat(Reads.uuidQuery(request(Map.of("player", somebody.toString())), "player"))
                .contains(somebody);
        assertThatThrownBy(() -> Reads.uuidQuery(request(Map.of("player", "steve")), "player"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static RestRequest request(Map<String, String> query) {
        return new RestRequest(new HttpRequest("GET", "/a", query, Map.of(), ""), Map.of(), "test");
    }
}

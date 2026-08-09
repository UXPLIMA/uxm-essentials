package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RestRequestTest {

    private static final UUID SOMEBODY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void aUuidParameterIsReadAsOne() {
        assertThat(request(Map.of("uuid", SOMEBODY.toString())).uuidParameter("uuid"))
                .isEqualTo(SOMEBODY);
    }

    @Test
    void aParameterThatIsNotAUuidIsABadRequestRatherThanACrash() {
        assertThatThrownBy(() -> request(Map.of("uuid", "steve")).uuidParameter("uuid"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void askingForAParameterTheRouteHasNoneOfIsAProgrammingMistake() {
        assertThatThrownBy(() -> request(Map.of()).parameter("uuid")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingQueryParameterFallsBackAndABadOneIsRefused() {
        HttpRequest http = new HttpRequest("GET", "/a", Map.of("limit", "x"), Map.of(), "");
        RestRequest withBadLimit = new RestRequest(http, Map.of(), "test");

        assertThat(request(Map.of()).intQuery("limit", 10)).isEqualTo(10);
        assertThatThrownBy(() -> withBadLimit.intQuery("limit", 10))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theCallerIsWhoeverAuthenticated() {
        assertThat(request(Map.of()).withCaller("panel").caller()).isEqualTo("panel");
    }

    private static RestRequest request(Map<String, String> parameters) {
        return new RestRequest(new HttpRequest("GET", "/a", Map.of(), Map.of(), ""), parameters, "test");
    }
}

package com.uxplima.uxmessentials.rest.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void aSuccessCarriesItsPayloadUnderData() {
        JsonObject payload = Json.object();
        payload.addProperty("balance", 25);

        HttpResponse response = Json.ok(payload);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isEqualTo("{\"ok\":true,\"data\":{\"balance\":25}}");
    }

    @Test
    void aSuccessWithNothingToSayIsStillAnEnvelope() {
        assertThat(Json.done().body()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void aRefusalIsAnAnswerRatherThanAnHttpError() {
        HttpResponse response = Json.refused("insufficient-funds", "not enough money");

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).contains("\"ok\":false").contains("\"code\":\"insufficient-funds\"");
    }

    @Test
    void anErrorCarriesItsStatusAndAnyHeaderItNeeds() {
        HttpResponse response =
                Json.error(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", "slow down", "Retry-After", "30");

        assertThat(response.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.headers()).containsEntry("Retry-After", "30");
    }

    @Test
    void anEmptyBodyParsesAsAnEmptyObject() {
        assertThat(Json.parse("").entrySet()).isEmpty();
    }

    @Test
    void aBodyThatIsNotAnObjectIsRefused() {
        assertThatThrownBy(() -> Json.parse("[1,2,3]"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aBodyThatIsNotJsonIsRefused() {
        assertThatThrownBy(() -> Json.parse("{not json"))
                .isInstanceOf(HttpException.class)
                .extracting(failure -> ((HttpException) failure).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aResponseIsWrittenWithItsOwnLengthAndNoKeepAlive() {
        String wire = new String(Json.done().toBytes(), StandardCharsets.UTF_8);

        assertThat(wire).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(wire).contains("Content-Length: 11\r\n");
        assertThat(wire).contains("Connection: close\r\n");
        assertThat(wire).endsWith("\r\n\r\n{\"ok\":true}");
    }
}

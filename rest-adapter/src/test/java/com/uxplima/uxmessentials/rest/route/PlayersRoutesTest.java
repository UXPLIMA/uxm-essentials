package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.api.query.UxmPlayersQuery;
import com.uxplima.uxmessentials.api.view.UxmPlayer;
import com.uxplima.uxmessentials.api.view.UxmPlayerMatch;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import org.junit.jupiter.api.Test;

/**
 * A client holding a typed name gets the account it means, every account it could mean on an offline-mode server, or
 * a plain not-found, and a Floodgate name arrives intact.
 */
class PlayersRoutesTest {

    private static final UxmPlayer STEVE =
            new UxmPlayer(UUID.fromString("5627dd98-e6be-3c21-b8a8-e92344183641"), "Steve");
    private static final UxmPlayer STEVE_LOWER =
            new UxmPlayer(UUID.fromString("b3b1a8f8-6c5e-3b4c-8a3a-2b6f1c0e9d11"), "steve");
    private static final UxmPlayer BEDROCK =
            new UxmPlayer(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), "#Gamer");

    private final List<String> asked = new ArrayList<>();

    @Test
    void aNameOneAccountPlaysUnderAnswersThatAccount() {
        JsonObject data = data(PlayersRoutes.byName(answering(new UxmPlayerMatch.Found(STEVE)), request("Steve")));

        assertThat(data.get("match").getAsString()).isEqualTo("found");
        assertThat(data.getAsJsonObject("player").get("player-id").getAsString())
                .isEqualTo(STEVE.playerId().toString());
        assertThat(data.getAsJsonObject("player").get("player-name").getAsString())
                .isEqualTo("Steve");
        assertThat(data.getAsJsonArray("candidates")).hasSize(1);
    }

    @Test
    void aNameSeveralAccountsMatchOnlyInCaseListsThemAllAndPicksNone() {
        UxmPlayerMatch several = new UxmPlayerMatch.Ambiguous("STEVE", List.of(STEVE, STEVE_LOWER));

        JsonObject data = data(PlayersRoutes.byName(answering(several), request("STEVE")));

        assertThat(data.get("match").getAsString()).isEqualTo("ambiguous");
        assertThat(data.get("player").isJsonNull()).isTrue();
        assertThat(data.getAsJsonArray("candidates").asList())
                .extracting(candidate ->
                        candidate.getAsJsonObject().get("player-name").getAsString())
                .containsExactly("Steve", "steve");
    }

    @Test
    void aNameNobodyPlayedUnderIsNotFound() {
        assertThatThrownBy(() -> PlayersRoutes.byName(answering(new UxmPlayerMatch.Unknown()), request("Nobody")))
                .isInstanceOfSatisfying(
                        HttpException.class,
                        refused -> assertThat(refused.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aPercentEncodedFloodgatePrefixAndAPlusReachTheLookupAsTyped() {
        PlayersRoutes.byName(answering(new UxmPlayerMatch.Found(BEDROCK)), request("%23Gamer"));
        PlayersRoutes.byName(answering(new UxmPlayerMatch.Found(BEDROCK)), request("+Gamer"));

        assertThat(asked).containsExactly("#Gamer", "+Gamer");
    }

    @Test
    void aBlankNameIsABadRequest() {
        assertThatThrownBy(() -> PlayersRoutes.byName(answering(new UxmPlayerMatch.Unknown()), request("%20")))
                .isInstanceOfSatisfying(
                        HttpException.class,
                        refused -> assertThat(refused.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(asked).isEmpty();
    }

    private UxmPlayersQuery answering(UxmPlayerMatch match) {
        return name -> {
            asked.add(name);
            return CompletableFuture.completedFuture(match);
        };
    }

    private static RestRequest request(String name) {
        HttpRequest http = new HttpRequest("GET", "/api/v1/players/by-name/" + name, Map.of(), Map.of(), "");
        return new RestRequest(http, Map.of("name", name), "panel");
    }

    private static JsonObject data(HttpResponse response) {
        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
    }
}

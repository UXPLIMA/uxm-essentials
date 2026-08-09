package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.view.UxmVotePeriod;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading votes: one player's totals, the leaderboard, and how close the party is. */
public final class VoteRoutes {

    /** The most leaderboard rows one request can ask for. */
    private static final int TOP_CAP = 100;

    private VoteRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/vote/top", Scopes.READ, request -> top(api, request)),
                Route.of("GET", PREFIX + "/vote/party", Scopes.READ, request -> party(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/votes", Scopes.READ, request -> votes(api, request)));
    }

    private static HttpResponse votes(UxmEssentialsApi api, RestRequest request) {
        UxmVoteQuery vote = voteOf(api);
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("totals", Views.voteTotals(Reads.await(vote.totals(playerId))));
        payload.addProperty("queued-rewards", Reads.await(vote.queuedRewards(playerId)));
        return Json.ok(payload);
    }

    private static HttpResponse top(UxmEssentialsApi api, RestRequest request) {
        return Reads.list(
                Reads.await(voteOf(api).top(period(request), Reads.limit(request, 10, TOP_CAP))), Views::voteRank);
    }

    private static HttpResponse party(UxmEssentialsApi api) {
        return Json.ok(Views.voteParty(Reads.await(voteOf(api).party())));
    }

    /** {@code ?period=} names one of the published periods; anything else is a mistake worth saying out loud. */
    private static UxmVotePeriod period(RestRequest request) {
        String raw = request.http().queryParam("period").orElse(UxmVotePeriod.ALL_TIME.name());
        try {
            return UxmVotePeriod.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "no vote period called " + raw);
        }
    }

    private static UxmVoteQuery voteOf(UxmEssentialsApi api) {
        return Reads.module(api.vote(), "vote");
    }
}

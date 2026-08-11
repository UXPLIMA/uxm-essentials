package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmRanksActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Ranks: the ladder, where a player stands on it, and the three ways a rank changes. */
public final class RanksRoutes {

    private RanksRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/ranks", Scopes.READ, request -> ladder(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/rank", Scopes.READ, request -> standing(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/rank/rankup",
                        Scopes.WRITE,
                        request -> rankUp(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/rank/set",
                        Scopes.WRITE,
                        request -> setRank(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/rank/prestige",
                        Scopes.WRITE,
                        request -> prestige(actions, request)));
    }

    private static HttpResponse ladder(UxmEssentialsApi api) {
        return Reads.list(reads(api).ladder(), Views::rank);
    }

    /**
     * Where the player stands, with the reach of the rung above it.
     *
     * <p>{@code can-rank-up} is answered by the plugin rather than inferred here: a requirement can name the
     * player's inventory or a placeholder, and only the server can read those. It is false for a player who is
     * offline, which is the same answer the command would give them.
     */
    private static HttpResponse standing(UxmEssentialsApi api, RestRequest request) {
        UxmRanksQuery ranks = reads(api);
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add(
                "standing",
                Reads.await(ranks.standingOf(playerId)).map(Views::rankStanding).orElse(JsonNull.INSTANCE));
        payload.addProperty("can-rank-up", Reads.await(ranks.canRankUp(playerId)));
        return Json.ok(payload);
    }

    private static HttpResponse rankUp(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).rankUp(request.uuidParameter("uuid")));
    }

    /** Put the player on a named rung, the way {@code /setrank} does: no cost, no requirements, offline is fine. */
    private static HttpResponse setRank(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setRank(request.uuidParameter("uuid"), Body.of(request).text("rank")));
    }

    private static HttpResponse prestige(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).prestige(request.uuidParameter("uuid")));
    }

    private static UxmRanksQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.ranks(), "ranks");
    }

    private static UxmRanksActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).ranks(), "ranks");
    }
}

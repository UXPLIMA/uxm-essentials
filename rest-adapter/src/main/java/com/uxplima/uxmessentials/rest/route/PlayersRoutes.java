package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading what is true of a player right now: away, hidden, flying, and how long they have played. */
public final class PlayersRoutes {

    private PlayersRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/presence", Scopes.READ, request -> presence(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/state", Scopes.READ, request -> state(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/playtime", Scopes.READ, request -> playtime(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/vanish", Scopes.READ, request -> vanish(api, request)),
                Route.of("GET", PREFIX + "/presence/afk", Scopes.READ, request -> afk(api)),
                Route.of("GET", PREFIX + "/vanish", Scopes.READ, request -> vanished(api)));
    }

    /**
     * One player's presence.
     *
     * <p>Absent means offline: presence is held for players who are here, and a uuid nobody has seen is a
     * {@code 404} rather than a made-up record of somebody who has never been away.
     */
    private static HttpResponse presence(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(
                Reads.module(api.presence(), "presence").of(request.uuidParameter("uuid")),
                "player online with that id",
                Views::presence);
    }

    private static HttpResponse state(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(
                Reads.module(api.playerState(), "playerstate").of(request.uuidParameter("uuid")),
                "player online with that id",
                Views::playerState);
    }

    private static HttpResponse playtime(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(Views.playtime(
                Reads.await(Reads.module(api.playtime(), "playerstate").of(request.uuidParameter("uuid")))));
    }

    /**
     * Whether one player is hidden, and at what level.
     *
     * <p>Reachable only with a token, which is the whole reason it can be answered at all: an operator's panel needs
     * to know who is vanished, and nothing a player can reach ever asks this.
     */
    private static HttpResponse vanish(UxmEssentialsApi api, RestRequest request) {
        UxmVanishQuery query = Reads.module(api.vanish(), "vanish");
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.addProperty("player-id", playerId.toString());
        payload.addProperty("vanished", query.isVanished(playerId));
        payload.addProperty("level", query.levelOf(playerId));
        return Json.ok(payload);
    }

    private static HttpResponse afk(UxmEssentialsApi api) {
        return Reads.list(Reads.module(api.presence(), "presence").afk(), Views::presence);
    }

    private static HttpResponse vanished(UxmEssentialsApi api) {
        JsonArray ids = new JsonArray();
        Reads.module(api.vanish(), "vanish").vanished().forEach(id -> ids.add(id.toString()));
        return Json.ok(ids);
    }
}

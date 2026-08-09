package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmVanishActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * Who is hidden, and hiding or showing somebody.
 *
 * <p>Reachable only with a token, which is the whole reason it can be answered at all: an operator's panel needs to
 * know who is vanished, and nothing a player can reach ever asks this.
 */
public final class VanishRoutes {

    private VanishRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/vanish", Scopes.READ, request -> vanished(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/vanish", Scopes.READ, request -> vanish(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/vanish",
                        Scopes.WRITE,
                        request -> setVanished(actions, request)));
    }

    private static HttpResponse vanish(UxmEssentialsApi api, RestRequest request) {
        UxmVanishQuery query = Reads.module(api.vanish(), "vanish");
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.addProperty("player-id", playerId.toString());
        payload.addProperty("vanished", query.isVanished(playerId));
        payload.addProperty("level", query.levelOf(playerId));
        return Json.ok(payload);
    }

    private static HttpResponse vanished(UxmEssentialsApi api) {
        JsonArray ids = new JsonArray();
        Reads.module(api.vanish(), "vanish").vanished().forEach(id -> ids.add(id.toString()));
        return Json.ok(ids);
    }

    /** {@code vanished} defaults to true, since hiding somebody is the call that gets made. */
    private static HttpResponse setVanished(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setVanished(request.uuidParameter("uuid"), Body.of(request).flag("vanished", true)));
    }

    private static UxmVanishActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).vanish(), "vanish");
    }
}

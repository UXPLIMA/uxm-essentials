package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.api.action.UxmScoreboardActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmScoreboardQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * Whether a player has put their sidebar away, putting it away, and redrawing it.
 *
 * <p>{@code hidden} comes back null for a player who is offline. The preference survives their relog, but it is
 * stored on them and cannot be read from here while they are away, and a null says that better than a default.
 */
public final class ScoreboardRoutes {

    private ScoreboardRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/scoreboard", Scopes.READ, request -> hidden(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/scoreboard",
                        Scopes.WRITE,
                        request -> setHidden(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/scoreboard/refresh",
                        Scopes.WRITE,
                        request -> refresh(actions, request)));
    }

    private static HttpResponse hidden(UxmEssentialsApi api, RestRequest request) {
        UxmScoreboardQuery query = Reads.module(api.scoreboard(), "scoreboard");
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = Json.object();
        payload.addProperty("player-id", playerId.toString());
        payload.add(
                "hidden",
                Reads.await(query.hidden(playerId))
                        .<JsonElement>map(JsonPrimitive::new)
                        .orElse(JsonNull.INSTANCE));
        return Json.ok(payload);
    }

    /** {@code hidden} defaults to true, since putting a sidebar away is the call that gets made. */
    private static HttpResponse setHidden(ActionsFor actions, RestRequest request) {
        UxmScoreboardActions writes = writes(actions, request);
        UUID playerId = request.uuidParameter("uuid");
        boolean hide = Body.of(request).flag("hidden", true);
        return Writes.outcome(hide ? writes.hide(playerId) : writes.show(playerId));
    }

    private static HttpResponse refresh(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).refresh(request.uuidParameter("uuid")));
    }

    private static UxmScoreboardActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).scoreboard(), "scoreboard");
    }
}

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.api.action.UxmBossBarActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmBossBarQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * The boss bars: the ones written, the ones on a player's screen, hiding and showing them, and putting one up for a
 * while for everybody or for one player.
 *
 * <p>{@code hidden} comes back null for a player who is offline, as the sidebar's does: the choice is kept on the
 * player and cannot be read while they are away. An announcement needs its {@code seconds}; a bar up for no time is
 * a mistake rather than a default.
 */
public final class BossBarRoutes {

    private BossBarRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/bossbars", Scopes.READ, request -> bars(api)),
                Route.of(
                        "POST",
                        PREFIX + "/bossbars/{bar}/announce",
                        Scopes.WRITE,
                        request -> announce(actions, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/bossbar", Scopes.READ, request -> player(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/bossbar",
                        Scopes.WRITE,
                        request -> setHidden(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/bossbar/refresh",
                        Scopes.WRITE,
                        request -> refresh(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/bossbar/{bar}/send",
                        Scopes.WRITE,
                        request -> send(actions, request)));
    }

    private static HttpResponse bars(UxmEssentialsApi api) {
        JsonObject payload = Json.object();
        payload.add("bars", strings(query(api).bars()));
        return Json.ok(payload);
    }

    private static HttpResponse player(UxmEssentialsApi api, RestRequest request) {
        UxmBossBarQuery query = query(api);
        UUID playerId = request.uuidParameter("uuid");
        JsonObject payload = Json.object();
        payload.addProperty("player-id", playerId.toString());
        payload.add(
                "hidden",
                Reads.await(query.hidden(playerId))
                        .<JsonElement>map(JsonPrimitive::new)
                        .orElse(JsonNull.INSTANCE));
        payload.add("shown", strings(Reads.await(query.shown(playerId))));
        return Json.ok(payload);
    }

    /** {@code hidden} defaults to true, since hiding the bars is the call that gets made. */
    private static HttpResponse setHidden(ActionsFor actions, RestRequest request) {
        UxmBossBarActions writes = writes(actions, request);
        UUID playerId = request.uuidParameter("uuid");
        boolean hide = Body.of(request).flag("hidden", true);
        return Writes.outcome(hide ? writes.hide(playerId) : writes.show(playerId));
    }

    private static HttpResponse refresh(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).refresh(request.uuidParameter("uuid")));
    }

    private static HttpResponse announce(ActionsFor actions, RestRequest request) {
        Duration length = length(request);
        return Writes.outcome(writes(actions, request).announce(request.parameter("bar"), length));
    }

    private static HttpResponse send(ActionsFor actions, RestRequest request) {
        Duration length = length(request);
        return Writes.outcome(
                writes(actions, request).send(request.uuidParameter("uuid"), request.parameter("bar"), length));
    }

    private static Duration length(RestRequest request) {
        return Body.of(request)
                .seconds("seconds")
                .orElseThrow(() -> new HttpException(HttpStatus.BAD_REQUEST, "seconds is required"));
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static UxmBossBarQuery query(UxmEssentialsApi api) {
        return Reads.module(api.bossBar(), "bossbar");
    }

    private static UxmBossBarActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).bossBar(), "bossbar");
    }
}

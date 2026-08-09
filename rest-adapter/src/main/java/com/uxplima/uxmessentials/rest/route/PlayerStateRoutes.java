package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.uxplima.uxmessentials.api.action.UxmPlayerStateActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * The switches held for a player who is online, and how long they have played.
 *
 * <p>One route per switch rather than one that takes them all. A body carrying five fields would need five results,
 * and "god mode on, fly refused, speed out of range" is not something one answer can say honestly.
 */
public final class PlayerStateRoutes {

    private PlayerStateRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/state", Scopes.READ, request -> state(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/playtime", Scopes.READ, request -> playtime(api, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/state/god", Scopes.WRITE, request -> god(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/state/fly", Scopes.WRITE, request -> fly(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/state/gamemode",
                        Scopes.WRITE,
                        request -> gameMode(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/state/walk-speed",
                        Scopes.WRITE,
                        request -> walkSpeed(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/state/fly-speed",
                        Scopes.WRITE,
                        request -> flySpeed(actions, request)),
                Route.of(
                        "POST", PREFIX + "/players/{uuid}/state/heal", Scopes.WRITE, request -> heal(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/state/feed",
                        Scopes.WRITE,
                        request -> feed(actions, request)));
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

    private static HttpResponse god(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setGodMode(request.uuidParameter("uuid"), Body.of(request).flag("enabled", true)));
    }

    private static HttpResponse fly(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setFlying(request.uuidParameter("uuid"), Body.of(request).flag("enabled", true)));
    }

    private static HttpResponse gameMode(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setGameMode(request.uuidParameter("uuid"), Body.of(request).gameMode("mode")));
    }

    private static HttpResponse walkSpeed(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setWalkSpeed(request.uuidParameter("uuid"), Body.of(request).number("multiplier")));
    }

    private static HttpResponse flySpeed(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .setFlySpeed(request.uuidParameter("uuid"), Body.of(request).number("multiplier")));
    }

    private static HttpResponse heal(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).heal(request.uuidParameter("uuid")));
    }

    private static HttpResponse feed(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).feed(request.uuidParameter("uuid")));
    }

    private static UxmPlayerStateActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).playerState(), "playerstate");
    }
}

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmTeleportActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Teleport requests, where {@code /back} would take somebody, and moving a player. */
public final class TeleportRoutes {

    private TeleportRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of(
                        "GET",
                        PREFIX + "/players/{uuid}/teleport-requests",
                        Scopes.READ,
                        request -> requests(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/back", Scopes.READ, request -> back(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/teleport",
                        Scopes.WRITE,
                        request -> teleport(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/back", Scopes.WRITE, request -> goBack(actions, request)));
    }

    /** Both directions in one answer, since a player has at most one outgoing request and any number waiting. */
    private static HttpResponse requests(UxmEssentialsApi api, RestRequest request) {
        UxmTeleportQuery teleport = teleportOf(api);
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("incoming", Views.each(teleport.pendingFor(playerId), Views::teleportRequest));
        payload.add(
                "outgoing",
                teleport.outgoingFrom(playerId).map(Views::teleportRequest).orElse(JsonNull.INSTANCE));
        return Json.ok(payload);
    }

    private static HttpResponse back(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(
                teleportOf(api).backPoint(request.uuidParameter("uuid")),
                "return point for that player",
                Views::backPoint);
    }

    private static UxmTeleportQuery teleportOf(UxmEssentialsApi api) {
        return Reads.module(api.teleport(), "teleport");
    }

    /** Move a player somewhere. The world has to be loaded, and the player has to be online. */
    private static HttpResponse teleport(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .teleport(request.uuidParameter("uuid"), Body.of(request).location("location")));
    }

    /** Send a player back where they were, which is what {@code /back} does and refuses the same way. */
    private static HttpResponse goBack(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).back(request.uuidParameter("uuid")));
    }

    private static UxmTeleportActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).teleport(), "teleport");
    }
}

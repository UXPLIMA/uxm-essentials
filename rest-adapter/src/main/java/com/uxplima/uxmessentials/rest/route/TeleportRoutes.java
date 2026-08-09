package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading teleport requests and where {@code /back} would take somebody. */
public final class TeleportRoutes {

    private TeleportRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of(
                        "GET",
                        PREFIX + "/players/{uuid}/teleport-requests",
                        Scopes.READ,
                        request -> requests(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/back", Scopes.READ, request -> back(api, request)));
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
}

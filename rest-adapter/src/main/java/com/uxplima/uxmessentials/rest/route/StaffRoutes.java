package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmStaffQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * Who is on duty.
 *
 * <p>Read-only, which is the published surface showing through rather than a decision taken here: entering staff
 * mode swaps a real inventory for a loadout, and only the module can be trusted to put the real one back.
 */
public final class StaffRoutes {

    private StaffRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/staff", Scopes.READ, request -> onDuty(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/staff", Scopes.READ, request -> player(api, request)));
    }

    private static HttpResponse onDuty(UxmEssentialsApi api) {
        JsonArray ids = new JsonArray();
        Reads.module(api.staff(), "staff").inStaffMode().forEach(id -> ids.add(id.toString()));
        return Json.ok(ids);
    }

    private static HttpResponse player(UxmEssentialsApi api, RestRequest request) {
        UxmStaffQuery query = Reads.module(api.staff(), "staff");
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = Json.object();
        payload.addProperty("player-id", playerId.toString());
        payload.addProperty("staff-mode", query.isInStaffMode(playerId));
        payload.add(
                "mode",
                query.modeOf(playerId).<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        return Json.ok(payload);
    }
}

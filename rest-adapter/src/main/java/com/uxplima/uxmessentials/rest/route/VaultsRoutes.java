package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Reading a player's vaults: which they have, how many they may have, and how big each one is.
 *
 * <p>What is in them is not here and will not be. Item stacks are a Bukkit type with no published form, and an
 * inventory rendered as JSON would be a second, worse item format that this project would then have to keep in step
 * with Minecraft's own.
 */
public final class VaultsRoutes {

    private VaultsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/vaults", Scopes.READ, request -> vaults(api, request)),
                Route.of(
                        "GET", PREFIX + "/players/{uuid}/vaults/{index}", Scopes.READ, request -> vault(api, request)));
    }

    private static HttpResponse vaults(UxmEssentialsApi api, RestRequest request) {
        UxmVaultsQuery query = vaultsOf(api);
        UUID ownerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("vaults", Views.each(Reads.await(query.list(ownerId)), Views::vault));
        payload.addProperty("count", Reads.await(query.count(ownerId)));
        payload.add("limit", Views.number(Reads.await(query.limit(ownerId))));
        payload.addProperty("rows", Reads.await(query.rows(ownerId)));
        return Json.ok(payload);
    }

    private static HttpResponse vault(UxmEssentialsApi api, RestRequest request) {
        int index = request.intParameter("index");
        return Reads.found(
                Reads.await(vaultsOf(api).get(request.uuidParameter("uuid"), index)),
                "vault numbered " + index,
                Views::vault);
    }

    private static UxmVaultsQuery vaultsOf(UxmEssentialsApi api) {
        return Reads.module(api.vaults(), "vaults");
    }
}

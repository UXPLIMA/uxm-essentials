package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.google.gson.JsonNull;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmItemworldQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * What a player's items have been bound to run.
 *
 * <p>Read-only, and only because the rest of itemworld has nothing to read: repairing an item or aliasing the
 * weather is a verb with no state behind it. Writing a binding is left to {@code /powertool}, which stamps it onto
 * the item the player is holding, and there is no held item in an HTTP request.
 *
 * <p>Both read a live inventory, so an offline player answers empty rather than 404: they have items, we simply
 * cannot see them from here.
 */
public final class ItemworldRoutes {

    private ItemworldRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of(
                        "GET", PREFIX + "/players/{uuid}/powertools", Scopes.READ, request -> powertools(api, request)),
                Route.of(
                        "GET",
                        PREFIX + "/players/{uuid}/powertools/held",
                        Scopes.READ,
                        request -> inHand(api, request)));
    }

    private static HttpResponse powertools(UxmEssentialsApi api, RestRequest request) {
        UxmItemworldQuery query = itemworld(api);
        return Reads.list(Reads.await(query.powertools(request.uuidParameter("uuid"))), Views::powertool);
    }

    /** Null rather than 404: an empty hand is an answer, and the player is not the thing being looked up. */
    private static HttpResponse inHand(UxmEssentialsApi api, RestRequest request) {
        UxmItemworldQuery query = itemworld(api);
        return Json.ok(Reads.await(query.powertoolInHand(request.uuidParameter("uuid")))
                .map(Views::powertool)
                .orElse(JsonNull.INSTANCE));
    }

    private static UxmItemworldQuery itemworld(UxmEssentialsApi api) {
        return Reads.module(api.itemworld(), "itemworld");
    }
}

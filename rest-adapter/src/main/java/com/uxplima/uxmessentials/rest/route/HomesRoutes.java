package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Reading homes.
 *
 * <p>The list answer carries the quota with it rather than making a panel ask twice: "three homes" and "three of
 * five" are different things to draw, and the second is what anybody rendering this actually wants.
 */
public final class HomesRoutes {

    private HomesRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/homes", Scopes.READ, request -> homes(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/homes/{slot}", Scopes.READ, request -> home(api, request)));
    }

    private static HttpResponse homes(UxmEssentialsApi api, RestRequest request) {
        UxmHomesQuery query = homesOf(api);
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("homes", Views.each(Reads.await(query.list(playerId)), Views::home));
        payload.addProperty("count", Reads.await(query.count(playerId)));
        payload.add("limit", Views.number(Reads.await(query.limit(playerId))));
        return Json.ok(payload);
    }

    /**
     * One home by slot.
     *
     * <p>The slot in the path is the one players see, counting from one, because that is the number they would type
     * and the number a panel would have shown them. The API counts from zero, and that translation lives here.
     */
    private static HttpResponse home(UxmEssentialsApi api, RestRequest request) {
        int slotNumber = request.intParameter("slot");
        if (slotNumber < 1) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "home slots count from one: " + slotNumber);
        }
        return Reads.found(
                Reads.await(homesOf(api).get(request.uuidParameter("uuid"), slotNumber - 1)),
                "home in slot " + slotNumber,
                Views::home);
    }

    private static UxmHomesQuery homesOf(UxmEssentialsApi api) {
        return Reads.module(api.homes(), "homes");
    }
}

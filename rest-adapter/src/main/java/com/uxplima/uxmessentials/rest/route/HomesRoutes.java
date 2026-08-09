package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
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
 * Homes: reading them, and setting, moving, renaming and deleting one.
 *
 * <p>The list answer carries the quota with it rather than making a panel ask twice: "three homes" and "three of
 * five" are different things to draw, and the second is what anybody rendering this actually wants.
 *
 * <p>Every path names the slot the way a player would, counting from one. The published API counts from zero, and
 * that single translation lives in {@link #slot(RestRequest)} rather than in each route.
 */
public final class HomesRoutes {

    private HomesRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/homes", Scopes.READ, request -> homes(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/homes/{slot}", Scopes.READ, request -> home(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/homes/{slot}/set",
                        Scopes.WRITE,
                        request -> set(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/homes/{slot}/move",
                        Scopes.WRITE,
                        request -> move(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/homes/{slot}/rename",
                        Scopes.WRITE,
                        request -> rename(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/homes/{slot}/delete",
                        Scopes.WRITE,
                        request -> delete(actions, request)));
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

    private static HttpResponse home(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(
                Reads.await(homesOf(api).get(request.uuidParameter("uuid"), slot(request))),
                "home in slot " + request.parameter("slot"),
                Views::home);
    }

    /** Set a home in a slot, whether or not one was there. */
    private static HttpResponse set(ActionsFor actions, RestRequest request) {
        return Writes.result(
                writes(actions, request)
                        .set(
                                request.uuidParameter("uuid"),
                                slot(request),
                                Body.of(request).location("location")),
                Views::home);
    }

    /** Move a home that already exists, which is a different thing from setting one and fails when it is not there. */
    private static HttpResponse move(ActionsFor actions, RestRequest request) {
        return Writes.result(
                writes(actions, request)
                        .relocate(
                                request.uuidParameter("uuid"),
                                slot(request),
                                Body.of(request).location("location")),
                Views::home);
    }

    private static HttpResponse rename(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .rename(
                        request.uuidParameter("uuid"),
                        slot(request),
                        Body.of(request).text("label")));
    }

    private static HttpResponse delete(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).delete(request.uuidParameter("uuid"), slot(request)));
    }

    /** The slot as the API counts it, from the number in the path, which counts as players do. */
    private static int slot(RestRequest request) {
        int slotNumber = request.intParameter("slot");
        if (slotNumber < 1) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "home slots count from one: " + slotNumber);
        }
        return slotNumber - 1;
    }

    private static UxmHomesQuery homesOf(UxmEssentialsApi api) {
        return Reads.module(api.homes(), "homes");
    }

    private static UxmHomeActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).homes(), "homes");
    }
}

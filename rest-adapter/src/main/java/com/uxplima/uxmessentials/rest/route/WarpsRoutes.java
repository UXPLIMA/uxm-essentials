package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * The server's warps: reading them, and making, moving and deleting one.
 *
 * <p>{@code ?visible-to=} narrows the list to what one player may actually use, which is the difference between a
 * warp list a panel can show a player and one that lists doors they cannot open.
 */
public final class WarpsRoutes {

    private WarpsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/warps", Scopes.READ, request -> warps(api, request)),
                Route.of("GET", PREFIX + "/warps/{name}", Scopes.READ, request -> warp(api, request)),
                Route.of("POST", PREFIX + "/warps/{name}/create", Scopes.WRITE, request -> create(actions, request)),
                Route.of("POST", PREFIX + "/warps/{name}/move", Scopes.WRITE, request -> move(actions, request)),
                Route.of("POST", PREFIX + "/warps/{name}/delete", Scopes.WRITE, request -> delete(actions, request)));
    }

    /** Make a warp. A name that is already taken is refused rather than quietly overwritten. */
    private static HttpResponse create(ActionsFor actions, RestRequest request) {
        return Writes.result(
                writes(actions, request)
                        .create(request.parameter("name"), Body.of(request).location("location")),
                Views::warp);
    }

    private static HttpResponse move(ActionsFor actions, RestRequest request) {
        return Writes.result(
                writes(actions, request)
                        .move(request.parameter("name"), Body.of(request).location("location")),
                Views::warp);
    }

    private static HttpResponse delete(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).delete(request.parameter("name")));
    }

    private static UxmWarpActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).warps(), "warps");
    }

    private static HttpResponse warps(UxmEssentialsApi api, RestRequest request) {
        UxmWarpsQuery warps = warpsOf(api);
        Optional<UUID> viewer = Reads.uuidQuery(request, "visible-to");
        if (viewer.isEmpty()) {
            return Reads.list(Reads.await(warps.list()), Views::warp);
        }
        return Reads.list(Reads.await(warps.visibleTo(viewer.get())), Views::warp);
    }

    /** One warp, with its rating folded in, since a lookup is where a client would want it. */
    private static HttpResponse warp(UxmEssentialsApi api, RestRequest request) {
        UxmWarpsQuery warps = warpsOf(api);
        String name = request.parameter("name");
        UxmWarp warp = Reads.await(warps.get(name))
                .orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "no warp named " + name));

        JsonObject payload = Views.warp(warp).getAsJsonObject();
        payload.addProperty("average-rating", Reads.await(warps.averageRating(name)));
        return Json.ok(payload);
    }

    private static UxmWarpsQuery warpsOf(UxmEssentialsApi api) {
        return Reads.module(api.warps(), "warps");
    }
}

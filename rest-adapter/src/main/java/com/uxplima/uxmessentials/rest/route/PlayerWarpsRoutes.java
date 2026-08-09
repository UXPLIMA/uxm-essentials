package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading the warps players own. */
public final class PlayerWarpsRoutes {

    /** The biggest page the public listing will hand out at once. */
    private static final int PAGE_CAP = 100;

    private PlayerWarpsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/playerwarps", Scopes.READ, request -> page(api, request)),
                Route.of("GET", PREFIX + "/playerwarps/{name}", Scopes.READ, request -> warp(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/playerwarps", Scopes.READ, request -> owned(api, request)));
    }

    /** The public listing, a page at a time, because a big server has thousands of these. */
    private static HttpResponse page(UxmEssentialsApi api, RestRequest request) {
        int page = request.intQuery("page", 1);
        if (page < 1) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "pages count from one: " + page);
        }
        int size = Reads.limit(request, 20, PAGE_CAP);

        JsonObject payload = new JsonObject();
        payload.addProperty("page", page);
        payload.addProperty("page-size", size);
        payload.add("warps", Views.each(Reads.await(playerWarpsOf(api).listPublic(page, size)), Views::playerWarp));
        return Json.ok(payload);
    }

    private static HttpResponse warp(UxmEssentialsApi api, RestRequest request) {
        String name = request.parameter("name");
        return Reads.found(Reads.await(playerWarpsOf(api).get(name)), "player warp named " + name, Views::playerWarp);
    }

    private static HttpResponse owned(UxmEssentialsApi api, RestRequest request) {
        UxmPlayerWarpsQuery query = playerWarpsOf(api);
        UUID ownerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("warps", Views.each(Reads.await(query.ownedBy(ownerId)), Views::playerWarp));
        payload.addProperty("count", Reads.await(query.count(ownerId)));
        payload.add("limit", Views.number(Reads.await(query.limit(ownerId))));
        return Json.ok(payload);
    }

    private static UxmPlayerWarpsQuery playerWarpsOf(UxmEssentialsApi api) {
        return Reads.module(api.playerWarps(), "playerwarps");
    }
}

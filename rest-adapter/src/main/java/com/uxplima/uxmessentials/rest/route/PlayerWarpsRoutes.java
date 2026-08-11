package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmPlayerWarpsActions;
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

/**
 * Reading the warps players own, and the writes a panel needs over them.
 *
 * <p>Every write names the player it acts as, in the body as {@code actor}, because a warp's rules are written in
 * terms of who is asking: the owner may remove it, a manager may move it, a stranger may do neither. A token is
 * not a person, so it has to say which person it stands for.
 */
public final class PlayerWarpsRoutes {

    /** The biggest page the public listing will hand out at once. */
    private static final int PAGE_CAP = 100;

    private PlayerWarpsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/playerwarps", Scopes.READ, request -> page(api, request)),
                Route.of("GET", PREFIX + "/playerwarps/{name}", Scopes.READ, request -> warp(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/playerwarps", Scopes.READ, request -> owned(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/playerwarps",
                        Scopes.WRITE,
                        request -> create(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/playerwarps/{name}/relocate",
                        Scopes.WRITE,
                        request -> relocate(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/playerwarps/{name}/rename",
                        Scopes.WRITE,
                        request -> rename(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/playerwarps/{name}/archive",
                        Scopes.WRITE,
                        request -> archive(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/playerwarps/{name}/restore",
                        Scopes.WRITE,
                        request -> restore(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/playerwarps/{name}/delete",
                        Scopes.WRITE,
                        request -> delete(actions, request)));
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

    /** Create a warp for the player in the path, at the place in the body. */
    private static HttpResponse create(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(writes(actions, request)
                .create(request.uuidParameter("uuid"), body.text("name"), body.location("location")));
    }

    private static HttpResponse relocate(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(writes(actions, request)
                .relocate(body.uuid("actor"), request.parameter("name"), body.location("location")));
    }

    private static HttpResponse rename(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(
                writes(actions, request).rename(body.uuid("actor"), request.parameter("name"), body.text("new-name")));
    }

    /** Retire a warp. Recoverable: {@code /restore} puts it back with everything hanging off it intact. */
    private static HttpResponse archive(ActionsFor actions, RestRequest request) {
        return Writes.outcome(actingOn(actions, request, UxmPlayerWarpsActions::archive));
    }

    private static HttpResponse restore(ActionsFor actions, RestRequest request) {
        return Writes.outcome(actingOn(actions, request, UxmPlayerWarpsActions::restore));
    }

    /** Drop a warp for good. There is no undo, which is why archive is the one a cleanup should reach for. */
    private static HttpResponse delete(ActionsFor actions, RestRequest request) {
        return Writes.outcome(actingOn(actions, request, UxmPlayerWarpsActions::delete));
    }

    /** The three verbs that need nothing but who is asking and which warp. */
    private static CompletableFuture<UxmOutcome> actingOn(ActionsFor actions, RestRequest request, WarpVerb verb) {
        return verb.run(writes(actions, request), Body.of(request).uuid("actor"), request.parameter("name"));
    }

    private static UxmPlayerWarpsQuery playerWarpsOf(UxmEssentialsApi api) {
        return Reads.module(api.playerWarps(), "playerwarps");
    }

    private static UxmPlayerWarpsActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).playerWarps(), "playerwarps");
    }

    /** One of the verbs whose whole request is an actor and a warp name. */
    @FunctionalInterface
    private interface WarpVerb {
        CompletableFuture<UxmOutcome> run(UxmPlayerWarpsActions writes, UUID actorId, String name);
    }
}

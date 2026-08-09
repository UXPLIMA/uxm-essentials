package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading the managed worlds, and whether one player may enter one. */
public final class WorldsRoutes {

    private WorldsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/worlds", Scopes.READ, request -> worlds(api)),
                Route.of("GET", PREFIX + "/worlds/{name}", Scopes.READ, request -> world(api, request)),
                Route.of("GET", PREFIX + "/worlds/{name}/access", Scopes.READ, request -> access(api, request)));
    }

    private static HttpResponse worlds(UxmEssentialsApi api) {
        return Reads.list(Reads.await(worldsOf(api).list()), Views::world);
    }

    private static HttpResponse world(UxmEssentialsApi api, RestRequest request) {
        String name = request.parameter("name");
        return Reads.found(Reads.await(worldsOf(api).get(name)), "world named " + name, Views::world);
    }

    /**
     * Whether {@code ?player=} may enter this world, and why not when they may not.
     *
     * <p>The answer distinguishes a permission they lack from a world that is full, because those are different
     * things to tell somebody and only one of them is worth waiting out.
     */
    private static HttpResponse access(UxmEssentialsApi api, RestRequest request) {
        String name = request.parameter("name");
        UUID playerId = Reads.uuidQuery(request, "player")
                .orElseThrow(() -> new HttpException(HttpStatus.BAD_REQUEST, "ask about a player: ?player=<uuid>"));
        UxmWorldAccess access = Reads.await(worldsOf(api).access(playerId, name));

        JsonObject payload = new JsonObject();
        payload.addProperty("world", name);
        payload.addProperty("player-id", playerId.toString());
        payload.addProperty("access", access.name());
        payload.addProperty("allowed", access == UxmWorldAccess.ALLOWED);
        return Json.ok(payload);
    }

    private static UxmWorldsQuery worldsOf(UxmEssentialsApi api) {
        return Reads.module(api.worlds(), "worlds");
    }
}

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonNull;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * WorldGuard regions, read through the plugin.
 *
 * <p>Under the world rather than at the top level, because a region id is only unique within one. Read-only, which
 * is the published surface's shape rather than a decision taken here: editing a protection is an operator act with
 * its own command and its own audit trail.
 */
public final class RegionsRoutes {

    private RegionsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/worlds/{name}/regions", Scopes.READ, request -> regions(api, request)),
                Route.of("GET", PREFIX + "/worlds/{name}/regions/{id}", Scopes.READ, request -> region(api, request)));
    }

    /**
     * Every region in the world, or the ones covering a point when {@code x}, {@code y} and {@code z} are given.
     *
     * <p>One route with a filter rather than two, so a path segment can never be read as a region id. The covering
     * set comes back highest priority first, which is the order that decides an overlap.
     */
    private static HttpResponse regions(UxmEssentialsApi api, RestRequest request) {
        String world = request.parameter("name");
        Optional<UxmLocation> point = point(request, world);
        UxmRegionsQuery regions = reads(api);
        return Reads.list(
                point.map(where -> Reads.await(regions.at(where))).orElseGet(() -> Reads.await(regions.in(world))),
                Views::region);
    }

    /** One region by id, or {@code null} when that world defines none with it. */
    private static HttpResponse region(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(Reads.await(reads(api).region(request.parameter("name"), request.parameter("id")))
                .map(Views::region)
                .orElse(JsonNull.INSTANCE));
    }

    /** The point to ask about, or empty when no coordinate was sent; a partial coordinate is a bad request. */
    private static Optional<UxmLocation> point(RestRequest request, String world) {
        Optional<String> x = request.http().queryParam("x");
        Optional<String> y = request.http().queryParam("y");
        Optional<String> z = request.http().queryParam("z");
        if (x.isEmpty() && y.isEmpty() && z.isEmpty()) {
            return Optional.empty();
        }
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "a point needs all three of x, y and z");
        }
        return Optional.of(
                new UxmLocation(world, number(x.orElseThrow()), number(y.orElseThrow()), number(z.orElseThrow())));
    }

    private static double number(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException notANumber) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "not a coordinate: " + raw);
        }
    }

    private static UxmRegionsQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.regions(), "regions");
    }
}

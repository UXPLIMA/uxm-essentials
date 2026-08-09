package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.view.UxmKit;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Reading the kit catalogue, and what one player can do with it.
 *
 * <p>The per-player answer is every kit with its state attached rather than only the claimable ones, because a menu
 * showing "Miner: 4h 12m" needs the kits a player cannot claim yet just as much as the ones they can.
 */
public final class KitsRoutes {

    private KitsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/kits", Scopes.READ, request -> kits(api)),
                Route.of("GET", PREFIX + "/kits/{id}", Scopes.READ, request -> kit(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/kits", Scopes.READ, request -> forPlayer(api, request)));
    }

    private static HttpResponse kits(UxmEssentialsApi api) {
        return Reads.list(kitsOf(api).list(), Views::kit);
    }

    private static HttpResponse kit(UxmEssentialsApi api, RestRequest request) {
        String id = request.parameter("id");
        return Reads.found(kitsOf(api).get(id), "kit named " + id, Views::kit);
    }

    /**
     * Every kit, with this player's state on each.
     *
     * <p>Both questions are asked of every kit before any of them is waited on, so the whole answer costs one round
     * trip's worth of waiting rather than one per kit.
     */
    private static HttpResponse forPlayer(UxmEssentialsApi api, RestRequest request) {
        UxmKitsQuery kits = kitsOf(api);
        UUID playerId = request.uuidParameter("uuid");

        List<UxmKit> catalogue = kits.list();
        List<Pending> pending = new ArrayList<>();
        for (UxmKit kit : catalogue) {
            pending.add(
                    new Pending(kit, kits.canClaim(playerId, kit.id()), kits.cooldownRemaining(playerId, kit.id())));
        }

        JsonArray answer = new JsonArray();
        pending.forEach(one -> answer.add(one.render()));
        return Json.ok(answer);
    }

    private static UxmKitsQuery kitsOf(UxmEssentialsApi api) {
        return Reads.module(api.kits(), "kits");
    }

    /** One kit with its two outstanding questions, waited on only once everything has been asked. */
    private record Pending(
            UxmKit kit, CompletableFuture<Boolean> claimable, CompletableFuture<Optional<Duration>> cooldown) {

        private JsonObject render() {
            JsonObject json = Views.kit(kit).getAsJsonObject();
            json.addProperty("can-claim", Reads.await(claimable));
            json.add(
                    "cooldown-remaining-seconds",
                    Views.number(Reads.await(cooldown).map(Duration::toSeconds)));
            return json;
        }
    }
}

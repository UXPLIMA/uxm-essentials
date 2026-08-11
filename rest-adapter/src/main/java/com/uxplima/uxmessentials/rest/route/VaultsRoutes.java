package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmVaultsActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Reading a player's vaults: which they have, how many they may have, and how big each one is.
 *
 * <p>What is in them is not here and will not be. Item stacks are a Bukkit type with no published form, and an
 * inventory rendered as JSON would be a second, worse item format that this project would then have to keep in step
 * with Minecraft's own. The writes reflect that: a panel can open a vault in front of its owner, label it or remove
 * it, and moving items stays where the item policy and the save-on-close live.
 */
public final class VaultsRoutes {

    private VaultsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/vaults", Scopes.READ, request -> vaults(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/vaults/{index}", Scopes.READ, request -> vault(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/vaults/{index}/open",
                        Scopes.WRITE,
                        request -> open(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/vaults/{index}/label",
                        Scopes.WRITE,
                        request -> label(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/vaults/{index}/delete",
                        Scopes.WRITE,
                        request -> delete(actions, request)));
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

    /** Put the real vault window in front of the owner, which is the only way items move. */
    private static HttpResponse open(ActionsFor actions, RestRequest request) {
        return Writes.outcome(
                writes(actions, request).open(request.uuidParameter("uuid"), request.intParameter("index")));
    }

    /**
     * Change how a vault is labelled: its {@code name}, its {@code icon}, or both.
     *
     * <p>A field left out is left alone and a field sent as {@code null} is cleared, so one call can name a vault
     * and drop its icon without needing a verb for each combination. A body with neither field is a mistake worth
     * reporting rather than a write that did nothing.
     */
    private static HttpResponse label(ActionsFor actions, RestRequest request) {
        UUID ownerId = request.uuidParameter("uuid");
        int index = request.intParameter("index");
        Body body = Body.of(request);
        UxmVaultsActions writes = writes(actions, request);

        boolean named = body.json().has("name");
        boolean iconed = body.json().has("icon");
        if (!named && !iconed) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "send a name, an icon, or both");
        }
        CompletableFuture<UxmOutcome> chain = CompletableFuture.completedFuture(UxmOutcome.ok());
        if (named) {
            chain = then(
                    chain,
                    () -> body.has("name")
                            ? writes.rename(ownerId, index, body.text("name"))
                            : writes.clearName(ownerId, index));
        }
        if (iconed) {
            chain = then(
                    chain,
                    () -> body.has("icon")
                            ? writes.setIcon(ownerId, index, body.text("icon"))
                            : writes.clearIcon(ownerId, index));
        }
        return Writes.outcome(chain);
    }

    /** Run {@code next} only if what came before it worked, so a refused rename does not go on to set an icon. */
    private static CompletableFuture<UxmOutcome> then(
            CompletableFuture<UxmOutcome> before, Supplier<CompletableFuture<UxmOutcome>> next) {
        return before.thenCompose(
                outcome -> outcome.succeeded() ? next.get() : CompletableFuture.completedFuture(outcome));
    }

    /** Remove a vault, and the items in it with it. */
    private static HttpResponse delete(ActionsFor actions, RestRequest request) {
        return Writes.outcome(
                writes(actions, request).delete(request.uuidParameter("uuid"), request.intParameter("index")));
    }

    private static UxmVaultsQuery vaultsOf(UxmEssentialsApi api) {
        return Reads.module(api.vaults(), "vaults");
    }

    private static UxmVaultsActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).vaults(), "vaults");
    }
}

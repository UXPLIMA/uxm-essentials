package com.uxplima.uxmessentials.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.http.Router;
import com.uxplima.uxmessentials.rest.route.ActionsFor;
import com.uxplima.uxmessentials.rest.route.EconomyRoutes;
import com.uxplima.uxmessentials.rest.route.HomesRoutes;
import com.uxplima.uxmessentials.rest.route.KitsRoutes;
import com.uxplima.uxmessentials.rest.route.MessagingRoutes;
import com.uxplima.uxmessentials.rest.route.ModerationRoutes;
import com.uxplima.uxmessentials.rest.route.PlayerStateRoutes;
import com.uxplima.uxmessentials.rest.route.PlayerWarpsRoutes;
import com.uxplima.uxmessentials.rest.route.PresenceRoutes;
import com.uxplima.uxmessentials.rest.route.TeleportRoutes;
import com.uxplima.uxmessentials.rest.route.VanishRoutes;
import com.uxplima.uxmessentials.rest.route.VaultsRoutes;
import com.uxplima.uxmessentials.rest.route.VoteRoutes;
import com.uxplima.uxmessentials.rest.route.WarpsRoutes;
import com.uxplima.uxmessentials.rest.route.WorldsRoutes;

/**
 * The route table: every path this listener answers, in one place.
 *
 * <p>Assembled rather than discovered. A table somebody can read top to bottom is the only way to answer "what does
 * this expose" without running it, and it is what the golden-file guard pins so a route cannot appear or disappear
 * without the diff saying so.
 *
 * <p>Each context contributes its own list. The order they are added in is the order a path is matched in, which
 * matters only where a literal segment and a parameter could both take it; no two contexts share a prefix, so
 * within this file the order is alphabetical rather than significant.
 */
public final class Routes {

    /** The version in every path, so a second one can exist beside the first rather than instead of it. */
    public static final String PREFIX = "/api/v1";

    private Routes() {}

    /**
     * Build the table against a live API.
     *
     * <p>Reads go through {@code api} directly; writes go through {@code actions}, which hands each request an action
     * surface named after the token that made it, so the audit trail says who asked rather than only which jar.
     *
     * <p>Two contexts take no {@code actions}: player warps and vaults publish a query surface and no action surface,
     * so over HTTP they are readable and nothing more. That is the published API's shape showing through rather than
     * a decision taken here.
     */
    public static Router build(UxmEssentialsApi api, ActionsFor actions) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(actions, "actions");
        return new Router()
                .add(Route.of("GET", PREFIX + "/status", Scopes.READ, request -> status(api)))
                .addAll(EconomyRoutes.of(api, actions))
                .addAll(HomesRoutes.of(api, actions))
                .addAll(KitsRoutes.of(api, actions))
                .addAll(MessagingRoutes.of(api, actions))
                .addAll(ModerationRoutes.of(api, actions))
                .addAll(PlayerStateRoutes.of(api, actions))
                .addAll(PlayerWarpsRoutes.of(api))
                .addAll(PresenceRoutes.of(api, actions))
                .addAll(TeleportRoutes.of(api, actions))
                .addAll(VanishRoutes.of(api, actions))
                .addAll(VaultsRoutes.of(api))
                .addAll(VoteRoutes.of(api, actions))
                .addAll(WarpsRoutes.of(api, actions))
                .addAll(WorldsRoutes.of(api, actions));
    }

    /**
     * What is running and what is on.
     *
     * <p>The modules are reported from the published surfaces themselves rather than from a list of names kept here,
     * so a module that gains or loses a surface cannot leave this answering yesterday's truth.
     */
    private static HttpResponse status(UxmEssentialsApi api) {
        JsonObject payload = Json.object();
        payload.addProperty("version", api.version());
        payload.addProperty("api", "v1");

        JsonObject modules = Json.object();
        surfaces(api).forEach(modules::addProperty);
        payload.add("modules", modules);
        return Json.ok(payload);
    }

    /** Which published surface each module has, present or absent, in a stable order. */
    private static Map<String, Boolean> surfaces(UxmEssentialsApi api) {
        Map<String, Boolean> present = new LinkedHashMap<>();
        present.put("economy", api.economy().isPresent());
        present.put("homes", api.homes().isPresent());
        present.put("warps", api.warps().isPresent());
        present.put("playerwarps", api.playerWarps().isPresent());
        present.put("kits", api.kits().isPresent());
        present.put("vaults", api.vaults().isPresent());
        present.put("moderation", api.moderation().isPresent());
        present.put("presence", api.presence().isPresent());
        present.put("vanish", api.vanish().isPresent());
        present.put("playerstate", api.playerState().isPresent());
        present.put("teleport", api.teleport().isPresent());
        present.put("worlds", api.worlds().isPresent());
        present.put("vote", api.vote().isPresent());
        present.put("messaging", api.messaging().isPresent());
        return present;
    }
}

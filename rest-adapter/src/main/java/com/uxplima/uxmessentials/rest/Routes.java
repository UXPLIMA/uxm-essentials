package com.uxplima.uxmessentials.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.http.Router;

/**
 * The route table: every path this listener answers, in one place.
 *
 * <p>Assembled rather than discovered. A table somebody can read top to bottom is the only way to answer "what
 * does this expose" without running it, and it is what the golden-file guard pins so a route cannot disappear in
 * a refactor without the diff saying so.
 */
public final class Routes {

    /** The version in every path, so a second one can exist beside the first rather than instead of it. */
    public static final String PREFIX = "/api/v1";

    private Routes() {}

    /** Build the table against a live API. */
    public static Router build(UxmEssentialsApi api) {
        Objects.requireNonNull(api, "api");
        return new Router().add(Route.of("GET", PREFIX + "/status", Scopes.READ, request -> status(api)));
    }

    /**
     * What is running and what is on.
     *
     * <p>The modules are reported from the published surfaces themselves rather than from a list of names kept
     * here, so a module that gains or loses a surface cannot leave this answering yesterday's truth.
     */
    private static com.uxplima.uxmessentials.rest.http.HttpResponse status(UxmEssentialsApi api) {
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

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Reading punishments.
 *
 * <p>The standing state is one answer rather than three, because "is this player in trouble" is one question. All
 * four lookups are started before any is waited on, so asking for the whole picture costs no more than asking for
 * the ban alone.
 */
public final class ModerationRoutes {

    /** The most history rows one request can ask for. */
    private static final int HISTORY_CAP = 200;

    private ModerationRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/sanctions", Scopes.READ, request -> standing(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/history", Scopes.READ, request -> history(api, request)));
    }

    private static HttpResponse standing(UxmEssentialsApi api, RestRequest request) {
        UxmModerationQuery moderation = moderationOf(api);
        UUID playerId = request.uuidParameter("uuid");

        CompletableFuture<Optional<UxmSanction>> ban = moderation.ban(playerId);
        CompletableFuture<Optional<UxmSanction>> mute = moderation.mute(playerId);
        CompletableFuture<Optional<UxmSanction>> jail = moderation.jail(playerId);
        CompletableFuture<List<com.uxplima.uxmessentials.api.view.UxmWarn>> warns = moderation.warns(playerId);

        JsonObject payload = new JsonObject();
        payload.add("ban", sanction(ban));
        payload.add("mute", sanction(mute));
        payload.add("jail", sanction(jail));
        payload.add("warns", Views.each(Reads.await(warns), Views::warn));
        return Json.ok(payload);
    }

    private static HttpResponse history(UxmEssentialsApi api, RestRequest request) {
        int limit = Reads.limit(request, 25, HISTORY_CAP);
        return Reads.list(
                Reads.await(moderationOf(api).history(request.uuidParameter("uuid"), limit)), Views::sanctionRecord);
    }

    private static JsonElement sanction(CompletableFuture<Optional<UxmSanction>> pending) {
        return Reads.await(pending).map(Views::sanction).orElse(JsonNull.INSTANCE);
    }

    private static UxmModerationQuery moderationOf(UxmEssentialsApi api) {
        return Reads.module(api.moderation(), "moderation");
    }
}

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmModerationActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Punishments: reading them, and handing them out.
 *
 * <p>The standing state is one answer rather than three, because "is this player in trouble" is one question. All
 * four lookups are started before any is waited on, so asking for the whole picture costs no more than asking for
 * the ban alone.
 *
 * <p>Bans, mutes and jails take an optional {@code duration-seconds}. Leaving it out means permanent, which is the
 * same thing leaving it out means on the command, and is why it is written as an absent field rather than as a
 * zero somebody could send by accident.
 */
public final class ModerationRoutes {

    /** The most history rows one request can ask for. */
    private static final int HISTORY_CAP = 200;

    private ModerationRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/sanctions", Scopes.READ, request -> standing(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/history", Scopes.READ, request -> history(api, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/ban", Scopes.WRITE, request -> ban(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/unban", Scopes.WRITE, request -> unban(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/mute", Scopes.WRITE, request -> mute(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/unmute", Scopes.WRITE, request -> unmute(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/kick", Scopes.WRITE, request -> kick(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/warn", Scopes.WRITE, request -> warn(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/jail", Scopes.WRITE, request -> jail(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/unjail", Scopes.WRITE, request -> unjail(actions, request)));
    }

    private static HttpResponse standing(UxmEssentialsApi api, RestRequest request) {
        UxmModerationQuery moderation = moderationOf(api);
        UUID playerId = request.uuidParameter("uuid");

        CompletableFuture<Optional<UxmSanction>> ban = moderation.ban(playerId);
        CompletableFuture<Optional<UxmSanction>> mute = moderation.mute(playerId);
        CompletableFuture<Optional<UxmSanction>> jail = moderation.jail(playerId);
        CompletableFuture<List<UxmWarn>> warns = moderation.warns(playerId);

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

    private static HttpResponse ban(ActionsFor actions, RestRequest request) {
        UxmModerationActions moderation = writes(actions, request);
        UUID target = request.uuidParameter("uuid");
        Body body = Body.of(request);
        Optional<String> reason = body.optionalText("reason");

        return Writes.result(
                body.seconds("duration-seconds")
                        .map(length -> reason.map(why -> moderation.tempBan(target, length, why))
                                .orElseGet(() -> moderation.tempBan(target, length)))
                        .orElseGet(() ->
                                reason.map(why -> moderation.ban(target, why)).orElseGet(() -> moderation.ban(target))),
                Views::sanction);
    }

    private static HttpResponse mute(ActionsFor actions, RestRequest request) {
        UxmModerationActions moderation = writes(actions, request);
        UUID target = request.uuidParameter("uuid");
        Body body = Body.of(request);
        Optional<String> reason = body.optionalText("reason");

        return Writes.result(
                body.seconds("duration-seconds")
                        .map(length -> reason.map(why -> moderation.tempMute(target, length, why))
                                .orElseGet(() -> moderation.tempMute(target, length)))
                        .orElseGet(() -> reason.map(why -> moderation.mute(target, why))
                                .orElseGet(() -> moderation.mute(target))),
                Views::sanction);
    }

    private static HttpResponse kick(ActionsFor actions, RestRequest request) {
        UxmModerationActions moderation = writes(actions, request);
        UUID target = request.uuidParameter("uuid");
        Optional<String> reason = Body.of(request).optionalText("reason");

        return Writes.outcome(reason.map(why -> moderation.kick(target, why)).orElseGet(() -> moderation.kick(target)));
    }

    /** A warning always says why: an entry on somebody's record with no reason on it is not worth writing. */
    private static HttpResponse warn(ActionsFor actions, RestRequest request) {
        return Writes.result(
                writes(actions, request)
                        .warn(request.uuidParameter("uuid"), Body.of(request).text("reason")),
                Views::warn);
    }

    private static HttpResponse jail(ActionsFor actions, RestRequest request) {
        UxmModerationActions moderation = writes(actions, request);
        UUID target = request.uuidParameter("uuid");
        Body body = Body.of(request);
        String jail = body.text("jail");
        String reason = body.optionalText("reason").orElse("");
        Optional<Duration> length = body.seconds("duration-seconds");

        return Writes.result(
                length.map(howLong -> moderation.jail(target, jail, howLong, reason))
                        .orElseGet(() -> moderation.jail(target, jail, reason)),
                Views::sanction);
    }

    private static HttpResponse unban(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).unban(request.uuidParameter("uuid")));
    }

    private static HttpResponse unmute(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).unmute(request.uuidParameter("uuid")));
    }

    private static HttpResponse unjail(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).unjail(request.uuidParameter("uuid")));
    }

    private static JsonElement sanction(CompletableFuture<Optional<UxmSanction>> pending) {
        return Reads.await(pending).map(Views::sanction).orElse(JsonNull.INSTANCE);
    }

    private static UxmModerationQuery moderationOf(UxmEssentialsApi api) {
        return Reads.module(api.moderation(), "moderation");
    }

    private static UxmModerationActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).moderation(), "moderation");
    }
}

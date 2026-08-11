package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.google.gson.JsonNull;
import com.uxplima.uxmessentials.api.action.UxmDiscordLinkActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmDiscordLinkQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Discord bindings: both directions of the lookup, and the one write.
 *
 * <p>A bot on the Discord side holds a snowflake and wants the account; a web panel holds the account and wants the
 * snowflake. Both are the same row read from either end, so both are here. There is no route that creates a binding:
 * a link is only real once the player proved it in game, and a binding written over HTTP would say something the
 * player never agreed to.
 */
public final class DiscordLinkRoutes {

    private DiscordLinkRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/discord", Scopes.READ, request -> byPlayer(api, request)),
                Route.of("GET", PREFIX + "/discord/{id}", Scopes.READ, request -> byDiscordId(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/discord/unlink",
                        Scopes.WRITE,
                        request -> unlink(actions, request)));
    }

    /** The binding a player has, or {@code null} rather than a {@code 404}: unlinked is an answer, not a miss. */
    private static HttpResponse byPlayer(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(Reads.await(reads(api).of(request.uuidParameter("uuid")))
                .map(Views::discordLink)
                .orElse(JsonNull.INSTANCE));
    }

    /**
     * The same row from the Discord side.
     *
     * <p>An id that is not a snowflake answers {@code null} rather than {@code 400}, because a bot passing through
     * whatever a user typed asks this a lot, and "nobody is linked to that" is the honest answer either way.
     */
    private static HttpResponse byDiscordId(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(Reads.await(reads(api).byDiscordId(request.parameter("id")))
                .map(Views::discordLink)
                .orElse(JsonNull.INSTANCE));
    }

    private static HttpResponse unlink(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).unlink(request.uuidParameter("uuid")));
    }

    private static UxmDiscordLinkQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.discordLink(), "discordlink");
    }

    private static UxmDiscordLinkActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).discordLink(), "discordlink");
    }
}

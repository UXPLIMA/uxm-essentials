package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmPresenceActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Who is away from their keyboard, and putting somebody there. */
public final class PresenceRoutes {

    private PresenceRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/presence/afk", Scopes.READ, request -> afk(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/presence", Scopes.READ, request -> presence(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/presence/afk",
                        Scopes.WRITE,
                        request -> setAfk(actions, request)));
    }

    /**
     * One player's presence.
     *
     * <p>Absent means offline: presence is held for players who are here, and a uuid nobody has seen is a
     * {@code 404} rather than a made-up record of somebody who has never been away.
     */
    private static HttpResponse presence(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(
                Reads.module(api.presence(), "presence").of(request.uuidParameter("uuid")),
                "player online with that id",
                Views::presence);
    }

    private static HttpResponse afk(UxmEssentialsApi api) {
        return Reads.list(Reads.module(api.presence(), "presence").afk(), Views::presence);
    }

    /**
     * Put somebody away or bring them back.
     *
     * <p>A {@code reason} implies away, since there is nothing to say about somebody who is here. Without one,
     * {@code away} decides, and it defaults to true so the common call is the short one.
     */
    private static HttpResponse setAfk(ActionsFor actions, RestRequest request) {
        UxmPresenceActions presence = writes(actions, request);
        UUID playerId = request.uuidParameter("uuid");
        Body body = Body.of(request);
        Optional<String> reason = body.optionalText("reason");

        return Writes.outcome(reason.map(why -> presence.setAfk(playerId, why))
                .orElseGet(() -> presence.setAfk(playerId, body.flag("away", true))));
    }

    private static UxmPresenceActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).presence(), "presence");
    }
}

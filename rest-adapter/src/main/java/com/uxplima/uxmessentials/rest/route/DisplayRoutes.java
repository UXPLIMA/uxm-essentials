package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;

/**
 * Redrawing a player's tab list or their nametag.
 *
 * <p>Two contexts in one file, which is the exception the shape earns: each publishes exactly one verb and nothing
 * to read, so a file apiece would be two route tables of one line. They are still separate modules and each answers
 * {@code 503} on its own when the operator has it switched off.
 *
 * <p>Write-only for the reason the Java surface is: what the tab list and the nametag say is authored in config and
 * repainted on a timer, so there is no per-player state to read and nothing to set. The one useful ask is to bring
 * the repaint forward, which is what a panel wants after it has changed a rank or a placeholder.
 */
public final class DisplayRoutes {

    private DisplayRoutes() {}

    public static List<Route> of(ActionsFor actions) {
        return List.of(
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/tablist/refresh",
                        Scopes.WRITE,
                        request -> tablist(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/nametag/refresh",
                        Scopes.WRITE,
                        request -> nametag(actions, request)));
    }

    private static HttpResponse tablist(ActionsFor actions, RestRequest request) {
        return Writes.outcome(Reads.module(actions.actingFor(request.caller()).tablist(), "tablist")
                .refresh(request.uuidParameter("uuid")));
    }

    private static HttpResponse nametag(ActionsFor actions, RestRequest request) {
        return Writes.outcome(Reads.module(actions.actingFor(request.caller()).nametags(), "nametags")
                .refresh(request.uuidParameter("uuid")));
    }
}

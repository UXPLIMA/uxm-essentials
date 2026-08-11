package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.uxplima.uxmessentials.api.action.UxmInvRollbackActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmInvRollbackQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Inventory snapshots: what is held for a player, and putting one back. */
public final class InvRollbackRoutes {

    private InvRollbackRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/snapshots", Scopes.READ, request -> snapshots(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/snapshots/restore",
                        Scopes.WRITE,
                        request -> restore(actions, request)));
    }

    /** The snapshots held for a player, newest first; the items are not in them and cannot be. */
    private static HttpResponse snapshots(UxmEssentialsApi api, RestRequest request) {
        return Reads.list(Reads.await(reads(api).of(request.uuidParameter("uuid"))), Views::snapshot);
    }

    /**
     * Put one back, naming it by id in the body.
     *
     * <p>The same safety copy the command takes is taken here, and the player has to be online, because a snapshot
     * is applied to a live inventory and never written to disk.
     */
    private static HttpResponse restore(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request)
                .restore(request.uuidParameter("uuid"), Body.of(request).uuid("snapshot")));
    }

    private static UxmInvRollbackQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.invRollback(), "invrollback");
    }

    private static UxmInvRollbackActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).invRollback(), "invrollback");
    }
}

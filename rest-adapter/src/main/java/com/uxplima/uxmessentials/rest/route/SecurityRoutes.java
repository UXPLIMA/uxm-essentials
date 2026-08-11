package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.uxplima.uxmessentials.api.action.UxmSecurityActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmSecurityQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Two-factor security: what an account holds, and the two safe writes.
 *
 * <p>No factor material crosses this boundary in either direction. There is nothing to read a PIN or an
 * authenticator secret with, and nothing to set one with: the enrolment a player did in game is the only one there
 * is.
 */
public final class SecurityRoutes {

    private SecurityRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/security", Scopes.READ, request -> status(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/security/force",
                        Scopes.WRITE,
                        request -> force(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/security/unlock",
                        Scopes.WRITE,
                        request -> unlock(actions, request)));
    }

    /** Which factors are on file and whether the account is inside a lockout window right now. */
    private static HttpResponse status(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(Views.securityStatus(Reads.await(reads(api).of(request.uuidParameter("uuid")))));
    }

    /** Forget the account's trusted devices, so its next join has to prove the factor again. */
    private static HttpResponse force(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).forceVerification(request.uuidParameter("uuid")));
    }

    /** End a lockout early. A lockout the operator writes to the ban list is lifted with the unban, not here. */
    private static HttpResponse unlock(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).clearLockout(request.uuidParameter("uuid")));
    }

    private static UxmSecurityQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.security(), "security");
    }

    private static UxmSecurityActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).security(), "security");
    }
}

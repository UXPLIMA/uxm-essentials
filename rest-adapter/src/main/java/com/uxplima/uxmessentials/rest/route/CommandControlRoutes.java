package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmCommandControlQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Whether a command would be stopped for a player, and by which rule.
 *
 * <p>Read-only, because the gate has nothing to write: the rules are the operator's config, and a panel that wanted
 * to change them would be editing that file rather than posting here.
 *
 * <p>The command is a query parameter rather than a path segment. A command root can carry a {@code namespace:}
 * prefix, and a colon in a path is a fight nobody needs to have.
 *
 * <p>An offline player is a {@code 404}: the answer depends on the world they are standing in and the permissions
 * they hold, and neither exists for somebody who is not here.
 */
public final class CommandControlRoutes {

    private CommandControlRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/command-check", Scopes.READ, request -> check(api, request)));
    }

    private static HttpResponse check(UxmEssentialsApi api, RestRequest request) {
        UxmCommandControlQuery query = Reads.module(api.commandControl(), "commandcontrol");
        UUID playerId = request.uuidParameter("uuid");
        return Reads.found(
                Reads.await(query.check(playerId, command(request))),
                "player online to check a command for",
                Views::commandCheck);
    }

    /** The command to ask about, which is the whole point of the request and so is required. */
    private static String command(RestRequest request) {
        String asked = request.http()
                .queryParam("command")
                .orElseThrow(() -> new HttpException(
                        HttpStatus.BAD_REQUEST, "pass the command to check as ?command=, for example ?command=fly"));
        if (asked.isBlank()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "command must not be blank");
        }
        return asked;
    }
}

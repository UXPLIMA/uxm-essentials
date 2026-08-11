package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmHologramsActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmHologramsQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Which holograms exist and what they say, and putting one up or editing it.
 *
 * <p>Every write names an {@code actor} in its body, for the reason the NPC routes do: the plugin runs the same use
 * case the command runs, and that use case is written for a player rather than for a token.
 *
 * <p>The line verbs are one path with the number in the body rather than three paths, because they are three
 * spellings of the same edit and a consumer that has just read a hologram already has the numbers in hand.
 */
public final class HologramsRoutes {

    private HologramsRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/holograms", Scopes.READ, request -> list(api)),
                Route.of("GET", PREFIX + "/holograms/{name}", Scopes.READ, request -> one(api, request)),
                Route.of("POST", PREFIX + "/holograms", Scopes.WRITE, request -> create(actions, request)),
                Route.of("POST", PREFIX + "/holograms/{name}/move", Scopes.WRITE, request -> move(actions, request)),
                Route.of("POST", PREFIX + "/holograms/{name}/lines", Scopes.WRITE, request -> lines(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/holograms/{name}/command",
                        Scopes.WRITE,
                        request -> command(actions, request)),
                Route.of(
                        "POST",
                        PREFIX + "/holograms/{name}/delete",
                        Scopes.WRITE,
                        request -> delete(actions, request)));
    }

    private static HttpResponse list(UxmEssentialsApi api) {
        UxmHologramsQuery query = Reads.module(api.holograms(), "holograms");
        return Reads.list(Reads.await(query.list()), Views::hologram);
    }

    private static HttpResponse one(UxmEssentialsApi api, RestRequest request) {
        UxmHologramsQuery query = Reads.module(api.holograms(), "holograms");
        return Reads.found(Reads.await(query.get(request.parameter("name"))), "hologram", Views::hologram);
    }

    private static HttpResponse create(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(writes(actions, request)
                .create(actor(body), body.text("name"), body.location("location"), body.text("line")));
    }

    private static HttpResponse move(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(writes(actions, request).move(actor(body), name(request), body.location("location")));
    }

    /**
     * One line, added, replaced or removed.
     *
     * <p>No {@code line} number means adding one to the bottom. A number with {@code text} replaces that line, and a
     * number without it removes that line, which is the shortest way to say all three without inventing a verb
     * field the body would then have to be checked against.
     */
    private static HttpResponse lines(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        UxmHologramsActions writes = writes(actions, request);
        String hologram = name(request);
        if (!body.has("line")) {
            return Writes.outcome(writes.addLine(actor(body), hologram, body.text("text")));
        }
        int line = body.integer("line", 0);
        if (line < 1) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "line counts from one, and " + line + " does not");
        }
        return Writes.outcome(
                body.has("text")
                        ? writes.setLine(actor(body), hologram, line, body.text("text"))
                        : writes.removeLine(actor(body), hologram, line));
    }

    /** {@code command} is the command a click runs; sending it as null unbinds it. */
    private static HttpResponse command(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        UxmHologramsActions writes = writes(actions, request);
        return Writes.outcome(body.optionalText("command")
                .map(command -> writes.setClickCommand(actor(body), name(request), command))
                .orElseGet(() -> writes.clearClickCommand(actor(body), name(request))));
    }

    private static HttpResponse delete(ActionsFor actions, RestRequest request) {
        return Writes.outcome(writes(actions, request).delete(actor(Body.of(request)), name(request)));
    }

    private static String name(RestRequest request) {
        return request.parameter("name");
    }

    private static UUID actor(Body body) {
        return body.uuid("actor");
    }

    private static UxmHologramsActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).holograms(), "holograms");
    }
}

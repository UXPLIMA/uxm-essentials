package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmNpcActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmNpcQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Which NPCs exist, and putting one up or taking it down.
 *
 * <p>Every write names an {@code actor} in its body rather than taking it from the token. The plugin charges the
 * actor's NPC limit and records them as the owner, and a token is not a player: naming one keeps an operator's list
 * of NPCs attributable to somebody who can be asked about it.
 */
public final class NpcRoutes {

    private NpcRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/npcs", Scopes.READ, request -> list(api, request)),
                Route.of("GET", PREFIX + "/npcs/{name}", Scopes.READ, request -> one(api, request)),
                Route.of("POST", PREFIX + "/npcs", Scopes.WRITE, request -> create(actions, request)),
                Route.of("POST", PREFIX + "/npcs/{name}/move", Scopes.WRITE, request -> move(actions, request)),
                Route.of("POST", PREFIX + "/npcs/{name}/skin", Scopes.WRITE, request -> skin(actions, request)),
                Route.of("POST", PREFIX + "/npcs/{name}/name", Scopes.WRITE, request -> displayName(actions, request)),
                Route.of("POST", PREFIX + "/npcs/{name}/command", Scopes.WRITE, request -> command(actions, request)),
                Route.of("POST", PREFIX + "/npcs/{name}/delete", Scopes.WRITE, request -> delete(actions, request)));
    }

    /** Every NPC, or only one player's when {@code owner} is given. */
    private static HttpResponse list(UxmEssentialsApi api, RestRequest request) {
        UxmNpcQuery query = Reads.module(api.npc(), "npc");
        return Reads.uuidQuery(request, "owner")
                .map(owner -> Reads.list(Reads.await(query.ownedBy(owner)), Views::npc))
                .orElseGet(() -> Reads.list(Reads.await(query.list()), Views::npc));
    }

    private static HttpResponse one(UxmEssentialsApi api, RestRequest request) {
        UxmNpcQuery query = Reads.module(api.npc(), "npc");
        return Reads.found(Reads.await(query.get(request.parameter("name"))), "npc", Views::npc);
    }

    private static HttpResponse create(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(
                writes(actions, request).create(actor(body), body.text("name"), body.location("location")));
    }

    private static HttpResponse move(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(writes(actions, request).move(actor(body), name(request), body.location("location")));
    }

    /** {@code skin} names the account whose skin to wear; sending it as null takes the skin back off. */
    private static HttpResponse skin(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        UxmNpcActions writes = writes(actions, request);
        return Writes.outcome(body.optionalText("skin")
                .map(owner -> writes.setSkin(actor(body), name(request), owner))
                .orElseGet(() -> writes.clearSkin(actor(body), name(request))));
    }

    /**
     * {@code name} is the label to show. Sending it as null goes back to showing the NPC's id, and sending
     * {@code "hidden": true} shows nothing at all, which is a third state rather than the same one.
     */
    private static HttpResponse displayName(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        UxmNpcActions writes = writes(actions, request);
        String npc = name(request);
        if (body.flag("hidden", false)) {
            return Writes.outcome(writes.hideDisplayName(actor(body), npc));
        }
        return Writes.outcome(body.optionalText("name")
                .map(label -> writes.setDisplayName(actor(body), npc, label))
                .orElseGet(() -> writes.clearDisplayName(actor(body), npc)));
    }

    /** {@code command} is the command a click runs; sending it as null unbinds it. */
    private static HttpResponse command(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        UxmNpcActions writes = writes(actions, request);
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

    private static UxmNpcActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).npc(), "npc");
    }
}

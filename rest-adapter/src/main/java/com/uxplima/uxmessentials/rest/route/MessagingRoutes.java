package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.action.UxmMessagingActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Mail, ignores, and sending a message or a letter. */
public final class MessagingRoutes {

    private MessagingRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/mail", Scopes.READ, request -> mailbox(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/ignores", Scopes.READ, request -> ignores(api, request)),
                Route.of("POST", PREFIX + "/messaging/message", Scopes.WRITE, request -> sendMessage(actions, request)),
                Route.of("POST", PREFIX + "/players/{uuid}/mail", Scopes.WRITE, request -> sendMail(actions, request)));
    }

    /** The mailbox with its unread count, which is the number anything drawing an inbox needs beside it. */
    private static HttpResponse mailbox(UxmEssentialsApi api, RestRequest request) {
        UxmMessagingQuery messaging = messagingOf(api);
        UUID playerId = request.uuidParameter("uuid");

        JsonObject payload = new JsonObject();
        payload.add("mail", Views.each(Reads.await(messaging.mailbox(playerId)), Views::mail));
        payload.addProperty("unread", Reads.await(messaging.unreadMail(playerId)));
        payload.addProperty("accepts-messages", messaging.acceptsMessages(playerId));
        payload.addProperty("social-spying", messaging.isSocialSpying(playerId));
        return Json.ok(payload);
    }

    private static HttpResponse ignores(UxmEssentialsApi api, RestRequest request) {
        return Reads.list(Reads.await(messagingOf(api).ignoreList(request.uuidParameter("uuid"))), Views::ignore);
    }

    private static UxmMessagingQuery messagingOf(UxmEssentialsApi api) {
        return Reads.module(api.messaging(), "messaging");
    }

    /**
     * A private message from one player to another, exactly as {@code /msg} would send it.
     *
     * <p>Both ends are players and both have to be here, because a private message is a conversation rather than a
     * notice: mutes, ignores and the recipient's own toggle all apply, and the sender sees their own copy.
     */
    private static HttpResponse sendMessage(ActionsFor actions, RestRequest request) {
        Body body = Body.of(request);
        return Writes.outcome(
                writes(actions, request).sendMessage(body.uuid("from"), body.uuid("to"), body.text("body")));
    }

    /**
     * Mail, from a player when {@code from} names one and from the server otherwise.
     *
     * <p>Server mail is how a plugin tells somebody something that has to keep until they are next on: it waits in
     * the mailbox rather than being lost to an offline player, and no mute or ignore applies to it, because neither
     * can be about a plugin.
     */
    private static HttpResponse sendMail(ActionsFor actions, RestRequest request) {
        UxmMessagingActions messaging = writes(actions, request);
        UUID recipient = request.uuidParameter("uuid");
        Body body = Body.of(request);
        String text = body.text("body");

        return Writes.outcome(
                body.has("from")
                        ? messaging.sendMail(body.uuid("from"), recipient, text)
                        : messaging.sendMail(recipient, text));
    }

    private static UxmMessagingActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).messaging(), "messaging");
    }
}

package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading mail and who somebody is ignoring. */
public final class MessagingRoutes {

    private MessagingRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/players/{uuid}/mail", Scopes.READ, request -> mailbox(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/ignores", Scopes.READ, request -> ignores(api, request)));
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
}

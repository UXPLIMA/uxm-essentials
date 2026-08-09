package com.uxplima.uxmessentials.rest.route;

import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.Routes;
import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestServer;

/**
 * Calling a route the way the listener does, for the tests that are about what a route answers.
 *
 * <p>It goes through {@link RestServer#dispatch}, so a test sees the same refusals a client would: a module that is
 * off comes back as a status and a code rather than as an exception the test would have to know to catch.
 */
final class Calls {

    private static final Logger QUIET = quietLogger();

    private Calls() {}

    /** {@code GET path}, with no query string. */
    static Answer get(UxmEssentialsApi api, String path) {
        return get(api, path, Map.of());
    }

    /** {@code GET path?query}. */
    static Answer get(UxmEssentialsApi api, String path, Map<String, String> query) {
        return call(api, unreachable(), new HttpRequest("GET", path, query, Map.of(), ""));
    }

    /** {@code POST path} with a JSON body, writing through {@code actions}. */
    static Answer post(UxmEssentialsApi api, UxmActions actions, String path, String body) {
        return call(api, caller -> actions, new HttpRequest("POST", path, Map.of(), Map.of(), body));
    }

    /** {@code POST path} with no body, for the verbs that need none. */
    static Answer post(UxmEssentialsApi api, UxmActions actions, String path) {
        return post(api, actions, path, "{}");
    }

    /** {@code POST path} through an {@link ActionsFor} the test supplies, for the tests about attribution. */
    static Answer postThrough(UxmEssentialsApi api, ActionsFor actions, String path) {
        return call(api, actions, new HttpRequest("POST", path, Map.of(), Map.of(), "{}"));
    }

    /** The name {@link RestServer.RequestFilter#allowing()} attributes a request to. */
    static final String CALLER = "test";

    private static Answer call(UxmEssentialsApi api, ActionsFor actions, HttpRequest request) {
        HttpResponse response =
                RestServer.dispatch(Routes.build(api, actions), RestServer.RequestFilter.allowing(), request, QUIET);
        return new Answer(
                response.status(), JsonParser.parseString(response.body()).getAsJsonObject());
    }

    /** A read cannot reach the action surface; if one ever does, say so loudly rather than hand it a mock. */
    private static ActionsFor unreachable() {
        return caller -> {
            throw new AssertionError("a read asked for the action surface");
        };
    }

    /** A handler that throws on purpose logs at SEVERE; keep that out of the test output. */
    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(Calls.class.getName());
        logger.setUseParentHandlers(false);
        return logger;
    }

    /** What came back, already parsed. */
    record Answer(int status, JsonObject envelope) {

        boolean ok() {
            return envelope.get("ok").getAsBoolean();
        }

        String code() {
            return envelope.get("code").getAsString();
        }

        String message() {
            return envelope.get("message").getAsString();
        }

        JsonElement data() {
            return envelope.get("data");
        }

        JsonObject object() {
            return data().getAsJsonObject();
        }

        JsonArray array() {
            return data().getAsJsonArray();
        }
    }
}

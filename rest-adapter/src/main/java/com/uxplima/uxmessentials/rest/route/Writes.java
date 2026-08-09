package com.uxplima.uxmessentials.rest.route;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;

/**
 * What every write route does with what the plugin hands back.
 *
 * <p>A refusal is an answer, not an error. "Not enough money" is the plugin working correctly, and it comes back as
 * {@code 200} carrying {@code ok:false} and the same code the Java API returns, so a consumer branches on the same
 * string over HTTP as in process. The HTTP statuses stay for the things HTTP is about: a malformed body, a token
 * without the scope, a module that is off.
 */
public final class Writes {

    private Writes() {}

    /** An action with nothing to hand back: done, or refused with a code. */
    public static HttpResponse outcome(CompletableFuture<UxmOutcome> pending) {
        UxmOutcome outcome = Reads.await(pending);
        return outcome.failure().map(Writes::refusal).orElseGet(Json::done);
    }

    /** An action that produces something: the thing, or refused with a code. */
    public static <T> HttpResponse result(CompletableFuture<UxmResult<T>> pending, Function<T, JsonElement> render) {
        UxmResult<T> result = Reads.await(pending);
        Optional<UxmFailure> failure = result.failure();
        if (failure.isPresent()) {
            return refusal(failure.get());
        }
        return Json.ok(render.apply(result.valueOrThrow()));
    }

    private static HttpResponse refusal(UxmFailure failure) {
        return Json.refused(failure.code(), failure.message());
    }
}

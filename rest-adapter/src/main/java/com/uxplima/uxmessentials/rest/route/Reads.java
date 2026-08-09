package com.uxplima.uxmessentials.rest.route;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * The four things every read route does, so no route writes them out again.
 *
 * <p>Ask whether the module is on. Wait for the answer. Say so when the thing asked for is not there. Render.
 */
public final class Reads {

    /**
     * How long a handler waits for the plugin to answer.
     *
     * <p>Long enough that a database under load still finishes, short enough that a connection cannot be held open
     * indefinitely by something that will never come back. Past it the client is told the truth: the server was
     * asked and did not answer.
     */
    static final long TIMEOUT_SECONDS = 10;

    private Reads() {}

    /**
     * The query surface for a module, or {@code 503} when the operator has that module switched off.
     *
     * <p>A distinct status because it is a distinct thing. The path is right, the token is right, and the answer is
     * that this server does not run that feature: a consumer can show that, and would only be confused by a 404.
     */
    public static <T> T module(Optional<T> query, String moduleId) {
        return query.orElseThrow(() -> new HttpException(
                HttpStatus.SERVICE_UNAVAILABLE, "the " + moduleId + " module is switched off on this server"));
    }

    /**
     * Wait for a published query to answer.
     *
     * <p>Blocking is right here and nowhere else in the plugin: this is the add-on's own virtual thread, holding
     * nothing the server needs, and the request cannot be answered until the value arrives.
     */
    public static <T> T await(CompletableFuture<T> future) {
        return await(future, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** The same wait, with the deadline named, so a test can watch one expire without waiting ten seconds. */
    static <T> T await(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException tooSlow) {
            throw new HttpException(HttpStatus.GATEWAY_TIMEOUT, "the server did not answer in time");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "the listener is shutting down");
        } catch (ExecutionException | CompletionException failed) {
            // Unwrap once so the log shows what actually went wrong rather than the wrapper the future put on it.
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException("the query failed: " + cause.getMessage(), cause);
        }
    }

    /** Render {@code value}, or answer {@code 404} saying what was not found. */
    public static <T> HttpResponse found(Optional<T> value, String what, Function<T, JsonElement> render) {
        return Json.ok(render.apply(
                value.orElseThrow(() -> new HttpException(HttpStatus.NOT_FOUND, "no " + what + " like that"))));
    }

    /**
     * The {@code limit} query parameter, defaulted and capped.
     *
     * <p>Capped rather than obeyed, because a leaderboard is a database query and {@code ?limit=1000000} is not a
     * request anybody makes on purpose. The cap is silent: the answer is simply the first {@code max}, which is what
     * a client asking for more than exists already handles.
     */
    public static int limit(RestRequest request, int fallback, int max) {
        int asked = request.intQuery("limit", fallback);
        if (asked < 1) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "limit must be at least one: " + asked);
        }
        return Math.min(asked, max);
    }

    /** A query parameter read as a uuid, or empty when it was not sent. */
    public static Optional<UUID> uuidQuery(RestRequest request, String name) {
        return request.http().queryParam(name).map(raw -> {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException notAUuid) {
                throw new HttpException(HttpStatus.BAD_REQUEST, name + " is not a uuid: " + raw);
            }
        });
    }

    /** Render a collection as an array, which is an empty array when there is nothing in it. */
    public static <T> HttpResponse list(Collection<T> items, Function<T, JsonElement> render) {
        return Json.ok(Views.each(items, render));
    }
}

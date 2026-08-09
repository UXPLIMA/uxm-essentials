package com.uxplima.uxmessentials.rest.http;

/**
 * What a route does.
 *
 * <p>A handler answers rather than throws: a refusal the server understood is a {@code 200} carrying a code. It
 * may throw an {@link HttpException} for a request that never made sense, and anything else it throws becomes a
 * {@code 500} with the detail in the log rather than in the body.
 */
@FunctionalInterface
public interface RestHandler {

    HttpResponse handle(RestRequest request);
}

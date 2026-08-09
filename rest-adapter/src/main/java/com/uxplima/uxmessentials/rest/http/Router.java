package com.uxplima.uxmessentials.rest.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The route table, and the one thing that decides which route a request belongs to.
 *
 * <p>Routes are tried in the order they were added, and the first whose path matches wins. A path that matches on
 * a method nobody registered answers {@code 405} rather than {@code 404}, because "you used the wrong verb" and
 * "there is nothing here" are different problems and a consumer debugging one should not be told the other.
 */
public final class Router {

    private final List<Route> routes = new ArrayList<>();

    /** Add a route. Returns this, so a table reads as one statement. */
    public Router add(Route route) {
        routes.add(Objects.requireNonNull(route, "route"));
        return this;
    }

    /** Add every route in {@code more}, which is how a context contributes its whole table at once. */
    public Router addAll(List<Route> more) {
        more.forEach(this::add);
        return this;
    }

    /** Every route, in the order they were added. */
    public List<Route> routes() {
        return List.copyOf(routes);
    }

    /** The route and parameters for this request, or empty when nothing answers it. */
    public Optional<Match> find(HttpRequest request) {
        boolean pathExists = false;
        for (Route route : routes) {
            Optional<Map<String, String>> parameters = route.path().match(request.path());
            if (parameters.isEmpty()) {
                continue;
            }
            pathExists = true;
            if (route.method().equals(request.method())) {
                return Optional.of(new Match(route, new RestRequest(request, parameters.get(), "unauthenticated")));
            }
        }
        if (pathExists) {
            throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED, request.method() + " is not used on that path");
        }
        return Optional.empty();
    }

    /** A route and the request it accepted, ready to run. */
    public record Match(Route route, RestRequest request) {}
}

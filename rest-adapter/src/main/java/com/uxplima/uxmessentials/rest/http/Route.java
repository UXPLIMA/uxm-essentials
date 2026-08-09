package com.uxplima.uxmessentials.rest.http;

import java.util.Locale;
import java.util.Objects;

/**
 * One method, one path shape, one handler.
 *
 * @param method the verb this route answers, upper-cased
 * @param path the path shape, with {@code {name}} where a value varies
 * @param scope what a token needs to be allowed to use it
 * @param handler what it does
 */
public record Route(String method, PathPattern path, String scope, RestHandler handler) {

    public Route {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(handler, "handler");
        method = method.toUpperCase(Locale.ROOT);
    }

    /** A route built from the path as it is written in the table. */
    public static Route of(String method, String path, String scope, RestHandler handler) {
        return new Route(method, PathPattern.of(path), scope, handler);
    }

    /** How this route reads in the route table and in the golden file the guard pins. */
    public String describe() {
        return method + " " + path.source() + " [" + scope + "]";
    }
}

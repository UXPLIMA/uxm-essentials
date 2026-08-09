package com.uxplima.uxmessentials.rest.http;

/**
 * The handful of statuses this listener ever sends, with the reason phrase that goes beside each.
 *
 * <p>A short list on purpose. A refusal the server understood is not an HTTP error: it comes back as {@code 200}
 * carrying {@code ok:false} and a code, so a consumer branches on the same string it would branch on in Java. The
 * statuses here are for the things HTTP itself is about: who is asking, whether the route exists, whether the
 * request was well formed.
 */
public final class HttpStatus {

    /** The request was understood and answered, whether or not the operation it asked for went ahead. */
    public static final int OK = 200;

    /** The request was malformed: bad JSON, a missing field, an id that is not a uuid. */
    public static final int BAD_REQUEST = 400;

    /** No token, or one nobody issued. Says nothing about whether the label exists. */
    public static final int UNAUTHORIZED = 401;

    /** A real token without the scope this route needs. */
    public static final int FORBIDDEN = 403;

    /** No route answers that method and path. */
    public static final int NOT_FOUND = 404;

    /** The route exists but not for that method. */
    public static final int METHOD_NOT_ALLOWED = 405;

    /** The body is longer than the listener will read. */
    public static final int PAYLOAD_TOO_LARGE = 413;

    /** The token is over its per-minute limit. */
    public static final int TOO_MANY_REQUESTS = 429;

    /** Something in here broke. The body says as little as is useful. */
    public static final int INTERNAL_ERROR = 500;

    /** A request shape this listener does not read, such as a chunked body. */
    public static final int NOT_IMPLEMENTED = 501;

    /** The server was asked and did not answer in time. */
    public static final int GATEWAY_TIMEOUT = 504;

    /** The module behind the route is switched off, which is a different thing from the route being wrong. */
    public static final int SERVICE_UNAVAILABLE = 503;

    private HttpStatus() {}

    /** The reason phrase for {@code status}, or a neutral one for anything not listed. */
    public static String reason(int status) {
        return switch (status) {
            case OK -> "OK";
            case BAD_REQUEST -> "Bad Request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not Found";
            case METHOD_NOT_ALLOWED -> "Method Not Allowed";
            case PAYLOAD_TOO_LARGE -> "Payload Too Large";
            case TOO_MANY_REQUESTS -> "Too Many Requests";
            case INTERNAL_ERROR -> "Internal Server Error";
            case NOT_IMPLEMENTED -> "Not Implemented";
            case GATEWAY_TIMEOUT -> "Gateway Timeout";
            case SERVICE_UNAVAILABLE -> "Service Unavailable";
            default -> "Status";
        };
    }
}

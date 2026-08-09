package com.uxplima.uxmessentials.rest.http;

import java.io.Serial;

/**
 * A request that cannot be answered normally, carrying the status to send back.
 *
 * <p>Thrown from the reading and routing layers, where there is no handler yet to return a response from. A
 * handler that has one returns it instead: an exception is for a request that never became one.
 */
public final class HttpException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;

    public HttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status this failure should be sent as. */
    public int status() {
        return status;
    }
}

package com.uxplima.uxmessentials.guard.application.port;

/** The region store could not be read or written. A failed load locks the affected worlds. */
public final class GuardStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuardStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public GuardStoreException(String message) {
        super(message);
    }
}

package com.uxplima.uxmessentials.discord;

/**
 * Raised when the {@link DiscordGateway} cannot establish its connection. A bad token, an interrupted login,
 * or a client build failure. The bootstrap catches it, logs the reason on the bridge's plugin logger, and
 * self-disables.
 */
public final class DiscordConnectException extends Exception {

    public DiscordConnectException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiscordConnectException(String message) {
        super(message);
    }
}

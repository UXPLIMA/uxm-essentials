package com.uxplima.uxmessentials.rest;

import java.util.Objects;

/**
 * What an operator decided about the listener.
 *
 * <p>The defaults are the cautious ones: off, on the loopback address, with a limit that a panel refreshing every
 * few seconds never notices and a script in a loop hits quickly.
 *
 * @param enabled whether to bind at all
 * @param bind the address to listen on
 * @param port the port to listen on
 * @param requestsPerMinute how many requests one token may make in a minute
 */
public record RestConfig(boolean enabled, String bind, int port, int requestsPerMinute) {

    /** The shipped defaults, and what a config that failed to load falls back to. */
    public static final RestConfig DORMANT = new RestConfig(false, "127.0.0.1", 8123, 120);

    public RestConfig {
        Objects.requireNonNull(bind, "bind");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535: " + port);
        }
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requests-per-minute must be at least one: " + requestsPerMinute);
        }
    }

    /** Whether this configuration says to listen on something other than the loopback address. */
    public boolean isExposed() {
        return !"127.0.0.1".equals(bind) && !"localhost".equals(bind) && !"::1".equals(bind);
    }
}

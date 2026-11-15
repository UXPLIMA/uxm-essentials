package com.uxplima.uxmessentials.worlds.application.port;

/**
 * Moves the worlds recorded on one server to another, in the shared database. A port because the move is one
 * conditional write across every backend's rows, and because the moved rows have to reach every backend's cache.
 */
@FunctionalInterface
public interface WorldServers {

    /**
     * Off-tick: record {@code to} on every world recorded on {@code from}, with its settings, answering how many moved.
     * A name {@code to} already runs stays on {@code from}.
     */
    int rename(String from, String to);
}

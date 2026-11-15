package com.uxplima.uxmessentials.homes.application.port;

/**
 * The server every home records, rewritten across all owners at once. A port of its own because the per-owner
 * {@link HomeRepository} has no way to reach every row, and because a rename touches homes no backend has cached.
 */
public interface HomeServers {

    /** Record {@code to} on every home recorded on {@code from}, returning how many homes changed. */
    int rename(String from, String to);
}

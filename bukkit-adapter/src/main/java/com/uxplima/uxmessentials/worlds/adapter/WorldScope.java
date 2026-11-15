package com.uxplima.uxmessentials.worlds.adapter;

import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.NetworkConfig;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import org.jspecify.annotations.NullMarked;

/**
 * The server a backend keeps its managed worlds under. Only a backend on a network under a name of its own records
 * one: off a network every backend is {@code server-1}, and so is one that never set its name, so worlds recorded
 * under it would vanish the day the operator names the server. Such a backend keeps its worlds under
 * {@value NetworkWorld#UNSCOPED}, exactly as before servers were recorded, and the claim copies them onto its real name
 * once it has one.
 */
@NullMarked
public final class WorldScope {

    /** What a recorded server name may hold: the column is 64 characters and the name goes to the proxy as is. */
    private static final Pattern RECORDABLE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private WorldScope() {}

    /** The scope for a backend with the network {@code enabled} or not, configured as {@code serverName}. */
    public static String of(boolean enabled, String serverName) {
        Objects.requireNonNull(serverName, "serverName");
        return records(enabled, serverName) ? serverName : NetworkWorld.UNSCOPED;
    }

    /** Whether a backend so configured records its own server on its worlds. */
    public static boolean records(boolean enabled, String serverName) {
        Objects.requireNonNull(serverName, "serverName");
        return enabled
                && !NetworkConfig.isDefaultServerName(serverName)
                && !serverName.equals(NetworkWorld.UNSCOPED)
                && RECORDABLE.matcher(serverName).matches();
    }
}

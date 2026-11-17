package com.uxplima.uxmessentials.shared.adapter.inbound.ip;

import java.util.Objects;

import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.RelayAddresses;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Says at start how this server will read the addresses Bedrock players arrive from, so an operator whose Bedrock
 * players all share Geyser's address learns it from the log rather than from an IP ban that bars them all.
 *
 * <p>Floodgate puts each Bedrock client's own address on its connection, so with Floodgate here there is nothing to
 * say. Geyser here without it is the setup to name: Geyser may hand over every player under its own address, and only
 * a loopback one is recognised without being listed.
 */
@NullMarked
public final class RelaySetup {

    private RelaySetup() {}

    /** Log the setup the installed plugins and {@code relays} describe. */
    public static void report(PluginManager plugins, RelayAddresses relays, Logger log) {
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(relays, "relays");
        Objects.requireNonNull(log, "log");
        if (plugins.isPluginEnabled("Geyser-Spigot") && !plugins.isPluginEnabled("floodgate")) {
            log.warn(
                    "event=relay_setup setup=geyser_without_floodgate relays_listed={} detail=Bedrock players may all"
                            + " arrive from Geyser's address. Loopback is treated as shared; list any other address Geyser"
                            + " connects from under relay-addresses in config.conf, or install Floodgate so each player keeps"
                            + " their own.",
                    relays.listed());
        } else if (relays.listed()) {
            log.info("event=relay_setup setup=listed detail=the listed relay-addresses are treated as shared");
        }
    }
}

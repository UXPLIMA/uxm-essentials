package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.uxplima.uxmessentials.worlds.application.ResolvePortalDestination;
import com.uxplima.uxmessentials.worlds.domain.PortalDestination;
import com.uxplima.uxmessentials.worlds.domain.PortalKind;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * Redirects nether and end portals to the destination configured by the source world's per-kind link, leaving
 * every other teleport cause — and every world without a link — to vanilla. The {@link ResolvePortalDestination}
 * use case owns the link lookup and coordinate scaling; this adapter only translates the live Bukkit
 * {@link PlayerPortalEvent} into and out of that decision. When the linked world is unloaded or missing the
 * portal is left to vanilla and a single warning is logged per offending target name.
 */
@NullMarked
public final class WorldPortalListener implements Listener {

    private final ResolvePortalDestination resolve;
    private final Server server;
    private final Logger log;
    private final Set<String> warnedMissingTargets = ConcurrentHashMap.newKeySet();

    public WorldPortalListener(ResolvePortalDestination resolve, Server server, Logger log) {
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Location relocated = destinationFor(event.getCause(), event.getFrom()).orElse(null);
        if (relocated == null) {
            return;
        }
        event.setTo(relocated);
        event.setCanCreatePortal(true);
    }

    /**
     * The live exit location a portal of {@code cause} entered at {@code from} should redirect to, or empty when
     * the cause is not a portal, the source world is unparseable, no link resolves, or the linked world is not
     * loaded. Package-private so the cause-to-location pipeline is unit-testable without firing a Bukkit event.
     */
    Optional<Location> destinationFor(PlayerTeleportEvent.TeleportCause cause, Location from) {
        PortalKind kind;
        switch (cause) {
            case NETHER_PORTAL -> kind = PortalKind.NETHER;
            case END_PORTAL -> kind = PortalKind.END;
            default -> {
                return Optional.empty();
            }
        }
        World fromWorld = from.getWorld();
        if (fromWorld == null) {
            return Optional.empty();
        }
        WorldName source;
        try {
            source = WorldName.of(fromWorld.getName());
        } catch (IllegalArgumentException badName) {
            return Optional.empty();
        }
        Optional<PortalDestination> dest = resolve.resolve(source, kind, from.getX(), from.getY(), from.getZ());
        if (dest.isEmpty()) {
            return Optional.empty();
        }
        PortalDestination d = dest.get();
        World target = server.getWorld(d.world().value());
        if (target == null) {
            warnMissing(d.world().value());
            return Optional.empty();
        }
        return Optional.of(new Location(target, d.x(), d.y(), d.z(), from.getYaw(), from.getPitch()));
    }

    private void warnMissing(String worldName) {
        if (warnedMissingTargets.add(worldName)) {
            log.warning(
                    "Portal link target world '" + worldName + "' is not loaded; portal left to vanilla behaviour.");
        }
    }
}

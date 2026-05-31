package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.moderation.adapter.ModerationSettings;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Sanctions} adapter: the live-player side of a sanction on top of the Paper {@link Server} and the
 * kernel {@link Scheduler}. Every per-player action hops to the player's entity thread through the scheduler
 * (Folia-valid) and silently no-ops when the player is offline. The frozen set is session-scoped runtime
 * state owned here — a {@code ConcurrentHashMap}-backed UUID set the move listener consults — so a relog
 * clears a freeze; it is not DB-backed.
 *
 * <p>The jail teleport resolves the named jail's location from {@link ModerationSettings}; an unresolved jail
 * (missing world) is a no-op rather than a crash. Release teleports back to the world spawn.
 */
@NullMarked
public final class BukkitSanctions implements Sanctions {

    private final Server server;
    private final Scheduler scheduler;
    private final ModerationSettings settings;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public BukkitSanctions(Server server, Scheduler scheduler, ModerationSettings settings) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reasonText, "reasonText");
        Component reason = MiniMessage.miniMessage().deserialize(reasonText);
        scheduler.onEntity(target, () -> withPlayer(target, player -> player.kick(reason)));
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        return server.getOnlinePlayers().stream().map(BukkitRefs::toRef).toList();
    }

    @Override
    public void freeze(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        frozen.add(target.uuid());
    }

    @Override
    public void unfreeze(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        frozen.remove(target.uuid());
    }

    @Override
    public boolean isFrozen(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return frozen.contains(target.uuid());
    }

    /** True when {@code uuid} is currently frozen — the move listener's hot-path check. */
    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Drop every freeze, called on module stop so no session state survives. */
    public void clear() {
        frozen.clear();
    }

    @Override
    public void sendToJail(PlayerRef target, String jail) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(jail, "jail");
        Optional<Location> location = jailLocation(jail);
        location.ifPresent(
                loc -> scheduler.onEntity(target, () -> withPlayer(target, player -> teleport(player, loc))));
    }

    @Override
    public void releaseFromJail(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        scheduler.onEntity(target, () -> withPlayer(target, this::sendToWorldSpawn));
    }

    private Optional<Location> jailLocation(String jail) {
        return settings.location(jail).flatMap(loc -> {
            World world = server.getWorld(loc.world());
            return world == null ? Optional.empty() : Optional.of(new Location(world, loc.x(), loc.y(), loc.z()));
        });
    }

    private void sendToWorldSpawn(Player player) {
        teleport(player, player.getWorld().getSpawnLocation());
    }

    private static void teleport(Player player, Location to) {
        var ignored = player.teleportAsync(to);
    }

    private void withPlayer(PlayerRef ref, PlayerAction action) {
        Player player = server.getPlayer(ref.uuid());
        if (player != null) {
            action.run(player);
        }
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player);
    }
}

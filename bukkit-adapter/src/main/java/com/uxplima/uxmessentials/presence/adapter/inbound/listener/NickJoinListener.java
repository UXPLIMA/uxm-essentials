package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.application.PlayerNicks;
import com.uxplima.uxmessentials.presence.domain.NickState;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Shows a joining player's nick, read during their login, before the join line is drawn with their display name.
 *
 * <p>An earlier version kept the nick in each server's player data, checked for its shape only, and never read it
 * back. A player who still carries one there and has no nick in the database takes it over, off the tick, under the
 * same rules as {@code /nick}: a nick another account holds as its name or its nick is dropped. The old value is
 * removed once the database has settled it, so a failed write leaves it for the next join.
 */
@NullMarked
public final class NickJoinListener implements Listener {

    private final PlayerNicks nicks;
    private final Scheduler scheduler;
    private final Logger log;
    private final NamespacedKey legacyKey;

    public NickJoinListener(Plugin plugin, PlayerNicks nicks, Scheduler scheduler, Logger log) {
        this.legacyKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "display-nick");
        this.nicks = Objects.requireNonNull(nicks, "nicks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Low: before the join line (high) is drawn with the display name. */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        nicks.joined(player.getUniqueId());
        takeOverLegacy(player);
    }

    private void takeOverLegacy(Player player) {
        String legacy = player.getPersistentDataContainer().get(legacyKey, PersistentDataType.STRING);
        if (legacy == null) {
            return;
        }
        NickState stored = nicks.stored(player.getUniqueId()).orElse(null);
        if (stored == null) {
            return; // the login read failed: whether the database holds a nick is not known
        }
        PlayerRef ref = BukkitRefs.toRef(player);
        if (stored.version() != 0L) {
            removeLegacy(ref);
            return;
        }
        scheduler.async(() -> {
            try {
                if (nicks.adoptLegacy(ref.uuid(), ref.name(), legacy)) {
                    removeLegacy(ref);
                }
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=legacy_nick_kept player={} reason={}; taken over at the next join",
                        ref.name(),
                        String.valueOf(unreachable.getMessage()));
            }
        });
    }

    /** Remove the old value on the player's own thread; a player who left keeps it until the next join. */
    private void removeLegacy(PlayerRef ref) {
        scheduler.onEntity(ref, () -> {
            Player here = org.bukkit.Bukkit.getPlayer(ref.uuid());
            if (here != null) {
                here.getPersistentDataContainer().remove(legacyKey);
            }
        });
    }
}

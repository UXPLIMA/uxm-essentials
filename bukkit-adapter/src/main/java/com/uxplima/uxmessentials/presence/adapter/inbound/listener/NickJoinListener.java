package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.application.PlayerNicks;
import com.uxplima.uxmessentials.presence.domain.NickState;
import org.jspecify.annotations.NullMarked;

/**
 * Shows a joining player's nick, read during their login, before the join line is drawn with their display name.
 *
 * <p>An earlier version kept the nick in each server's player data and never read it back. A player who still carries
 * one there and has no nick in the database takes it over at their next join to that server. The old value is removed
 * once the database is known to hold a nick, and nothing is taken for a player whose login read failed.
 */
@NullMarked
public final class NickJoinListener implements Listener {

    private final PlayerNicks nicks;
    private final NamespacedKey legacyKey;

    public NickJoinListener(Plugin plugin, PlayerNicks nicks) {
        this.legacyKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "display-nick");
        this.nicks = Objects.requireNonNull(nicks, "nicks");
    }

    /** Low: before the join line (high) is drawn with the display name. */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        takeOverLegacy(player);
        nicks.joined(player.getUniqueId());
    }

    private void takeOverLegacy(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        String legacy = data.get(legacyKey, PersistentDataType.STRING);
        if (legacy == null) {
            return;
        }
        NickState stored = nicks.stored(player.getUniqueId()).orElse(null);
        if (stored == null) {
            return; // the login read failed: whether the database holds a nick is not known
        }
        if (stored.changedAtMillis() == 0L && !legacy.isBlank()) {
            nicks.set(player.getUniqueId(), legacy.strip());
        }
        data.remove(legacyKey);
    }
}

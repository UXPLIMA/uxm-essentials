package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link BroadcastOptOutStore} implementation. The {@code /broadcasttoggle} preference survives relog, so it
 * is stamped in PDC under a single pre-created key, mirroring the messaging context's {@code PdcMessageToggleStore}.
 * A fresh player who never ran {@code /broadcasttoggle} is opted in and receives announcements.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. The opt-out bit is read and written through the owning {@code Player}, so the
 * toggle runs on the player's region thread; the announcer fan-out reads the same flag on each broadcast tick,
 * also through the live player, so a read and a write never touch the same off-heap container concurrently. The
 * {@link NamespacedKey} is built once in the constructor, never on a hot path.
 */
@NullMarked
public final class PdcBroadcastOptOutStore implements BroadcastOptOutStore {

    private final NamespacedKey optOutKey;

    public PdcBroadcastOptOutStore(Plugin plugin) {
        this.optOutKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "broadcast-opt-out");
    }

    @Override
    public boolean receivesBroadcasts(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // an offline player is not part of the live broadcast audience
        }
        return !optedOut(player);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // nothing to flip for an offline player; report opted-in
        }
        boolean nowOptedOut = !optedOut(player);
        player.getPersistentDataContainer().set(optOutKey, PersistentDataType.BYTE, nowOptedOut ? (byte) 1 : (byte) 0);
        return nowOptedOut;
    }

    @Override
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        // The opt-out bit is a persistent per-player preference, not session state: it must survive the quit so a
        // returning player keeps their choice. There is therefore nothing to drop here.
    }

    private boolean optedOut(Player player) {
        return player.getPersistentDataContainer().getOrDefault(optOutKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }
}

package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MessageToggleStore} implementation. The {@code /msgtoggle} switch is a per-player preference
 * that survives relog, so it is stamped in PDC under a single pre-created key (mirroring the teleport
 * context's {@code /tptoggle} flag). A fresh player who never ran {@code /msgtoggle} accepts messages.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. PDC reads and writes go through the owning {@code Player}, so this is
 * touched on the command/region thread. The {@link NamespacedKey} is built once in the constructor, never on
 * a hot path.
 */
@NullMarked
public final class PdcMessageToggleStore implements MessageToggleStore {

    private final NamespacedKey toggleKey;

    public PdcMessageToggleStore(Plugin plugin) {
        this.toggleKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "msg-toggle-off");
    }

    @Override
    public boolean acceptsMessages(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // an offline target cannot receive a real-time message anyway
        }
        return accepts(player);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true;
        }
        boolean nowAccepting = !accepts(player);
        PdcFlag.set(player.getPersistentDataContainer(), toggleKey, !nowAccepting);
        return nowAccepting;
    }

    private boolean accepts(Player player) {
        return !PdcFlag.get(player.getPersistentDataContainer(), toggleKey);
    }
}

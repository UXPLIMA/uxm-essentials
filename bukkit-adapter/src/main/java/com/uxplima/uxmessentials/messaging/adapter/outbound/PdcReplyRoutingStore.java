package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ReplyRoutingStore} implementation. The {@code /rtoggle} switch is a per-player preference that
 * survives relog, so it is stamped in PDC under a single pre-created key (mirroring the {@code /msgtoggle}
 * flag). A fresh player who never ran {@code /rtoggle} takes part in reply routing.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. PDC reads and writes go through the owning {@code Player}, so this is
 * touched on the command/region thread. The {@link NamespacedKey} is built once in the constructor, never on
 * a hot path.
 */
@NullMarked
public final class PdcReplyRoutingStore implements ReplyRoutingStore {

    private final NamespacedKey toggleKey;

    public PdcReplyRoutingStore(Plugin plugin) {
        this.toggleKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "reply-toggle-off");
    }

    @Override
    public boolean acceptsReplies(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // an offline target cannot receive a real-time reply anyway
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
        player.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, nowAccepting ? (byte) 0 : (byte) 1);
        return nowAccepting;
    }

    private boolean accepts(Player player) {
        return player.getPersistentDataContainer().getOrDefault(toggleKey, PersistentDataType.BYTE, (byte) 0) == 0;
    }
}

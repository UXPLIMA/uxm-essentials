package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * One per-player on/off switch stamped in PDC, the mechanic behind {@code /msgtoggle} and {@code /rtoggle}.
 * The flag stores the <em>off</em> state, so a fresh player who never ran the command is accepting.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. PDC reads and writes go through the owning {@code Player}, so this is
 * touched on the command/region thread. The {@link NamespacedKey} is built once in the constructor, never on
 * a hot path.
 */
@NullMarked
final class PdcToggle {

    private final NamespacedKey toggleKey;

    PdcToggle(Plugin plugin, String key) {
        this.toggleKey =
                new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), Objects.requireNonNull(key, "key"));
    }

    /** Whether {@code who} is accepting. An offline player is not: nothing real-time can reach them anyway. */
    boolean accepts(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        return player != null && accepts(player);
    }

    /** Flip {@code who}'s switch and report the state it landed in. An offline player is left untouched. */
    boolean toggle(PlayerRef who) {
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

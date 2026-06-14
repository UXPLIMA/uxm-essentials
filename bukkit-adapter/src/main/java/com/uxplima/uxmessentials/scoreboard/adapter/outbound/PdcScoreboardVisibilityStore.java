package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ScoreboardVisibilityStore} implementation. The {@code /scoreboard} preference survives relog, so it is
 * stamped in PDC under a single pre-created key, mirroring the communication context's {@code PdcBroadcastOptOutStore}.
 * A fresh player who never ran {@code /scoreboard} is shown the display (the {@code scoreboard-hidden} byte defaults
 * to {@code 0}).
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. The hidden bit is read and written through the owning {@code Player}, so the toggle
 * runs on the player's region thread and the render loop reads the same flag on each refresh through the live player
 * (the render fan-out hops to the entity thread before reading), so a read and a write never touch the same off-heap
 * container concurrently. The {@link NamespacedKey} is built once in the constructor, never on a hot path.
 */
@NullMarked
public final class PdcScoreboardVisibilityStore implements ScoreboardVisibilityStore {

    private final NamespacedKey hiddenKey;

    public PdcScoreboardVisibilityStore(Plugin plugin) {
        this.hiddenKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "scoreboard-hidden");
    }

    @Override
    public boolean hidden(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true; // an offline player has no live board to render; treat as hidden
        }
        return isHidden(player);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // nothing to flip for an offline player; report shown
        }
        boolean nowHidden = !isHidden(player);
        PdcFlag.set(player.getPersistentDataContainer(), hiddenKey, nowHidden);
        return nowHidden;
    }

    @Override
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        // The hidden bit is a persistent per-player preference, not session state: it must survive the quit so a
        // returning player keeps their choice. There is therefore nothing to drop here.
    }

    private boolean isHidden(Player player) {
        return PdcFlag.get(player.getPersistentDataContainer(), hiddenKey);
    }
}

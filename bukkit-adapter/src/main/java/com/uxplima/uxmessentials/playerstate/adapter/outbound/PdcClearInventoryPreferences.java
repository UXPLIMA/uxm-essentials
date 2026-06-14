package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.ClearInventoryPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ClearInventoryPreferences} implementation. The {@code /clearinventoryconfirmtoggle} preference is
 * a per-player flag that survives relog, so it is stamped in PDC under a single pre-created key. The key is
 * built once via {@link NamespacedKey#fromString(String)} so the playerstate context stays free of a
 * {@code Plugin} handle (it owns no database and no scheduler-side plugin reference), matching the rest of its
 * adapters.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the owning live {@code Player}; an offline
 * player defaults to confirm-off and is never stamped. The {@link NamespacedKey} is created once as a constant,
 * never on a hot path.
 */
@NullMarked
public final class PdcClearInventoryPreferences implements ClearInventoryPreferences {

    private static final NamespacedKey CONFIRM_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:ci-confirm"), "ci-confirm key");

    @Override
    public boolean confirmEnabled(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // an offline player defaults to confirm-off; nothing to read
        }
        return PdcFlag.get(player.getPersistentDataContainer(), CONFIRM_KEY);
    }

    @Override
    public boolean toggleConfirm(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        boolean nowOn = !confirmEnabled(who);
        PdcFlag.set(player.getPersistentDataContainer(), CONFIRM_KEY, nowOn);
        return nowOn;
    }
}

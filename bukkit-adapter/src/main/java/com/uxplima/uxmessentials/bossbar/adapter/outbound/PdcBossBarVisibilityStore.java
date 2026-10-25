package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A player's choice to hide their boss bars, kept on the player, so it survives a relog. A player who never chose has
 * no entry and reads {@code hidden-by-default}, so changing that default reaches everybody who has not chosen.
 */
@NullMarked
public final class PdcBossBarVisibilityStore implements BossBarVisibilityStore {

    private final NamespacedKey key;
    private final BooleanSupplier hiddenByDefault;

    public PdcBossBarVisibilityStore(Plugin plugin, BooleanSupplier hiddenByDefault) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "bossbar-hidden");
        this.hiddenByDefault = Objects.requireNonNull(hiddenByDefault, "hiddenByDefault");
    }

    @Override
    public boolean hidden(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true;
        }
        @Nullable Boolean chosen = player.getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
        return chosen != null ? chosen : hiddenByDefault.getAsBoolean();
    }

    @Override
    public void setHidden(PlayerRef who, boolean hidden) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player != null) {
            player.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, hidden);
        }
    }
}

package com.uxplima.uxmessentials.security.adapter;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.security.domain.SpectatorPolicy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Holds a frozen spectator in a mode that can click, and puts their own mode back afterwards.
 *
 * <p>A spectator cannot click any inventory the server opens for them: the keypad renders, every button is visible,
 * and not one of them responds. Without this, a player who reaches the join freeze in spectator mode is stuck with
 * no way to prove anything and no way out, which is the worst failure this module can have. So the freeze moves
 * them into the configured {@link SpectatorPolicy} mode for its duration and restores what they had on the way out.
 *
 * <p>The original mode is stamped into the player's persistent data container rather than a map in memory, because
 * the restore has to survive the one case a map cannot: the server going down while somebody is mid-verification.
 * On the next join the stamp is still there and {@link #restore} puts them back before anything else looks at their
 * mode. The stamp is written only when absent, so a second freeze cannot overwrite a pending restore with the
 * temporary mode and strand the player in it permanently.
 *
 * <p>Every method touches the live player, so every call must already be on that player's own region thread.
 */
@NullMarked
public final class FreezeGameMode {

    private final NamespacedKey key;
    private final SpectatorPolicy policy;

    public FreezeGameMode(Plugin plugin, SpectatorPolicy policy) {
        Objects.requireNonNull(plugin, "plugin");
        this.key = new NamespacedKey(plugin, "verify_gamemode");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Move {@code player} out of spectator mode for the length of the freeze, remembering what they were in. A
     * no-op for a player who is not a spectator, or when the policy is {@link SpectatorPolicy#NONE}.
     */
    public void apply(Player player) {
        Objects.requireNonNull(player, "player");
        if (policy == SpectatorPolicy.NONE || player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        // Write the stamp only when there is not one already: a re-freeze must not record the mode we ourselves put
        // the player in, or the restore would hand them adventure mode forever.
        if (!player.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, GameMode.SPECTATOR.name());
        }
        player.setGameMode(target());
    }

    /**
     * Put back the mode {@code player} held before the freeze, if one was recorded, and drop the stamp. Safe to call
     * for any player at any time, so the unfreeze, the quit and the next join can all call it without checking.
     */
    public void restore(Player player) {
        Objects.requireNonNull(player, "player");
        String stamped = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stamped == null) {
            return;
        }
        player.getPersistentDataContainer().remove(key);
        GameMode previous = parse(stamped);
        if (previous != null && player.getGameMode() != previous) {
            player.setGameMode(previous);
        }
    }

    private GameMode target() {
        return policy == SpectatorPolicy.SURVIVAL ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    /** The mode named by a stamp, or null when the stamp is unreadable, in which case the player keeps what they have. */
    private static @Nullable GameMode parse(String stamped) {
        for (GameMode mode : GameMode.values()) {
            if (mode.name().equals(stamped.toUpperCase(Locale.ROOT))) {
                return mode;
            }
        }
        return null;
    }
}

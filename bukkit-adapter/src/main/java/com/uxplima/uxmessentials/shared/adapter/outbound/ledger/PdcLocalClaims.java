package com.uxplima.uxmessentials.shared.adapter.outbound.ledger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.shared.adapter.outbound.cooldown.PdcCooldowns;
import com.uxplima.uxmessentials.shared.application.ledger.LocalClaims;
import org.jspecify.annotations.NullMarked;

/**
 * Reads what this server's PDC holds of a player's one-time kit claims and cooldowns, for the network to carry into
 * the database, and marks the player once that is done.
 *
 * <p>The mark is per server because the PDC is: every backend of a network carries its own copy once, and the
 * database keeps the stricter value. It is never carried twice, so a {@code /kit reset} on the network is not undone
 * by a stamp still sitting in an old PDC.
 *
 * <p>Every method touches the player's PDC and runs on the thread that owns the player.
 */
@NullMarked
public final class PdcLocalClaims {

    private final Plugin plugin;
    private final NamespacedKey carriedKey;

    public PdcLocalClaims(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.carriedKey = new NamespacedKey(plugin, "network-claims-carried");
    }

    /** Whether this server already carried {@code player}'s PDC claims into the database. */
    public boolean carried(Player player) {
        return player.getPersistentDataContainer().has(carriedKey, PersistentDataType.BYTE);
    }

    /** Mark {@code player}'s PDC claims as carried, so they are never carried again. */
    public void markCarried(Player player) {
        player.getPersistentDataContainer().set(carriedKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** The one-time kits and the cooldown stamps {@code player}'s PDC holds. */
    public LocalClaims read(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String namespace = carriedKey.getNamespace();
        Map<String, Long> cooldowns = new HashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            String path = key.getKey();
            if (!key.getNamespace().equals(namespace)
                    || !path.startsWith(PdcCooldowns.KEY_PREFIX)
                    || !pdc.has(key, PersistentDataType.LONG)) {
                continue;
            }
            Long readyAt = pdc.get(key, PersistentDataType.LONG);
            if (readyAt != null) {
                cooldowns.put(path.substring(PdcCooldowns.KEY_PREFIX.length()), readyAt);
            }
        }
        return new LocalClaims(PdcKitClaims.claimedIds(plugin, pdc), cooldowns);
    }
}

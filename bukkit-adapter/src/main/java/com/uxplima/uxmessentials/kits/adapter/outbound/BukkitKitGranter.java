package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link KitGranter} implementation: decodes each {@link KitItem} through the {@link KitItemCodec} and
 * adds it to the recipient's inventory, dropping any overflow at their feet. The grant runs on the claim
 * command thread, which is the player's own region thread (a command source resolves to the player's
 * region), so the inventory mutation is region-correct without an extra hop.
 *
 * <p>An offline recipient has no inventory to fill, so the grant no-ops and reports that everything "fit" —
 * there is nothing to overflow. A single corrupt {@code kits.conf} entry is logged and skipped rather than
 * aborting the whole claim, so one bad item never denies a player the rest of a kit.
 */
@NullMarked
public final class BukkitKitGranter implements KitGranter {

    private final Logger log;

    public BukkitKitGranter(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Grant grant(PlayerRef recipient, List<KitItem> items) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(items, "items");
        Player player = Bukkit.getPlayer(recipient.uuid());
        if (player == null) {
            return Grant.complete();
        }
        boolean fit = true;
        for (KitItem item : items) {
            fit &= give(player, item);
        }
        return fit ? Grant.complete() : Grant.overflowed();
    }

    private boolean give(Player player, KitItem item) {
        ItemStack stack = decode(item);
        if (stack == null) {
            return true; // a corrupt entry was skipped; it neither filled nor overflowed the inventory
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (overflow.isEmpty()) {
            return true;
        }
        overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        return false;
    }

    private @org.jspecify.annotations.Nullable ItemStack decode(KitItem item) {
        try {
            return KitItemCodec.decode(item);
        } catch (IllegalArgumentException malformed) {
            log.warn("skipping unreadable kit item payload: " + malformed.getMessage());
            return null;
        }
    }
}

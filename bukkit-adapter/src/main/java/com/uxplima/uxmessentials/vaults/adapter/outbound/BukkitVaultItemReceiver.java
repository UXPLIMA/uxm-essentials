package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReceiver;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;

/**
 * Puts returned vault items into the owner's live inventory. The player is looked up here, on the owner's own thread,
 * never captured beforehand: after a relog a captured {@code Player} is a dead object, and items added to it would be
 * lost. What does not fit, and anything a failed add left behind, is reported back to be kept for the next join.
 */
@NullMarked
public final class BukkitVaultItemReceiver implements VaultItemReceiver {

    @Override
    public Optional<Receipt> give(PlayerRef owner, VaultContents items) {
        Player live = Bukkit.getPlayer(owner.uuid());
        if (live == null || !live.isOnline()) {
            return Optional.empty();
        }
        ItemStack[] stacks = VaultItemCodec.decodeAll(items);
        List<ItemStack> leftover = new ArrayList<>();
        int given = 0;
        int left = 0;
        for (int slot = 0; slot < stacks.length; slot++) {
            ItemStack stack = stacks[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            try {
                int before = stack.getAmount();
                int notFitting = 0;
                for (ItemStack extra :
                        live.getInventory().addItem(stack.clone()).values()) {
                    leftover.add(extra);
                    notFitting += extra.getAmount();
                }
                given += before - notFitting;
                left += notFitting;
            } catch (RuntimeException failure) {
                // Keep this stack and every one after it, so a failed add loses nothing.
                for (int rest = slot; rest < stacks.length; rest++) {
                    ItemStack kept = stacks[rest];
                    if (kept != null && !kept.getType().isAir()) {
                        leftover.add(kept);
                        left += kept.getAmount();
                    }
                }
                break;
            }
        }
        VaultContents rest = VaultItemCodec.encode(leftover.toArray(ItemStack[]::new));
        return Optional.of(new Receipt(given, rest, left));
    }
}

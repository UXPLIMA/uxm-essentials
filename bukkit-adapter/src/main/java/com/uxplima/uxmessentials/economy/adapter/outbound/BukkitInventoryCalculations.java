package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Denomination;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Handles all inventory scans, mutations, and greedy breakdown change calculations
 * for physical item-backed currencies.
 */
@NullMarked
// Paper deprecated the int-based CustomModelData accessors in favour of the component API; the denomination
// config still carries a single legacy int id, so the int accessors stay until that config model is widened.
@SuppressWarnings("deprecation")
public final class BukkitInventoryCalculations {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Checks if the given item stack matches the denomination's material and custom model data.
     */
    public static boolean matches(ItemStack item, Denomination denomination) {
        if (item.getType() == Material.AIR) {
            return false;
        }
        if (!item.getType().name().equalsIgnoreCase(denomination.material())) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (denomination.customModelData() != null) {
            if (meta == null || !meta.hasCustomModelData()) {
                return false;
            }
            return meta.getCustomModelData() == denomination.customModelData().intValue();
        } else {
            // If the denomination doesn't require custom model data, we don't match items that have one
            // to keep custom coins distinct from vanilla items.
            return meta == null || !meta.hasCustomModelData();
        }
    }

    /**
     * Scans the player's active inventory, counting the total holdings value of the given currency.
     */
    public BigDecimal getBalance(Player player, Currency currency) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            for (Denomination denomination : currency.denominations()) {
                if (matches(item, denomination)) {
                    BigDecimal stackValue = denomination.value().multiply(BigDecimal.valueOf(item.getAmount()));
                    total = total.add(stackValue);
                }
            }
        }
        return total.setScale(currency.precision(), java.math.RoundingMode.HALF_UP);
    }

    /**
     * Credits the player with the specified physical money amount, adding the largest possible denominations.
     * If the inventory is full, leftover items are dropped at the player's location.
     */
    public Result<Unit, TransferError> credit(Player player, Money amount) {
        Currency currency = amount.currency();
        BigDecimal remaining = amount.amount();

        List<Denomination> sortedDenoms = new ArrayList<>(currency.denominations());
        // Sort descending by value (largest first)
        sortedDenoms.sort((d1, d2) -> d2.value().compareTo(d1.value()));

        Map<Denomination, Integer> toAdd = new LinkedHashMap<>();
        for (Denomination denomination : sortedDenoms) {
            BigDecimal[] divideAndRemainder = remaining.divideAndRemainder(denomination.value());
            int count = divideAndRemainder[0].intValue();
            if (count > 0) {
                toAdd.put(denomination, count);
                remaining = divideAndRemainder[1];
            }
        }

        // Apply modifications
        for (Map.Entry<Denomination, Integer> entry : toAdd.entrySet()) {
            Denomination denom = entry.getKey();
            int count = entry.getValue();

            ItemStack stack = createItemStack(denom, count);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack leftoverStack : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverStack);
                }
            }
        }

        return Result.ok();
    }

    /**
     * Debits the player's inventory by removing the exact value.
     * Uses a greedy selection algorithm and breaks down larger denominations to return change if necessary.
     */
    public Result<Unit, TransferError> debit(Player player, Money amount) {
        Currency currency = amount.currency();
        BigDecimal holdings = getBalance(player, currency);

        if (holdings.compareTo(amount.amount()) < 0) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }

        // Count available items in player's inventory
        Map<Denomination, Integer> available = new LinkedHashMap<>();
        for (Denomination denom : currency.denominations()) {
            available.put(denom, 0);
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) {
                continue;
            }
            for (Denomination denom : currency.denominations()) {
                if (matches(item, denom)) {
                    available.put(denom, available.getOrDefault(denom, 0) + item.getAmount());
                }
            }
        }

        List<Denomination> sortedDenoms = new ArrayList<>(currency.denominations());
        // Sort descending (largest first)
        sortedDenoms.sort((d1, d2) -> d2.value().compareTo(d1.value()));

        BigDecimal remaining = amount.amount();
        Map<Denomination, Integer> toRemove = new LinkedHashMap<>();

        // First pass: Pay using existing denominations smaller than or equal to the remaining amount
        for (Denomination denom : sortedDenoms) {
            int denomAvailable = available.getOrDefault(denom, 0);
            int needed = remaining
                    .divide(denom.value(), 0, java.math.RoundingMode.DOWN)
                    .intValue();
            int toUse = Math.min(needed, denomAvailable);

            if (toUse > 0) {
                toRemove.put(denom, toUse);
                remaining = remaining.subtract(denom.value().multiply(BigDecimal.valueOf(toUse)));
            }

            if (remaining.signum() == 0) {
                break;
            }
        }

        Map<Denomination, Integer> toAdd = new LinkedHashMap<>();

        // Second pass: If remaining > 0, we must break down a larger denomination
        if (remaining.signum() > 0) {
            Denomination denomToBreak = null;
            // Sort ascending (smallest first) to find the smallest denomination larger than remaining
            List<Denomination> ascendingDenoms = new ArrayList<>(currency.denominations());
            ascendingDenoms.sort(Comparator.comparing(Denomination::value));

            for (Denomination denom : ascendingDenoms) {
                if (denom.value().compareTo(remaining) > 0) {
                    int availAfterPass = available.getOrDefault(denom, 0) - toRemove.getOrDefault(denom, 0);
                    if (availAfterPass > 0) {
                        denomToBreak = denom;
                        break;
                    }
                }
            }

            if (denomToBreak == null) {
                // Should not happen since holdings >= amount, but fallback in case of rounding/precision
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }

            // Mark 1 of this denomination for removal
            toRemove.put(denomToBreak, toRemove.getOrDefault(denomToBreak, 0) + 1);

            // Calculate change to return
            BigDecimal change = denomToBreak.value().subtract(remaining);
            BigDecimal changeRemaining = change;

            // Sort descending to breakdown the change into largest possible denominations
            for (Denomination denom : sortedDenoms) {
                BigDecimal[] divideAndRemainder = changeRemaining.divideAndRemainder(denom.value());
                int count = divideAndRemainder[0].intValue();
                if (count > 0) {
                    toAdd.put(denom, count);
                    changeRemaining = divideAndRemainder[1];
                }
            }
        }

        // Actually execute the removals from inventory
        for (Map.Entry<Denomination, Integer> entry : toRemove.entrySet()) {
            removeItems(player, entry.getKey(), entry.getValue());
        }

        // Give back change if any
        for (Map.Entry<Denomination, Integer> entry : toAdd.entrySet()) {
            ItemStack changeStack = createItemStack(entry.getKey(), entry.getValue());
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(changeStack);
            if (!leftover.isEmpty()) {
                for (ItemStack leftoverStack : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverStack);
                }
            }
        }

        return Result.ok();
    }

    private void removeItems(Player player, Denomination denom, int amountToRemove) {
        int remaining = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (matches(item, denom)) {
                int count = item.getAmount();
                if (count <= remaining) {
                    player.getInventory().setItem(i, null);
                    remaining -= count;
                } else {
                    item.setAmount(count - remaining);
                    remaining = 0;
                }
            }
            if (remaining == 0) {
                break;
            }
        }
    }

    private ItemStack createItemStack(Denomination denom, int count) {
        ItemStack item = new ItemStack(Material.valueOf(denom.material().toUpperCase(java.util.Locale.ROOT)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (denom.customModelData() != null) {
                meta.setCustomModelData(denom.customModelData());
            }
            // The denomination display name is an operator-configured MiniMessage template, resolved to an
            // Adventure Component — never legacy section/ampersand colour codes.
            meta.displayName(MINI_MESSAGE
                    .deserialize(denom.displayName())
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        item.setAmount(count);
        return item;
    }
}

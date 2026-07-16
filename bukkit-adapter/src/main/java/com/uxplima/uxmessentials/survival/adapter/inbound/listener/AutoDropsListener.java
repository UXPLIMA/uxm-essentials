package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import com.uxplima.uxmessentials.survival.domain.SellPrices;
import com.uxplima.uxmessentials.survival.domain.SmeltMap;
import org.jspecify.annotations.NullMarked;

/**
 * The break-drop pipeline that composes the three auto-* mechanics that all act on {@code BlockBreakEvent}'s drops:
 * auto-smelt, then auto-sell, then auto-pickup. They share one listener because they share one thing — the set of items
 * a break produces — and the spec order is fixed: a drop is smelted first (raw iron becomes an ingot), then the priced
 * ones are sold for coin, then whatever remains is routed to the player's inventory (overflowing to the ground when it
 * is full). Each stage is gated independently by its own config switch, per-player toggle, and use permission, so a
 * server that enables only auto-pickup gets exactly auto-pickup; the listener is only registered at all when at least
 * one of the three is enabled.
 *
 * <h2>Taking over the drops</h2>
 * When any stage is active for this break the listener cancels the vanilla ground drops ({@code setDropItems(false)})
 * and takes ownership of the item set computed from the block against the breaker's tool — so fortune and silk touch
 * are honoured — routing it itself. Auto-sell removes a stack from the set only once the wallet credit has actually
 * succeeded, so a refused deposit (a balance cap, no economy provider) never destroys the item: it falls through to
 * pickup or the ground.
 *
 * <h2>Folia</h2>
 * The break event runs on the region owning the broken block; the inventory, the block's world drops, and the wallet
 * credit are all applied inline on that thread — the block's own region — so no scheduler hop is needed, matching the
 * harvesting and farm-assist listeners.
 */
@NullMarked
public final class AutoDropsListener implements Listener {

    private static final String PICKUP_PERMISSION = "uxmessentials.survival.autopickup";
    private static final String SMELT_PERMISSION = "uxmessentials.survival.autosmelt";
    private static final String SELL_PERMISSION = "uxmessentials.survival.autosell";

    private final boolean pickupEnabled;
    private final boolean transferXp;
    private final boolean smeltEnabled;
    private final boolean sellEnabled;
    private final SmeltMap smeltMap;
    private final SellPrices sellPrices;
    private final Optional<SurvivalSales> sales;
    private final PdcSurvivalToggles toggles;

    public AutoDropsListener(
            boolean pickupEnabled,
            boolean transferXp,
            boolean smeltEnabled,
            SmeltMap smeltMap,
            boolean sellEnabled,
            SellPrices sellPrices,
            Optional<SurvivalSales> sales,
            PdcSurvivalToggles toggles) {
        this.pickupEnabled = pickupEnabled;
        this.transferXp = transferXp;
        this.smeltEnabled = smeltEnabled;
        this.smeltMap = Objects.requireNonNull(smeltMap, "smeltMap");
        this.sellEnabled = sellEnabled;
        this.sellPrices = Objects.requireNonNull(sellPrices, "sellPrices");
        this.sales = Objects.requireNonNull(sales, "sales");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        boolean pickup =
                pickupEnabled && player.hasPermission(PICKUP_PERMISSION) && toggles.autoPickupActive(player, true);
        boolean smelt = smeltEnabled && player.hasPermission(SMELT_PERMISSION) && toggles.autoSmeltActive(player, true);
        boolean sell = sellEnabled
                && sales.isPresent()
                && player.hasPermission(SELL_PERMISSION)
                && toggles.autoSellActive(player, true);
        if (!pickup && !smelt && !sell) {
            return;
        }
        Block block = event.getBlock();
        List<ItemStack> drops =
                new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        event.setDropItems(false);
        if (smelt) {
            drops.replaceAll(this::smelt);
        }
        if (pickup && transferXp && event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop());
            event.setExpToDrop(0);
        }
        if (sell) {
            sell(player, drops);
        }
        route(player, block, drops, pickup);
    }

    /** The smelted counterpart of one drop (same amount), or the drop unchanged when it is not smeltable. */
    private ItemStack smelt(ItemStack item) {
        Optional<String> result = smeltMap.smelted(item.getType().name());
        if (result.isEmpty()) {
            return item;
        }
        Material material = Material.matchMaterial(result.get());
        return material == null ? item : new ItemStack(material, item.getAmount());
    }

    /** Credit the total value of the priced drops in one deposit, removing them only once the credit succeeds. */
    private void sell(Player player, List<ItemStack> drops) {
        List<ItemStack> sold = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : drops) {
            Optional<BigDecimal> value = sellPrices.saleValue(item.getType().name(), item.getAmount());
            if (value.isPresent()) {
                total = total.add(value.get());
                sold.add(item);
            }
        }
        if (total.signum() > 0 && sales.orElseThrow().credit(BukkitRefs.toRef(player), total)) {
            drops.removeAll(sold);
        }
    }

    /** Route the remaining drops: into the inventory when auto-pickup is active (ground overflow), else to the ground. */
    private void route(Player player, Block block, List<ItemStack> drops, boolean pickup) {
        if (drops.isEmpty()) {
            return;
        }
        World world = block.getWorld();
        Location location = block.getLocation();
        for (ItemStack item : drops) {
            if (pickup) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                overflow.values().forEach(left -> world.dropItemNaturally(location, left));
            } else {
                world.dropItemNaturally(location, item);
            }
        }
    }
}

package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.adapter.inbound.event.KitClaimEvent;
import com.uxplima.uxmessentials.kits.adapter.inbound.event.KitPreClaimEvent;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
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
 * there is nothing to overflow. A single corrupt kit item is logged and skipped rather than aborting the
 * whole claim, so one bad item never denies a player the rest of a kit.
 *
 * <p>When a kit opts in with {@code parse-placeholders: true} and PlaceholderAPI is installed, each granted
 * item's display name and lore are resolved for the recipient before placement, so an operator can author an
 * item named {@code <gold>{player}'s Reward}. The flag is off by default, so an existing kit and a server
 * without PlaceholderAPI grant the item's text unchanged.
 */
@NullMarked
public final class BukkitKitGranter implements KitGranter {

    private final Logger log;

    public BukkitKitGranter(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean preGrant(PlayerRef recipient, KitDefinition kit) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(recipient.uuid());
        if (player == null) {
            return true;
        }
        KitPreClaimEvent event = new KitPreClaimEvent(player, kit);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public Grant grant(PlayerRef recipient, KitDefinition kit) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(recipient.uuid());
        if (player == null) {
            return Grant.complete();
        }
        java.util.function.UnaryOperator<String> placeholders = placeholderBridge(recipient, kit);
        boolean fit = true;
        for (KitItem item : kit.items()) {
            fit &= give(player, item, kit.autoEquip(), placeholders);
        }

        // Fire post-claim event
        KitClaimEvent claimEvent = new KitClaimEvent(player, kit);
        Bukkit.getPluginManager().callEvent(claimEvent);

        return fit ? Grant.complete() : Grant.overflowed();
    }

    private boolean give(
            Player player, KitItem item, boolean autoEquip, java.util.function.UnaryOperator<String> placeholders) {
        ItemStack stack = decode(item);
        if (stack == null) {
            return true; // a corrupt entry was skipped; it neither filled nor overflowed the inventory
        }
        applyPlaceholders(stack, placeholders);

        int maxStackSize = stack.getMaxStackSize();
        int amount = stack.getAmount();
        boolean hasOversizedPermission = player.hasPermission("uxmessentials.oversizedstacks");

        if (item.slot().isPresent()) {
            int targetSlot = item.slot().get();
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            if (targetSlot >= 0 && targetSlot < inv.getSize()) {
                ItemStack currentItem = inv.getItem(targetSlot);
                if (isEmpty(currentItem)) {
                    if (amount > maxStackSize && !hasOversizedPermission) {
                        // Split it: maxStackSize in target slot, rest to other slots
                        ItemStack slotStack = stack.clone();
                        slotStack.setAmount(maxStackSize);
                        inv.setItem(targetSlot, slotStack);

                        ItemStack remainingStack = stack.clone();
                        remainingStack.setAmount(amount - maxStackSize);
                        Map<Integer, ItemStack> overflow = inv.addItem(remainingStack);
                        if (overflow.isEmpty()) {
                            return true;
                        }
                        overflow.values()
                                .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                        return false;
                    } else {
                        // Fit directly
                        inv.setItem(targetSlot, stack);
                        return true;
                    }
                }
            }
            // Target slot occupied or invalid, fallback to general inventory placement or autoEquip
        }

        if (autoEquip) {
            org.bukkit.inventory.EquipmentSlot slot = getEquipmentSlot(stack.getType());
            if (slot != null) {
                org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                switch (slot) {
                    case HEAD -> {
                        if (isEmpty(inv.getHelmet())) {
                            inv.setHelmet(stack);
                            return true;
                        }
                    }
                    case CHEST -> {
                        if (isEmpty(inv.getChestplate())) {
                            inv.setChestplate(stack);
                            return true;
                        }
                    }
                    case LEGS -> {
                        if (isEmpty(inv.getLeggings())) {
                            inv.setLeggings(stack);
                            return true;
                        }
                    }
                    case FEET -> {
                        if (isEmpty(inv.getBoots())) {
                            inv.setBoots(stack);
                            return true;
                        }
                    }
                    case OFF_HAND -> {
                        if (isEmpty(inv.getItemInOffHand())) {
                            inv.setItemInOffHand(stack);
                            return true;
                        }
                    }
                    default -> {}
                }
            }
        }

        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        if (hasOversizedPermission) {
            // Find the first empty slot in main inventory (0 to 35)
            int firstEmpty = -1;
            for (int i = 0; i < 36; i++) {
                if (isEmpty(inv.getItem(i))) {
                    firstEmpty = i;
                    break;
                }
            }
            if (firstEmpty != -1) {
                inv.setItem(firstEmpty, stack);
                return true;
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
                return false;
            }
        } else {
            Map<Integer, ItemStack> overflow = inv.addItem(stack);
            if (overflow.isEmpty()) {
                return true;
            }
            overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            return false;
        }
    }

    private @org.jspecify.annotations.Nullable EquipmentSlot getEquipmentSlot(org.bukkit.Material material) {
        try {
            EquipmentSlot slot = material.getEquipmentSlot();
            if (slot != null) {
                return slot;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Some environments (like MockBukkit) throw UnimplementedOperationException or lack this method.
        }
        String name = material.name();
        if (name.endsWith("_HELMET") || name.endsWith("_CAP")) {
            return org.bukkit.inventory.EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.endsWith("_PLATE") || name.equals("ELYTRA")) {
            return org.bukkit.inventory.EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS") || name.endsWith("_PANTS")) {
            return org.bukkit.inventory.EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return org.bukkit.inventory.EquipmentSlot.FEET;
        }
        if (name.equals("SHIELD") || name.equals("TOTEM_OF_UNDYING")) {
            return org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private boolean isEmpty(@org.jspecify.annotations.Nullable ItemStack stack) {
        return stack == null || stack.getType() == org.bukkit.Material.AIR;
    }

    private @org.jspecify.annotations.Nullable ItemStack decode(KitItem item) {
        try {
            return KitItemCodec.decode(item);
        } catch (IllegalArgumentException malformed) {
            log.warn("skipping unreadable kit item payload: " + malformed.getMessage());
            return null;
        }
    }

    /**
     * The per-grant transform applied to each granted item's name and lore: resolve the recipient's
     * PlaceholderAPI tokens when the kit opts in ({@code parse-placeholders: true}) and PlaceholderAPI is
     * installed, the identity otherwise — so an existing kit and a server without PlaceholderAPI both grant
     * the item's text unchanged.
     */
    private UnaryOperator<String> placeholderBridge(PlayerRef recipient, KitDefinition kit) {
        if (kit.parsePlaceholders() && PlaceholderApiSupport.isPresent()) {
            return PlaceholderApiSupport.messageBridge(recipient.uuid());
        }
        return UnaryOperator.identity();
    }

    /**
     * Rewrite {@code stack}'s display name and lore through {@code placeholders}, round-tripping each line as
     * MiniMessage so an item named {@code <gold>{player}'s Reward} resolves for the recipient. A stack with no
     * meta, no name, and no lore is left untouched, and an identity bridge is a no-op.
     */
    private void applyPlaceholders(ItemStack stack, UnaryOperator<String> placeholders) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean changed = rewriteName(meta, placeholders);
        changed |= rewriteLore(meta, placeholders);
        if (changed) {
            stack.setItemMeta(meta);
        }
    }

    private boolean rewriteName(ItemMeta meta, UnaryOperator<String> placeholders) {
        Component name = meta.hasDisplayName() ? meta.displayName() : null;
        if (name == null) {
            return false;
        }
        meta.displayName(resolve(name, placeholders));
        return true;
    }

    private boolean rewriteLore(ItemMeta meta, UnaryOperator<String> placeholders) {
        List<Component> lore = meta.hasLore() ? meta.lore() : null;
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        List<Component> resolved = new ArrayList<>(lore.size());
        for (Component line : lore) {
            resolved.add(resolve(line, placeholders));
        }
        meta.lore(resolved);
        return true;
    }

    /** Serialize {@code component} to MiniMessage, resolve placeholders, and re-deserialize the result. */
    private Component resolve(Component component, UnaryOperator<String> placeholders) {
        MiniMessage mini = MiniMessage.miniMessage();
        return mini.deserialize(placeholders.apply(mini.serialize(component)));
    }
}

package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadgetItems;
import com.uxplima.uxmessentials.staff.adapter.StaffSettings;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-facing {@link StaffLoadoutCapture}: it is the only staff class that touches a live {@code Player},
 * an {@code ItemStack}, or a {@code PotionEffect}. It snapshots the real loadout into the pure
 * {@link SavedLoadout} the use case persists, restores a stored loadout back onto a player, and lays out the
 * gadget hotbar. The item regions ride the same {@link VaultItemCodec} the vaults context uses; the potion
 * effects ride {@link StaffEffectCodec}. The domain never sees a Bukkit type.
 *
 * <p><b>Thread ownership.</b> Every method here reads or writes the live player entity, so each must run on
 * that player's region thread. The caller (the command on enter, the use case path on exit, the listener on
 * quit/disable) is responsible for hopping onto the entity thread through the {@code Scheduler} port before
 * invoking these; this class assumes it already owns the entity.
 *
 * <p>An offline player cannot be snapshotted or swapped, so {@link #capture} of an absent player yields an
 * empty-but-valid loadout and {@link #restore}/{@link #applyGadgetHotbar} are silent no-ops — the persisted DB
 * row remains the item-loss-safe net, restored when the player is next reachable.
 */
@NullMarked
public final class BukkitStaffLoadoutCapture implements StaffLoadoutCapture {

    private static final int MAIN_INVENTORY_SLOTS = 36;

    private final StaffSettings settings;
    private final StaffGadgetItems gadgetItems;

    public BukkitStaffLoadoutCapture(StaffSettings settings, StaffGadgetItems gadgetItems) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gadgetItems = Objects.requireNonNull(gadgetItems, "gadgetItems");
    }

    @Override
    public SavedLoadout capture(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            // Offline at capture: hand back a valid empty loadout so the use case still has something durable.
            return emptyLoadout();
        }
        PlayerInventory inventory = player.getInventory();
        return new SavedLoadout(
                blob(inventory.getStorageContents()),
                blob(inventory.getArmorContents()),
                blob(new ItemStack[] {inventory.getItemInOffHand()}),
                inventory.getHeldItemSlot(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode().name(),
                player.getAllowFlight() && player.isFlying(),
                StaffEffectCodec.encode(player.getActivePotionEffects()));
    }

    @Override
    public void restore(PlayerRef who, SavedLoadout loadout) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(loadout, "loadout");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(
                VaultItemCodec.decode(VaultContents.of(loadout.inventory().bytes()), 36));
        inventory.setArmorContents(
                VaultItemCodec.decode(VaultContents.of(loadout.armor().bytes()), 4));
        inventory.setItemInOffHand(offhand(loadout));
        inventory.setHeldItemSlot(loadout.heldSlot());
        restoreExperience(player, loadout);
        restoreEffects(player, loadout);
        restoreGameMode(player, loadout);
        player.updateInventory();
    }

    @Override
    public void applyGadgetHotbar(PlayerRef who, String modeName) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(modeName, "modeName");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (StaffSettings.GadgetSpec spec : settings.gadgets()) {
            inventory.setItem(spec.slot(), gadgetItems.build(spec));
        }
        player.updateInventory();
    }

    private static SavedLoadout emptyLoadout() {
        return new SavedLoadout(
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0.0f,
                GameMode.SURVIVAL.name(),
                false,
                LoadoutBlob.empty());
    }

    private static LoadoutBlob blob(ItemStack[] contents) {
        return LoadoutBlob.of(VaultItemCodec.encode(contents).payload().orElseGet(() -> new byte[0]));
    }

    private static ItemStack offhand(SavedLoadout loadout) {
        ItemStack[] decoded =
                VaultItemCodec.decode(VaultContents.of(loadout.offhand().bytes()), 1);
        ItemStack stored = decoded.length == 0 ? null : decoded[0];
        return stored == null ? new ItemStack(org.bukkit.Material.AIR) : stored;
    }

    private static void restoreExperience(Player player, SavedLoadout loadout) {
        player.setLevel(loadout.expLevel());
        player.setExp(loadout.expProgress());
    }

    private static void restoreEffects(Player player, SavedLoadout loadout) {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        StaffEffectCodec.decode(loadout.potionEffects()).forEach(player::addPotionEffect);
    }

    private static void restoreGameMode(Player player, SavedLoadout loadout) {
        GameMode mode = parseGameMode(loadout.gameMode());
        player.setGameMode(mode);
        boolean flightCapable = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        player.setAllowFlight(flightCapable || loadout.flying());
        player.setFlying(loadout.flying());
    }

    private static GameMode parseGameMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            // A renamed/removed game mode falls back to survival rather than failing the whole restore.
            return GameMode.SURVIVAL;
        }
    }

    /** {@code MAIN_INVENTORY_SLOTS} documents the 36-slot storage region the codec round-trips. */
    static int mainInventorySlots() {
        return MAIN_INVENTORY_SLOTS;
    }
}

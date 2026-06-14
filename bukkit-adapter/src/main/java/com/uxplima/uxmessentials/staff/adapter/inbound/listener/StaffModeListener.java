package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadget;
import com.uxplima.uxmessentials.staff.adapter.StaffGadgetItems;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The staff-mode interaction listener: it turns a right-click on a gadget item into the gadget's action, keeps
 * the gadget items from leaking out of the gadget hotbar, and guarantees a quit-in-mode never loses items.
 *
 * <ul>
 *   <li><b>Gadget use.</b> A left/right interact with a gadget item resolves the gadget by its PDC tag (never by
 *       material or name) and fires it: the VANISH gadget flips vanish through the soft-coupled {@link StaffVanish}
 *       (no-op without presence), the EXAMINE gadget opens the online-player picker. The interaction is cancelled
 *       so the gadget item never also places/uses.
 *   <li><b>No leak.</b> Dropping a gadget item, or moving it in any inventory view, is cancelled — so a gadget can
 *       never reach the ground or survive into the restored real inventory.
 *   <li><b>Quit safety.</b> On quit while in staff mode the real loadout is restored straight away (the quit event
 *       still has a usable player on its own region thread), so a player never logs out stranded in the gadget
 *       hotbar. If the process dies before that runs, the committed loadout row is the item-loss-safe net and is
 *       restored when the player next toggles out (the row outlives the crash). The chosen semantics are therefore
 *       <i>restore-on-quit, with the persisted row as the crash fallback</i>.
 * </ul>
 */
@NullMarked
public final class StaffModeListener implements Listener {

    private final StaffServices services;
    private final StaffGadgetItems gadgetItems;
    private final StaffVanish vanish;
    private final StaffExamineView examineView;

    public StaffModeListener(
            StaffServices services, StaffGadgetItems gadgetItems, StaffVanish vanish, StaffExamineView examineView) {
        this.services = Objects.requireNonNull(services, "services");
        this.gadgetItems = Objects.requireNonNull(gadgetItems, "gadgetItems");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.examineView = Objects.requireNonNull(examineView, "examineView");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isUse(event.getAction()) || event.getHand() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        var gadget = gadgetItems.gadgetOf(hand);
        if (gadget.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        fire(gadget.get(), player, who);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (gadgetItems.isGadget(event.getItemDrop().getItemStack())) {
            // Never let a gadget item reach the ground; it must stay in the hotbar to be cleared on exit.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (gadgetItems.isGadget(event.getCurrentItem()) || gadgetItems.isGadget(event.getCursor())) {
            // Cancel any move of a gadget item so it cannot be shuffled into a slot the restore would keep.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        if (services.store().isActive(who)) {
            // Restore the real loadout now; the player is still usable on this region thread. The DB row is the
            // crash fallback if the process dies before this runs.
            services.exit().exit(who);
        }
    }

    private void fire(StaffGadget gadget, Player player, PlayerRef who) {
        switch (gadget) {
            case VANISH -> vanish.setVanished(who, !player.isInvisible());
            case EXAMINE -> examineView.open(player, who);
        }
    }

    private static boolean isUse(Action action) {
        return action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }
}

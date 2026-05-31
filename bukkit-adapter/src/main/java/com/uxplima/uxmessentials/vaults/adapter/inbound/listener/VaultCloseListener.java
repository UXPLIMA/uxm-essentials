package com.uxplima.uxmessentials.vaults.adapter.inbound.listener;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultInventoryHolder;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;

/**
 * Persists a vault on {@code InventoryCloseEvent}: when the closed inventory's holder is a
 * {@link VaultInventoryHolder}, the live slots are serialized into opaque {@link VaultContents} on the region
 * thread the event already fires on, then handed to {@link SaveVault}, whose DB write is dispatched off-tick
 * through the kernel {@link Scheduler#async} (region-safe save — the Bukkit read happens here, the I/O happens
 * async). The holder carries the {@code Vault} identity, so the owning vault and viewer are resolved straight
 * from it — no side map keyed by player.
 *
 * <p>The set of open vault windows is tracked here (open adds, close removes) so the module's {@code stop()}
 * can {@link #flushAll() close-and-save} every still-open vault on disable, the {@code open-guis=N} the
 * {@code /uxmess doctor} line reports. The set is transient in-memory state cleared on stop.
 */
@NullMarked
public final class VaultCloseListener implements Listener {

    private final SaveVault saveVault;
    private final Scheduler scheduler;
    private final Set<VaultInventoryHolder> open = ConcurrentHashMap.newKeySet();

    public VaultCloseListener(SaveVault saveVault, Scheduler scheduler) {
        this.saveVault = Objects.requireNonNull(saveVault, "saveVault");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        holderOf(event.getInventory()).ifPresent(open::add);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        holderOf(inventory).ifPresent(holder -> {
            open.remove(holder);
            persist(holder, inventory);
        });
    }

    /** Close-and-save every still-open vault window; called on module stop so no vault is lost on disable. */
    public void flushAll() {
        for (VaultInventoryHolder holder : Set.copyOf(open)) {
            open.remove(holder);
            persist(holder, holder.getInventory());
        }
    }

    /** Drop the open-window tracking, called after the flush on stop. */
    public void clear() {
        open.clear();
    }

    private void persist(VaultInventoryHolder holder, Inventory inventory) {
        ItemStack[] snapshot = inventory.getContents();
        VaultContents contents = VaultItemCodec.encode(snapshot);
        scheduler.async(() -> saveVault.save(holder.owner(), holder.vault(), contents));
    }

    private static java.util.Optional<VaultInventoryHolder> holderOf(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof VaultInventoryHolder vaultHolder
                ? java.util.Optional.of(vaultHolder)
                : java.util.Optional.empty();
    }
}

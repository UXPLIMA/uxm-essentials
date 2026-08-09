package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The behaviour behind the {@code /villager manager} window: what its buttons do and what happens to the stacks the
 * editor leaves in it. {@link VillagerManagerWindow} owns the window itself (the operator's spec file and the bindings
 * that point at the methods here), so this class never builds an inventory of its own.
 *
 * <p>Opening seeds the item region from the villager's current trades; the toggle flips the villager's disable flag
 * and redraws its button; a remove button empties one trade's slots; and closing reads the region back into a recipe
 * set that is applied to the live merchant and PDC-serialised through the {@link VillagerRecipeStore} so the edits
 * survive a reload. Edits are dupe-safe by shape: the window is a private definition surface (its stacks are trade
 * templates, not deposited items), and the villager's recipes are replaced wholesale on close, never appended.
 *
 * <p>{@link #open} touches the live editor, so it hops to the editor's entity thread through the kernel
 * {@link Scheduler}; the close-time apply hops to the villager's own region (on Folia the villager may sit in a
 * different region from the closing editor) before it mutates the merchant. Every open window is tracked so a single
 * save claims it: whichever of the close read-back or {@link #flushAll} (on module stop) reaches it first.
 */
@NullMarked
public final class VillagerManagerView {

    private final Scheduler scheduler;
    private final PdcVillagerFlags flags;
    private final VillagerRecipeStore recipeStore;
    private final VillagerManagerWindow window;
    private final Set<VillagerManagerHolder> open = ConcurrentHashMap.newKeySet();

    public VillagerManagerView(
            Scheduler scheduler,
            PdcVillagerFlags flags,
            VillagerRecipeStore recipeStore,
            VillagerManagerWindow window) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.recipeStore = Objects.requireNonNull(recipeStore, "recipeStore");
        this.window = Objects.requireNonNull(window, "window");
    }

    /** Open the manager window for {@code villager} for {@code editor}, scheduled on the editor's entity thread. */
    public void open(Player player, PlayerRef editor, Villager villager) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(villager, "villager");
        scheduler.onEntity(editor, () -> {
            Player live = Bukkit.getPlayer(editor.uuid());
            if (live != null && live.isOnline()) {
                window.open(beginSession(editor, villager));
            }
        });
    }

    /** Save every still-open manager window and forget it; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (VillagerManagerHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                drain(holder);
            }
        }
    }

    /** The number of manager windows currently open. */
    public int openCount() {
        return open.size();
    }

    /**
     * Track a manager session for {@code villager} and return its holder: the seam {@link #open} and the tests share.
     * The holder is the subject the window carries, and registering it here is what lets the close read-back (or
     * {@link #flushAll}) claim and persist the session later.
     */
    VillagerManagerHolder beginSession(PlayerRef editor, Villager villager) {
        VillagerManagerHolder holder = new VillagerManagerHolder(
                editor, villager, villager.getRecipes(), BukkitRefs.toPosition(villager.getLocation()));
        open.add(holder);
        return holder;
    }

    /** The villager's label for the window title: its profession, as the operator's title placeholder sees it. */
    String villagerLabel(VillagerManagerHolder holder) {
        return holder.villager().getProfession().key().value();
    }

    /** Whether this villager currently refuses to trade; the toggle's two states are drawn from it. */
    boolean tradingDisabled(VillagerManagerHolder holder) {
        return flags.tradesDisabled(holder.villager());
    }

    /** Flip the villager's disable flag and redraw the window so the toggle shows its new state. */
    void toggleTrading(VillagerManagerHolder holder) {
        Villager villager = holder.villager();
        flags.setTradesDisabled(villager, !flags.tradesDisabled(villager));
        window.redraw(holder.editor());
    }

    /** Empty trade {@code index}'s slots, so the trade is gone from the set the window is read back into. */
    void removeTrade(VillagerManagerHolder holder, int index) {
        window.live(holder.editor()).ifPresent(inv -> window.clearTrade(inv, index));
    }

    /** The stacks the item region opens with: the villager's trades as they stood when the window opened. */
    List<@Nullable ItemStack> currentTrades(VillagerManagerHolder holder, int trades) {
        return VillagerManagerLayout.paint(holder.originalRecipes(), trades);
    }

    /** Claim {@code holder}'s session on close and apply the region it leaves behind as the new trade set. */
    void onWindowClosed(VillagerManagerHolder holder, List<@Nullable ItemStack> contents) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            persist(holder, contents);
        }
    }

    /** Read an already-claimed session's live window on the editor's own thread and persist what it holds. */
    private void drain(VillagerManagerHolder holder) {
        scheduler.onEntity(
                holder.editor(),
                () -> window.live(holder.editor()).ifPresent(inv -> persist(holder, Arrays.asList(window.read(inv)))));
    }

    private void persist(VillagerManagerHolder holder, List<@Nullable ItemStack> contents) {
        List<MerchantRecipe> recipes = VillagerManagerLayout.readRecipes(contents, holder.originalRecipes());
        Villager villager = holder.villager();
        scheduler.onRegion(holder.villagerPosition(), () -> {
            villager.setRecipes(recipes);
            recipeStore.store(villager, recipes);
        });
    }
}

package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /villager manager} window: a six-row chest that mirrors a villager's trades into
 * {@link VillagerManagerLayout}'s recipe rows and lets staff edit them. Opening seeds each row with the villager's
 * current buy/sell stacks; a click toggles the villager's disable flag or removes a row; and closing reads the window
 * back into a recipe set that is applied to the live merchant and PDC-serialised through the {@link VillagerRecipeStore}
 * so the edits survive a reload. Edits are dupe-safe by the same shape as the kit editor: the window is a private
 * definition surface (its stacks are trade templates, not deposited items), and the villager's recipes are replaced
 * wholesale on close, never appended.
 *
 * <p>{@link #open} touches the live editor, so the caller schedules it on the editor's entity thread through the
 * kernel {@link Scheduler}; the close-time apply hops to the villager's own region (the villager may sit in a
 * different region from the closing editor on Folia) before it mutates the merchant. Every open window is tracked so a
 * single save claims it — whichever of the close handler or {@link #flushAll} (on module stop) reaches it first.
 */
@NullMarked
public final class VillagerManagerView {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final PdcVillagerFlags flags;
    private final VillagerRecipeStore recipeStore;
    private final Set<VillagerManagerHolder> open = ConcurrentHashMap.newKeySet();

    public VillagerManagerView(
            GuiText guiText, Scheduler scheduler, PdcVillagerFlags flags, VillagerRecipeStore recipeStore) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.recipeStore = Objects.requireNonNull(recipeStore, "recipeStore");
    }

    /** Open the manager window for {@code villager} for {@code editor}, scheduled on the editor's entity thread. */
    public void open(Player player, PlayerRef editor, Villager villager) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(villager, "villager");
        scheduler.onEntity(editor, () -> {
            Player live = Bukkit.getPlayer(editor.uuid());
            if (live != null && live.isOnline()) {
                VillagerManagerHolder holder = beginSession(editor, villager);
                live.openInventory(holder.getInventory());
            }
        });
    }

    /** Save every still-open manager window and forget it; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (VillagerManagerHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                persist(holder);
            }
        }
    }

    /** The number of manager windows currently open. */
    public int openCount() {
        return open.size();
    }

    /**
     * Build, seed, and track a manager session for {@code villager} — the seam {@link #open} and the tests share. The
     * returned holder carries the seeded window ({@link VillagerManagerHolder#getInventory()}) and is registered so a
     * later {@link #onClose} persists it; production opens that window to the editor, the tests inspect it directly.
     */
    VillagerManagerHolder beginSession(PlayerRef editor, Villager villager) {
        VillagerManagerHolder holder = new VillagerManagerHolder(
                editor, villager, villager.getRecipes(), BukkitRefs.toPosition(villager.getLocation()));
        Inventory menu = Bukkit.createInventory(holder, VillagerManagerLayout.SIZE, title(holder));
        holder.attach(menu);
        seed(menu, holder);
        open.add(holder);
        return holder;
    }

    /**
     * Handle a click at {@code slot} in the window, returning whether the click must be cancelled. The toggle flips
     * the villager's disable flag and re-renders its button; a remove button clears its row; any other non-editable
     * slot is a static control and is cancelled; only an editable buy/sell slot lets the click through.
     */
    boolean handleClick(VillagerManagerHolder holder, int slot) {
        Objects.requireNonNull(holder, "holder");
        if (slot == VillagerManagerLayout.TOGGLE_SLOT) {
            Villager villager = holder.villager();
            flags.setTradesDisabled(villager, !flags.tradesDisabled(villager));
            holder.getInventory().setItem(VillagerManagerLayout.TOGGLE_SLOT, toggleIcon(holder.editor(), villager));
            return true;
        }
        if (VillagerManagerLayout.isRemoveButton(slot)) {
            VillagerManagerLayout.clearRow(holder.getInventory(), VillagerManagerLayout.rowOf(slot));
            return true;
        }
        return !VillagerManagerLayout.isEditable(slot);
    }

    /** Claim {@code holder}'s window on close and apply its contents as the villager's new trade set. */
    void onClose(VillagerManagerHolder holder) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            persist(holder);
        }
    }

    private void persist(VillagerManagerHolder holder) {
        List<MerchantRecipe> recipes =
                VillagerManagerLayout.readRecipes(holder.getInventory(), holder.originalRecipes());
        Villager villager = holder.villager();
        scheduler.onRegion(holder.villagerPosition(), () -> {
            villager.setRecipes(recipes);
            recipeStore.store(villager, recipes);
        });
    }

    private void seed(Inventory menu, VillagerManagerHolder holder) {
        ItemStack filler = filler();
        for (int index = 0; index < VillagerManagerLayout.SIZE; index++) {
            menu.setItem(index, filler);
        }
        List<MerchantRecipe> recipes = holder.villager().getRecipes();
        for (int row = 0; row < VillagerManagerLayout.RECIPE_ROWS; row++) {
            seedRow(menu, holder.editor(), row, row < recipes.size() ? recipes.get(row) : null);
        }
        menu.setItem(VillagerManagerLayout.TOGGLE_SLOT, toggleIcon(holder.editor(), holder.villager()));
        menu.setItem(VillagerManagerLayout.HELP_SLOT, helpIcon(holder.editor()));
    }

    private void seedRow(Inventory menu, PlayerRef editor, int row, @Nullable MerchantRecipe recipe) {
        menu.setItem(VillagerManagerLayout.slot(row, VillagerManagerLayout.ARROW_COL), arrowIcon());
        menu.setItem(VillagerManagerLayout.slot(row, VillagerManagerLayout.REMOVE_COL), removeIcon(editor));
        ItemStack buyA = null;
        ItemStack buyB = null;
        ItemStack sell = null;
        if (recipe != null) {
            List<ItemStack> ingredients = recipe.getIngredients();
            buyA = ingredients.isEmpty() ? null : ingredients.get(0);
            buyB = ingredients.size() > 1 ? ingredients.get(1) : null;
            sell = recipe.getResult();
        }
        menu.setItem(VillagerManagerLayout.slot(row, VillagerManagerLayout.BUY_A_COL), buyA);
        menu.setItem(VillagerManagerLayout.slot(row, VillagerManagerLayout.BUY_B_COL), buyB);
        menu.setItem(VillagerManagerLayout.slot(row, VillagerManagerLayout.SELL_COL), sell);
    }

    private Component title(VillagerManagerHolder holder) {
        return guiText.text(
                holder.editor(),
                VillagersMessageKey.VILLAGERS_MANAGER_TITLE,
                Map.of("name", villagerLabel(holder.villager())));
    }

    private static String villagerLabel(Villager villager) {
        return villager.getProfession().key().value();
    }

    private ItemStack toggleIcon(PlayerRef editor, Villager villager) {
        boolean disabled = flags.tradesDisabled(villager);
        VillagersMessageKey key = disabled
                ? VillagersMessageKey.VILLAGERS_MANAGER_TRADING_DISABLED
                : VillagersMessageKey.VILLAGERS_MANAGER_TRADING_ENABLED;
        Material material = disabled ? Material.BARRIER : Material.EMERALD;
        return ItemBuilder.of(material).name(guiText.text(editor, key)).build();
    }

    private ItemStack removeIcon(PlayerRef editor) {
        return ItemBuilder.of(Material.REDSTONE_BLOCK)
                .name(guiText.text(editor, VillagersMessageKey.VILLAGERS_MANAGER_REMOVE))
                .build();
    }

    private ItemStack helpIcon(PlayerRef editor) {
        return ItemBuilder.of(Material.BOOK)
                .name(guiText.text(editor, VillagersMessageKey.VILLAGERS_MANAGER_HELP))
                .build();
    }

    private static ItemStack arrowIcon() {
        return ItemBuilder.of(Material.ARROW).name(Component.empty()).build();
    }

    private static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }
}

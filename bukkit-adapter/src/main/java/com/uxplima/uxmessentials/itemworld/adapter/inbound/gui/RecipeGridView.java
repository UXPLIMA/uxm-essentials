package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /recipe} crafting-grid menu: a fixed three-row {@link SimpleGui} that lays a crafting recipe out the
 * way a crafting table shows it — a 3x3 grid of ingredient items on the left, the result item in its own slot to
 * the right (the classic crafting-table result position), the rest of the window glass filler. It is the visual
 * twin of the {@code /recipe} text read-out: same first shaped/shapeless recipe, drawn rather than printed.
 *
 * <p>The view holds no recipe logic. The {@code RecipeCommand} resolves the recipe (its existing
 * {@code firstCraftingRecipe} + choice→material helpers) into a nine-cell ingredient grid (a {@code null} cell is
 * an empty slot) and a result material, then hands them to {@link #open}; this class only places them. A recipe
 * with no crafting form never reaches the grid — the command opens {@link #openEmpty} instead, a one-row
 * empty-state title.
 */
@NullMarked
public final class RecipeGridView {

    // The 3x3 ingredient grid in the upper-left of a three-row window, and the result slot to its right — the
    // classic crafting-table layout reproduced in a chest GUI.
    private static final int[] GRID_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int RESULT_SLOT = 15;
    private static final int ROWS = 3;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Material filler;

    public RecipeGridView(GuiText guiText, Scheduler scheduler, Material filler) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.filler = Objects.requireNonNull(filler, "filler");
    }

    /**
     * Open the recipe grid for {@code viewer}: the nine {@code grid} cells (a {@code null} cell is empty) and the
     * {@code result} material, on the viewer's entity thread. The {@code grid} must be exactly nine cells in
     * row-major order (top-left to bottom-right).
     */
    public void open(Player player, PlayerRef viewer, List<@Nullable Material> grid, Material result) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(result, "result");
        if (Objects.requireNonNull(grid, "grid").size() != GRID_SLOTS.length) {
            throw new IllegalArgumentException("grid must have " + GRID_SLOTS.length + " cells, was " + grid.size());
        }
        List<@Nullable Material> cells = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(grid));
        scheduler.onEntity(viewer, () -> build(viewer, cells, result).open(player));
    }

    /** Open the empty-state title for {@code viewer} — a one-row menu shown for an item with no crafting recipe. */
    public void openEmpty(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> emptyMenu(viewer).open(player));
    }

    private SimpleGui build(PlayerRef viewer, List<@Nullable Material> grid, Material result) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_TITLE))
                .rows(ROWS)
                .build();
        fill(gui);
        for (int cell = 0; cell < GRID_SLOTS.length; cell++) {
            gui.set(GRID_SLOTS[cell], GuiItem.display(ingredient(viewer, grid.get(cell))));
        }
        gui.set(RESULT_SLOT, GuiItem.display(resultIcon(viewer, result)));
        return gui;
    }

    private ItemStack ingredient(PlayerRef viewer, @Nullable Material material) {
        if (material == null || material.isAir()) {
            return ItemBuilder.of(filler)
                    .name(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_EMPTY_NAME))
                    .build();
        }
        return ItemBuilder.of(material)
                .lore(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_INGREDIENT_LORE))
                .build();
    }

    private ItemStack resultIcon(PlayerRef viewer, Material result) {
        Map<String, String> placeholders = Map.of("item", result.getKey().getKey());
        return ItemBuilder.of(result)
                .name(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_RESULT_NAME, placeholders))
                .lore(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_RESULT_LORE))
                .build();
    }

    private SimpleGui emptyMenu(PlayerRef viewer) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewer, ItemworldMessageKey.RECIPE_GUI_NONE_TITLE))
                .rows(1)
                .build();
        ItemStack glass = ItemBuilder.of(filler).name(Component.empty()).build();
        for (int slot = 0; slot < 9; slot++) {
            gui.set(slot, GuiItem.display(glass));
        }
        return gui;
    }

    private void fill(SimpleGui gui) {
        ItemStack glass = ItemBuilder.of(filler).name(Component.empty()).build();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(glass));
        }
    }
}

package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The kit→category selector the kit-settings editor opens to assign a kit to a category: a six-row grid with one
 * icon per configured {@link KitCategory}, a "No Category" clear button, and a back button to the kit's settings.
 * Clicking a category runs the same {@link KitEditor} save the old bespoke window did — the kit with that category
 * id — then returns the viewer to its {@link KitSettingsView}, the caller that opened the selector.
 *
 * <p>The window draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link EntityListSpec}, so it is a holder-backed engine list routed and torn down by the one menu listener and one
 * {@code closeMenu}, with paging re-paginating the same holder. The category list is a plain repository read that
 * touches no Bukkit API, so the engine reads only that snapshot and shows the window on the viewer's entity thread;
 * the still-bespoke kit-settings editor opens this transitionally and the assign reopens it, exactly as before.
 */
@NullMarked
public final class KitCategorySelectorView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int NONE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private final GuiText guiText;
    private final KitCategoryRepository categoryRepository;
    private final KitEditor kitEditor;
    private final KitSettingsView settingsView;
    private final Menus menus;
    private final MiniMessage miniMessage;

    public KitCategorySelectorView(
            GuiText guiText,
            KitCategoryRepository categoryRepository,
            KitEditor kitEditor,
            KitSettingsView settingsView,
            Menus menus,
            Scheduler scheduler) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.kitEditor = Objects.requireNonNull(kitEditor, "kitEditor");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.menus = Objects.requireNonNull(menus, "menus");
        // The engine hops onto the viewer's entity thread inside openList, so the scheduler is only validated here.
        Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the selector for {@code viewer} to assign {@code kit} to a category, returning to its settings on pick. */
    public void open(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kit, "kit");
        List<KitCategory> snapshot = List.copyOf(categoryRepository.all());
        menus.openList(viewer, spec(player, viewer, kit, snapshot));
    }

    /**
     * Build the engine {@link EntityListSpec}: the categories as the listed entities, the per-category icon reproducing
     * the old {@code icon(cat, viewer)}, an {@code onSelect} that saves the kit with the clicked category and reopens
     * its settings, plus the "No Category" clear and back buttons the old grid drew at slots 49 and 53.
     */
    private EntityListSpec spec(Player player, PlayerRef viewer, KitDefinition kit, List<KitCategory> categories) {
        return EntityListSpec.builder()
                .title(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, Material.ARROW)
                .navNames(
                        guiText.text(viewer, KitsMessageKey.KIT_MENU_PREV),
                        guiText.text(viewer, KitsMessageKey.KIT_MENU_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(categories))
                .iconRenderer((v, entity) -> icon(v, (KitCategory) entity))
                .onSelect((p, entity) -> assign(p, viewer, kit, Optional.of(((KitCategory) entity).id())))
                .extraButtons(List.of(noneButton(player, viewer, kit), backButton(player, viewer, kit)))
                .build();
    }

    /** Save {@code kit} with {@code categoryId} through the editor, then reopen its settings — the old click's effect. */
    private void assign(Player player, PlayerRef viewer, KitDefinition kit, Optional<String> categoryId) {
        kitEditor.save(viewer, kit.withCategoryId(categoryId));
        settingsView.open(player, viewer, kit.withCategoryId(categoryId));
    }

    private EntityListSpec.ExtraButton noneButton(Player player, PlayerRef viewer, KitDefinition kit) {
        ItemStack icon = ItemBuilder.of(Material.BARRIER)
                .name(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_NONE_NAME))
                .lore(List.of(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_NONE_LORE)))
                .build();
        return new EntityListSpec.ExtraButton(NONE_SLOT, icon, p -> assign(player, viewer, kit, Optional.empty()));
    }

    private EntityListSpec.ExtraButton backButton(Player player, PlayerRef viewer, KitDefinition kit) {
        ItemStack icon = ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                .build();
        return new EntityListSpec.ExtraButton(BACK_SLOT, icon, p -> settingsView.open(player, viewer, kit));
    }

    private ItemStack icon(PlayerRef viewer, KitCategory category) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        for (String line : category.displayLore()) {
            lore.add(miniMessage.deserialize(line));
        }
        lore.add(Component.empty());
        lore.add(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(Component.empty());
        lore.add(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_SELECT_HINT));
        return ItemBuilder.of(material(category)).name(name).lore(lore).build();
    }

    /** The category's configured icon material, the BOOK fallback when it is absent, blank, air, or unknown. */
    private static Material material(KitCategory category) {
        if (category.displayMaterial().isEmpty()) {
            return Material.BOOK;
        }
        try {
            Material parsed = Material.valueOf(category.displayMaterial().get().toUpperCase(java.util.Locale.ROOT));
            return parsed.isAir() ? Material.BOOK : parsed;
        } catch (IllegalArgumentException unknown) {
            return Material.BOOK;
        }
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}

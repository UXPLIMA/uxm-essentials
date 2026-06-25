package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The kit-category manager the admin kit manager opens to author the category tree: a six-row grid with one icon per
 * configured {@link KitCategory}, an "Create New Category" button, and a back button to the engine kit manager.
 * Clicking a category opens its {@link KitCategorySettingsView}; the create button prompts for a category id through
 * the shared input seam, saves the new empty category through the repository, and opens its settings — exactly as the
 * old bespoke window did.
 *
 * <p>The window draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link ListSpec}, so it is a holder-backed engine list routed and torn down by the one menu listener and one
 * {@code closeMenu}, with paging re-paginating the same holder. The category list is a plain repository read that
 * touches no Bukkit API, so the engine reads only that snapshot and shows the window on the viewer's entity thread.
 * Mirrors the two category selectors the kit-settings and category-settings editors open.
 */
@NullMarked
public final class KitCategoryManagerView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int CREATE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private final GuiText guiText;
    private final Messages messages;
    private final KitCategoryRepository categoryRepository;
    private final TextInput textInput;
    private final Menus menus;
    private final MiniMessage miniMessage;

    /** The settings editor opened on a category click and after a create; injected after this view to break the cycle. */
    private @Nullable KitCategorySettingsView settingsView;

    /** Reopens the engine kit manager when the back button is clicked; injected after this view to break the cycle. */
    private @Nullable BiConsumer<Player, PlayerRef> onBack;

    public KitCategoryManagerView(
            GuiText guiText,
            Messages messages,
            KitCategoryRepository categoryRepository,
            TextInput textInput,
            Menus menus,
            Scheduler scheduler) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.menus = Objects.requireNonNull(menus, "menus");
        // The engine hops onto the viewer's entity thread inside openList, so the scheduler is only validated here.
        Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Wire the two collaborators that close the manager→settings→back loop. The settings editor opens this manager
     * back on its own back button, and the back button reopens the kit manager, so all three are built before any one
     * of them exists; this setter breaks that cycle, mirroring the {@code managerHolder[0]} indirection in wiring.
     */
    public void bind(KitCategorySettingsView settingsView, BiConsumer<Player, PlayerRef> onBack) {
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Open the category manager for {@code viewer}; a category click opens its settings, create prompts for an id. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        List<KitCategory> snapshot = List.copyOf(categoryRepository.all());
        menus.openList(viewer, spec(viewer, snapshot));
    }

    /**
     * Build the engine {@link ListSpec}: the categories as the listed entities, the per-category icon reproducing the
     * old {@code icon(cat, viewer)}, an {@code onSelect} that opens the clicked category's settings, plus the create
     * and back buttons the old grid drew at slots 49 and 53.
     */
    private ListSpec spec(PlayerRef viewer, List<KitCategory> categories) {
        return ListSpec.builder()
                .title(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_MANAGER_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, Material.ARROW)
                .navNames(
                        guiText.text(viewer, KitsMessageKey.KIT_MENU_PREV),
                        guiText.text(viewer, KitsMessageKey.KIT_MENU_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(categories))
                .iconRenderer((v, entity) -> icon(v, (KitCategory) entity))
                .onSelect((p, entity) -> openSettings(p, viewer, (KitCategory) entity))
                .extraButtons(List.of(createButton(viewer), backButton(viewer)))
                .build();
    }

    private ListSpec.ExtraButton createButton(PlayerRef viewer) {
        ItemStack icon = ItemBuilder.of(Material.EMERALD_BLOCK)
                .name(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_CREATE_BUTTON_NAME))
                .lore(List.of(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_CREATE_BUTTON_LORE)))
                .build();
        return new ListSpec.ExtraButton(CREATE_SLOT, icon, p -> promptCreate(p, viewer));
    }

    private ListSpec.ExtraButton backButton(PlayerRef viewer) {
        ItemStack icon = ItemBuilder.of(Material.ARROW)
                .name(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_SETTINGS_BACK_BUTTON))
                .build();
        return new ListSpec.ExtraButton(BACK_SLOT, icon, p -> back(p, viewer));
    }

    /** Prompt for a category id, save the new empty category, and open its settings — the old create button's effect. */
    private void promptCreate(Player player, PlayerRef viewer) {
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("kit.category.create-name", KitsMessageKey.KIT_EDITOR_CATEGORY_PROMPT_CREATE),
                name -> create(player, viewer, name),
                () -> open(player, viewer));
    }

    /**
     * Save a new category under the sanitized id and open its settings; a name with a space sanitizes to empty and is
     * rejected, exactly as the old window did. Package-private so the create branch is unit-tested without an anvil.
     */
    void create(Player player, PlayerRef viewer, String name) {
        String clean = sanitizeId(name);
        if (clean.isEmpty()) {
            player.sendMessage(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME));
            return;
        }
        KitCategory category = new KitCategory(clean, name, Optional.empty(), List.of(), 0, Optional.empty());
        categoryRepository.save(category);
        openSettings(player, viewer, category);
    }

    private void openSettings(Player player, PlayerRef viewer, KitCategory category) {
        if (settingsView != null) {
            settingsView.open(player, viewer, category);
        }
    }

    private void back(Player player, PlayerRef viewer) {
        if (onBack != null) {
            onBack.accept(player, viewer);
        }
    }

    private ItemStack icon(PlayerRef viewer, KitCategory category) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        for (String line : category.displayLore()) {
            lore.add(miniMessage.deserialize(line));
        }
        lore.add(Component.empty());
        lore.add(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(guiText.text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_SLOT,
                Map.of("slot", Integer.toString(category.slot()))));
        lore.add(guiText.text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_PARENT,
                Map.of("parent", category.parentCategoryId().orElse(none(viewer)))));
        lore.add(Component.empty());
        lore.add(guiText.text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_LORE_EDIT_HINT));
        return ItemBuilder.of(material(category)).name(name).lore(lore).build();
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of());
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

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}

package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The warp category manager the admin warp manager opens to author the category tree: a six-row grid with one icon
 * per configured {@link WarpCategory}, a "create category" button, and a back button to the engine warp manager.
 * Clicking a category opens its {@link WarpCategorySettingsView}; the create button prompts for a category id through
 * the shared input seam, saves the new empty category through the repository, and opens its settings — exactly as the
 * old bespoke window did.
 *
 * <p>The window draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link EntityListSpec}, so it is a holder-backed engine list routed and torn down by the one menu listener and one
 * {@code closeMenu}, with paging re-paginating the same holder. The category list is a plain repository read that
 * touches no Bukkit API, so the engine reads only that snapshot and shows the window on the viewer's entity thread.
 * Mirrors the two category selectors the warp editor and category-settings editors open.
 */
@NullMarked
public final class WarpCategoryManagerView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int CREATE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private final Messages messages;
    private final WarpCategoryRepository categoryRepository;
    private final TextInput textInput;
    private final Menus menus;
    private final MiniMessage miniMessage;

    /** The settings editor opened on a category click and after a create; injected after this view to break the cycle. */
    private @Nullable WarpCategorySettingsView settingsView;

    /** Reopens the engine warp manager when the back button is clicked; injected after this view to break the cycle. */
    private @Nullable BiConsumer<Player, PlayerRef> onBack;

    public WarpCategoryManagerView(
            Messages messages,
            WarpCategoryRepository categoryRepository,
            TextInput textInput,
            Menus menus,
            Scheduler scheduler) {
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
     * back on its own back button, and the back button reopens the warp manager, so all three are built before any
     * one of them exists; this setter breaks that cycle, mirroring the {@code managerHolder[0]} indirection in wiring.
     */
    public void bind(WarpCategorySettingsView settingsView, BiConsumer<Player, PlayerRef> onBack) {
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Open the category manager for {@code viewer}; a category click opens its settings, create prompts for an id. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        List<WarpCategory> snapshot = List.copyOf(categoryRepository.all());
        menus.openList(viewer, spec(viewer, snapshot));
    }

    /**
     * Build the engine {@link EntityListSpec}: the categories as the listed entities, the per-category icon reproducing the
     * old {@code icon(cat, viewer)}, an {@code onSelect} that opens the clicked category's settings, plus the create
     * and back buttons the old grid drew at slots 49 and 53.
     */
    private EntityListSpec spec(PlayerRef viewer, List<WarpCategory> categories) {
        return EntityListSpec.builder()
                .title(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_MANAGER_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, Material.ARROW)
                .navNames(text(viewer, WarpsMessageKey.WARP_MENU_PREV), text(viewer, WarpsMessageKey.WARP_MENU_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(categories))
                .iconRenderer((v, entity) -> icon((WarpCategory) entity, v))
                .onSelect((p, entity) -> openSettings(p, viewer, (WarpCategory) entity))
                .extraButtons(List.of(createButton(viewer), backButton(viewer)))
                .build();
    }

    private EntityListSpec.ExtraButton createButton(PlayerRef viewer) {
        ItemStack icon = ItemBuilder.of(Material.EMERALD_BLOCK)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_CREATE_BUTTON_NAME))
                .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_CREATE_BUTTON_LORE)))
                .build();
        return new EntityListSpec.ExtraButton(CREATE_SLOT, icon, p -> promptCreate(p, viewer));
    }

    private EntityListSpec.ExtraButton backButton(PlayerRef viewer) {
        ItemStack icon = ItemBuilder.of(Material.ARROW)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                .build();
        return new EntityListSpec.ExtraButton(BACK_SLOT, icon, p -> back(p, viewer));
    }

    /** Prompt for a category id, save the new empty category, and open its settings — the old create button's effect. */
    private void promptCreate(Player player, PlayerRef viewer) {
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.category.create-name", WarpsMessageKey.WARP_EDITOR_CATEGORY_PROMPT_CREATE),
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
            player.sendMessage(text(viewer, WarpsMessageKey.WARP_MANAGER_ERROR_INVALID_NAME));
            return;
        }
        WarpCategory category = new WarpCategory(clean, name, Optional.empty(), List.of(), 0, Optional.empty());
        categoryRepository.save(category);
        openSettings(player, viewer, category);
    }

    private void openSettings(Player player, PlayerRef viewer, WarpCategory category) {
        if (settingsView != null) {
            settingsView.open(player, viewer, category);
        }
    }

    private void back(Player player, PlayerRef viewer) {
        if (onBack != null) {
            onBack.accept(player, viewer);
        }
    }

    private ItemStack icon(WarpCategory category, PlayerRef viewer) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        for (String line : category.displayLore()) {
            lore.add(miniMessage.deserialize(line));
        }
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_SLOT,
                Map.of("slot", Integer.toString(category.slot()))));
        lore.add(text(
                viewer,
                WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_PARENT,
                Map.of("parent", category.parentCategoryId().orElse(none(viewer)))));
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_LORE_EDIT_HINT));
        return ItemBuilder.of(WarpCategoryIcons.material(category))
                .name(name)
                .lore(lore)
                .build();
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
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

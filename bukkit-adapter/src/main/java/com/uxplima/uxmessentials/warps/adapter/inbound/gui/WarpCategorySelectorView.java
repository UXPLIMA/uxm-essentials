package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The warp → category selector the server-warp editor opens to assign a warp to a category: a six-row grid with one
 * icon per configured {@link WarpCategory}, a "no category" clear button, and a back button to the warp's editor.
 * Clicking a category runs the same repository save the old bespoke window did — the warp with that category id —
 * then returns the viewer to its {@link WarpEditorView}, the caller that opened the selector.
 *
 * <p>The window draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link ListSpec}, so it is a holder-backed engine list routed and torn down by the one menu listener and one
 * {@code closeMenu}, with paging re-paginating the same holder. The category list is a plain repository read that
 * touches no Bukkit API, so the engine reads only that snapshot and shows the window on the viewer's entity thread;
 * the still-bespoke warp editor opens this transitionally and the assign reopens it, exactly as before.
 */
@NullMarked
public final class WarpCategorySelectorView {

    private static final int ROWS = 6;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int NONE_SLOT = 49;
    private static final int BACK_SLOT = 53;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private final Messages messages;
    private final WarpCategoryRepository categoryRepository;
    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;
    private final Menus menus;
    private final MiniMessage miniMessage;

    public WarpCategorySelectorView(
            Messages messages,
            WarpCategoryRepository categoryRepository,
            WarpRepository warpRepository,
            WarpEditorView editorView,
            Menus menus,
            Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.menus = Objects.requireNonNull(menus, "menus");
        // The engine hops onto the viewer's entity thread inside openList, so the scheduler is only validated here.
        Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Open the selector for {@code viewer} to assign {@code warpName} to a category, returning to its editor on pick. */
    public void open(Player player, PlayerRef viewer, String warpName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        List<WarpCategory> snapshot = List.copyOf(categoryRepository.all());
        menus.openList(viewer, spec(player, viewer, warpName, snapshot));
    }

    /**
     * Build the engine {@link ListSpec}: the categories as the listed entities, the per-category icon reproducing
     * the old {@code icon(cat, viewer)}, an {@code onSelect} that saves the warp with the clicked category and reopens
     * its editor, plus the "no category" clear and back buttons the old grid drew at slots 49 and 53.
     */
    private ListSpec spec(Player player, PlayerRef viewer, String warpName, List<WarpCategory> categories) {
        return ListSpec.builder()
                .title(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_TITLE))
                .rows(ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, Material.ARROW)
                .navNames(text(viewer, WarpsMessageKey.WARP_MENU_PREV), text(viewer, WarpsMessageKey.WARP_MENU_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(categories))
                .iconRenderer((v, entity) -> icon((WarpCategory) entity, v))
                .onSelect((p, entity) -> assign(p, viewer, warpName, Optional.of(((WarpCategory) entity).id())))
                .extraButtons(List.of(noneButton(player, viewer, warpName), backButton(player, viewer, warpName)))
                .build();
    }

    /** Save the warp under {@code categoryId} through the repository, then reopen its editor — the old click's effect. */
    private void assign(Player player, PlayerRef viewer, String warpName, Optional<String> categoryId) {
        Optional<Warp> warp = warpRepository.find(WarpName.of(warpName));
        warp.ifPresent(value -> warpRepository.save(value.withCategoryId(categoryId)));
        editorView.open(player, viewer, warpName, null);
    }

    private ListSpec.ExtraButton noneButton(Player player, PlayerRef viewer, String warpName) {
        ItemStack icon = ItemBuilder.of(Material.BARRIER)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_NONE_NAME))
                .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_NONE_LORE)))
                .build();
        return new ListSpec.ExtraButton(NONE_SLOT, icon, p -> assign(player, viewer, warpName, Optional.empty()));
    }

    private ListSpec.ExtraButton backButton(Player player, PlayerRef viewer, String warpName) {
        ItemStack icon = ItemBuilder.of(Material.ARROW)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                .build();
        return new ListSpec.ExtraButton(BACK_SLOT, icon, p -> editorView.open(player, viewer, warpName, null));
    }

    private ItemStack icon(WarpCategory category, PlayerRef viewer) {
        Component name = miniMessage.deserialize(category.displayName());
        List<Component> lore = new ArrayList<>();
        for (String line : category.displayLore()) {
            lore.add(miniMessage.deserialize(line));
        }
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lore.add(Component.empty());
        lore.add(text(viewer, WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_SELECT_HINT));
        return ItemBuilder.of(WarpCategoryIcons.material(category))
                .name(name)
                .lore(lore)
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}

package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the warp to category selector with the menu engine and opens it. The warp editor opens this to file a
 * warp under one of the configured categories: one icon per category, a button that clears the category, and a back
 * button, each returning the viewer to the editor it came from.
 *
 * <p>The grid is the {@code warps:category-selector} list source, read on the viewer's entity thread at open and
 * handed to the engine as the menu subject so the engine renders off that snapshot. The category's own icon, display
 * name and lore reach the spec through the {@code warp_category_*} placeholders, since they are authored per category
 * rather than per menu. Everything else (geometry, materials, which slot holds which button) lives in
 * {@code modules/warps/gui/warp-category-selector.conf} and is an operator's to change.
 */
@NullMarked
public final class WarpCategorySelectorMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "warp-category-selector";

    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-category-selector.conf";

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpCategoryRepository categoryRepository;
    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;

    public WarpCategorySelectorMenu(
            Menus menus,
            Messages messages,
            Scheduler scheduler,
            WarpCategoryRepository categoryRepository,
            WarpRepository warpRepository,
            WarpEditorView editorView) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
    }

    /** Register the bindings the spec names and the spec itself; called once at warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(
                "warps:category-selector", ctx -> ctx.subject(Selection.class).categories());
        bindings.placeholder(
                "warp_category_icon",
                ctx -> WarpCategoryIcons.material(categoryOf(ctx)).name());
        bindings.placeholder("warp_category_name", ctx -> categoryOf(ctx).displayName());
        bindings.placeholder("warp_category_lore", this::lore);
        bindings.action("warps:category-assign", this::assignClicked);
        bindings.action("warps:category-clear", this::clearClicked);
        bindings.action("warps:category-back", this::backClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the selector for {@code viewer} to file {@code warpName}, returning to that warp's editor on any click. */
    public void open(Player player, PlayerRef viewer, String warpName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(
                viewer,
                () -> menus.open(viewer, SPEC_ID, new Selection(warpName, List.copyOf(categoryRepository.all()))));
    }

    /**
     * The bound category's lore as one string the renderer splits per line: the category's own lore lines, a spacer,
     * the category id line, a spacer, then the click hint. This is what the bespoke grid drew, line for line.
     */
    private String lore(MenuContext ctx) {
        WarpCategory category = categoryOf(ctx);
        List<String> lines = new ArrayList<>(category.displayLore());
        lines.add("");
        lines.add(messages.resolve(
                ctx.viewer(), WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lines.add("");
        lines.add(messages.resolve(ctx.viewer(), WarpsMessageKey.WARP_EDITOR_CATEGORY_SELECTOR_SELECT_HINT, Map.of()));
        return String.join("\n", lines);
    }

    /** Left-click a category icon: file the warp under it and reopen the warp's editor. */
    private void assignClicked(MenuActionContext ctx) {
        assign(ctx, Optional.of(ctx.entry(WarpCategory.class).id()));
    }

    /** Left-click the clear button: drop the warp's category and reopen its editor. */
    private void clearClicked(MenuActionContext ctx) {
        assign(ctx, Optional.empty());
    }

    /** Left-click back: reopen the warp's editor, changing nothing. */
    private void backClicked(MenuActionContext ctx) {
        editorView.open(ctx.player(), ctx.viewer(), warpNameOf(ctx), null);
    }

    /** Save the warp under {@code categoryId} through the repository, then reopen its editor. */
    private void assign(MenuActionContext ctx, Optional<String> categoryId) {
        String warpName = warpNameOf(ctx);
        Optional<Warp> warp = warpRepository.find(WarpName.of(warpName));
        warp.ifPresent(value -> warpRepository.save(value.withCategoryId(categoryId)));
        editorView.open(ctx.player(), ctx.viewer(), warpName, null);
    }

    private static WarpCategory categoryOf(MenuContext ctx) {
        return ctx.entry(WarpCategory.class);
    }

    private static String warpNameOf(MenuActionContext ctx) {
        return ctx.subject(Selection.class).warpName();
    }

    /**
     * The subject of an open selector: the warp being filed and the categories to choose from, snapshotted before the
     * open so the engine renders without a repository read of its own.
     *
     * @param warpName the warp the click files under the clicked category
     * @param categories the configured categories, in repository order
     */
    public record Selection(String warpName, List<WarpCategory> categories) {

        public Selection {
            Objects.requireNonNull(warpName, "warpName");
            categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        }
    }
}

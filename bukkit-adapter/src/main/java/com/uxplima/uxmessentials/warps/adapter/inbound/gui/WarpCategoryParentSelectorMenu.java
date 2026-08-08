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
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the parent-category selector with the menu engine and opens it. A category's settings panel opens this to
 * nest that category under another: every category except the one being edited is listed, since a category can never be
 * its own parent, plus a clear button and a back button to the settings panel.
 *
 * <p>The candidates are the {@code warps:category-parent-selector} list source, snapshotted at open and handed to the
 * engine as the menu subject. The candidate's icon, name and lore reach the spec through the {@code warp_parent_*}
 * placeholders because they are authored per category; the geometry and the fixed buttons live in
 * {@code modules/warps/gui/warp-category-parent-selector.conf}.
 */
@NullMarked
public final class WarpCategoryParentSelectorMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "warp-category-parent-selector";

    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-category-parent-selector.conf";

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpCategoryRepository categoryRepository;
    private final WarpCategorySettingsView settingsView;

    public WarpCategoryParentSelectorMenu(
            Menus menus,
            Messages messages,
            Scheduler scheduler,
            WarpCategoryRepository categoryRepository,
            WarpCategorySettingsView settingsView) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
    }

    /** Register the bindings the spec names and the spec itself; called once at warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(
                "warps:category-parent-selector",
                ctx -> ctx.subject(Selection.class).candidates());
        bindings.placeholder(
                "warp_parent_icon",
                ctx -> WarpCategoryIcons.material(candidateOf(ctx)).name());
        bindings.placeholder("warp_parent_name", ctx -> candidateOf(ctx).displayName());
        bindings.placeholder("warp_parent_lore", this::lore);
        bindings.action("warps:category-parent-assign", this::assignClicked);
        bindings.action("warps:category-parent-clear", this::clearClicked);
        bindings.action("warps:category-parent-back", this::backClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the selector for {@code viewer} to set the parent of {@code category}, returning to its settings on pick. */
    public void open(Player player, PlayerRef viewer, WarpCategory category) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(category, "category");
        List<WarpCategory> candidates = categoryRepository.all().stream()
                .filter(candidate -> !candidate.id().equals(category.id()))
                .toList();
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, new Selection(category, candidates)));
    }

    /**
     * The bound candidate's lore as one string the renderer splits per line: its own lore lines, a spacer, its id line,
     * a spacer, then the click hint. This is what the bespoke grid drew, line for line.
     */
    private String lore(MenuContext ctx) {
        WarpCategory candidate = candidateOf(ctx);
        List<String> lines = new ArrayList<>(candidate.displayLore());
        lines.add("");
        lines.add(messages.resolve(
                ctx.viewer(), WarpsMessageKey.WARP_EDITOR_CATEGORY_ICON_ID, Map.of("id", candidate.id())));
        lines.add("");
        lines.add(messages.resolve(
                ctx.viewer(), WarpsMessageKey.WARP_EDITOR_CATEGORY_PARENT_SELECTOR_SELECT_HINT, Map.of()));
        return String.join("\n", lines);
    }

    /** Left-click a candidate: nest the edited category under it and reopen that category's settings. */
    private void assignClicked(MenuActionContext ctx) {
        assign(ctx, Optional.of(ctx.entry(WarpCategory.class).id()));
    }

    /** Left-click the clear button: drop the parent and reopen the settings panel. */
    private void clearClicked(MenuActionContext ctx) {
        assign(ctx, Optional.empty());
    }

    /** Left-click back: reopen the settings panel, changing nothing. */
    private void backClicked(MenuActionContext ctx) {
        settingsView.open(ctx.player(), ctx.viewer(), editedOf(ctx));
    }

    /** Save the edited category under {@code parentId} through the repository, then reopen its settings. */
    private void assign(MenuActionContext ctx, Optional<String> parentId) {
        WarpCategory updated = editedOf(ctx).withParentCategoryId(parentId);
        categoryRepository.save(updated);
        settingsView.open(ctx.player(), ctx.viewer(), updated);
    }

    private static WarpCategory candidateOf(MenuContext ctx) {
        return ctx.entry(WarpCategory.class);
    }

    private static WarpCategory editedOf(MenuActionContext ctx) {
        return ctx.subject(Selection.class).edited();
    }

    /**
     * The subject of an open parent selector: the category being edited and the candidates it may nest under,
     * snapshotted before the open so the engine renders without a repository read of its own.
     *
     * @param edited the category whose parent a click sets
     * @param candidates every other category, in repository order
     */
    public record Selection(WarpCategory edited, List<WarpCategory> candidates) {

        public Selection {
            Objects.requireNonNull(edited, "edited");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }
}

package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the kit to category selector with the menu engine and opens it. A kit's settings panel opens this to file
 * the kit under one of the configured categories: one icon per {@link KitCategory}, a button that clears the kit's
 * category, and a back button, each returning the viewer to the settings panel it came from.
 *
 * <p>The grid is the {@code kits:category-selector} list source, snapshotted at open and handed to the engine as the
 * menu subject. The category's own icon, display name and lore reach the spec through the {@code kit_category_*}
 * placeholders because they are authored per category; the geometry, the materials and which slot holds which button
 * live in {@code modules/kits/gui/kit-category-selector.conf} and are an operator's to change.
 */
@NullMarked
public final class KitCategorySelectorMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "kit-category-selector";

    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-category-selector.conf";

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final KitCategoryRepository categoryRepository;
    private final KitEditor kitEditor;
    private final KitSettingsView settingsView;

    public KitCategorySelectorMenu(
            Menus menus,
            Messages messages,
            Scheduler scheduler,
            KitCategoryRepository categoryRepository,
            KitEditor kitEditor,
            KitSettingsView settingsView) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.kitEditor = Objects.requireNonNull(kitEditor, "kitEditor");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
    }

    /** Register the bindings the spec names and the spec itself; called once at kits wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(
                "kits:category-selector", ctx -> ctx.subject(Selection.class).categories());
        bindings.placeholder(
                "kit_category_icon",
                ctx -> KitCategoryIcons.material(categoryOf(ctx)).name());
        bindings.placeholder("kit_category_name", ctx -> categoryOf(ctx).displayName());
        bindings.placeholder("kit_category_lore", this::lore);
        bindings.action("kits:category-assign", this::assignClicked);
        bindings.action("kits:category-clear", this::clearClicked);
        bindings.action("kits:category-back", this::backClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the selector for {@code viewer} to file {@code kit}, returning to that kit's settings on any click. */
    public void open(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kit, "kit");
        scheduler.onEntity(
                viewer, () -> menus.open(viewer, SPEC_ID, new Selection(kit, List.copyOf(categoryRepository.all()))));
    }

    /**
     * The bound category's lore as one string the renderer splits per line: the category's own lore lines, a spacer,
     * the category id line, a spacer, then the click hint. This is what the bespoke grid drew, line for line.
     */
    private String lore(MenuContext ctx) {
        KitCategory category = categoryOf(ctx);
        List<String> lines = new ArrayList<>(category.displayLore());
        lines.add("");
        lines.add(messages.resolve(
                ctx.viewer(), KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lines.add("");
        lines.add(messages.resolve(ctx.viewer(), KitsMessageKey.KIT_EDITOR_CATEGORY_SELECTOR_SELECT_HINT, Map.of()));
        return String.join("\n", lines);
    }

    /** Left-click a category icon: file the kit under it and reopen the kit's settings. */
    private void assignClicked(MenuActionContext ctx) {
        assign(ctx, Optional.of(ctx.entry(KitCategory.class).id()));
    }

    /** Left-click the clear button: drop the kit's category and reopen its settings. */
    private void clearClicked(MenuActionContext ctx) {
        assign(ctx, Optional.empty());
    }

    /** Left-click back: reopen the kit's settings, changing nothing. */
    private void backClicked(MenuActionContext ctx) {
        settingsView.open(ctx.player(), ctx.viewer(), kitOf(ctx));
    }

    /** Save the kit under {@code categoryId} through the editor, then reopen its settings. */
    private void assign(MenuActionContext ctx, Optional<String> categoryId) {
        KitDefinition updated = kitOf(ctx).withCategoryId(categoryId);
        kitEditor.save(ctx.viewer(), updated);
        settingsView.open(ctx.player(), ctx.viewer(), updated);
    }

    private static KitCategory categoryOf(MenuContext ctx) {
        return ctx.entry(KitCategory.class);
    }

    private static KitDefinition kitOf(MenuActionContext ctx) {
        return ctx.subject(Selection.class).kit();
    }

    /**
     * The subject of an open selector: the kit being filed and the categories to choose from, snapshotted before the
     * open so the engine renders without a repository read of its own.
     *
     * @param kit the kit a click files under the clicked category
     * @param categories the configured categories, in repository order
     */
    public record Selection(KitDefinition kit, List<KitCategory> categories) {

        public Selection {
            Objects.requireNonNull(kit, "kit");
            categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        }
    }
}

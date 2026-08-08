package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the kit category manager with the menu engine and opens it. The admin kit manager opens this to author the
 * category tree: one icon per configured {@link KitCategory}, a create button that prompts for a category id through
 * the shared input seam, and a back button to the kit manager.
 *
 * <p>The grid is the {@code kits:category-manager} list source, snapshotted at open and handed to the engine as the
 * menu subject. Each category's icon, display name and lore reach the spec through the {@code kit_manager_category_*}
 * placeholders because they are authored per category; the geometry, the materials and which slot holds which button
 * live in {@code modules/kits/gui/kit-category-manager.conf} and are an operator's to change.
 */
@NullMarked
public final class KitCategoryManagerMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "kit-category-manager";

    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-category-manager.conf";

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final KitCategoryRepository categoryRepository;
    private final TextInput textInput;

    /** The settings editor opened on a category click and after a create; injected after this menu to break the cycle. */
    private @Nullable KitCategorySettingsView settingsView;

    /** Reopens the kit manager when the back button is clicked; injected after this menu to break the cycle. */
    private @Nullable BiConsumer<Player, PlayerRef> onBack;

    public KitCategoryManagerMenu(
            Menus menus,
            Messages messages,
            Scheduler scheduler,
            KitCategoryRepository categoryRepository,
            TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Wire the two collaborators that close the manager to settings to back loop. The settings editor opens this
     * manager again on its own back button, and the back button reopens the kit manager, so all three are built before
     * any one of them exists; this setter breaks that cycle.
     */
    public void bind(KitCategorySettingsView settingsView, BiConsumer<Player, PlayerRef> onBack) {
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Register the bindings the spec names and the spec itself; called once at kits wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("kits:category-manager", ctx -> ctx.subject(Listing.class).categories());
        bindings.placeholder(
                "kit_manager_category_icon",
                ctx -> KitCategoryIcons.material(categoryOf(ctx)).name());
        bindings.placeholder("kit_manager_category_name", ctx -> categoryOf(ctx).displayName());
        bindings.placeholder("kit_manager_category_lore", this::lore);
        bindings.action("kits:category-manager-open", this::openClicked);
        bindings.action("kits:category-manager-create", this::createClicked);
        bindings.action("kits:category-manager-back", this::backClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the category manager for {@code viewer}; a category click opens its settings, create prompts for an id. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(
                viewer, () -> menus.open(viewer, SPEC_ID, new Listing(List.copyOf(categoryRepository.all()))));
    }

    /**
     * The bound category's lore as one string the renderer splits per line: the category's own lore lines, a spacer,
     * its id, slot and parent, a spacer, then the edit hint. This is what the bespoke grid drew, line for line.
     */
    private String lore(MenuContext ctx) {
        KitCategory category = categoryOf(ctx);
        PlayerRef viewer = ctx.viewer();
        List<String> lines = new ArrayList<>(category.displayLore());
        lines.add("");
        lines.add(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_ID, Map.of("id", category.id())));
        lines.add(text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_SLOT,
                Map.of("slot", Integer.toString(category.slot()))));
        lines.add(text(
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_ICON_PARENT,
                Map.of("parent", category.parentCategoryId().orElse(none(viewer)))));
        lines.add("");
        lines.add(text(viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_LORE_EDIT_HINT, Map.of()));
        return String.join("\n", lines);
    }

    /** Left-click a category icon: open its settings panel. */
    private void openClicked(MenuActionContext ctx) {
        openSettings(ctx.player(), ctx.viewer(), ctx.entry(KitCategory.class));
    }

    /** Left-click create: prompt for a category id, then save it and open its settings. */
    private void createClicked(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("kit.category.create-name", KitsMessageKey.KIT_EDITOR_CATEGORY_PROMPT_CREATE),
                name -> create(player, viewer, name),
                () -> open(player, viewer));
    }

    /** Left-click back: reopen the kit manager, changing nothing. */
    private void backClicked(MenuActionContext ctx) {
        if (onBack != null) {
            onBack.accept(ctx.player(), ctx.viewer());
        }
    }

    /**
     * Save a new category under the sanitized id and open its settings; a name with a space sanitizes to empty and is
     * rejected, exactly as the old window did. Package-private so the create branch is unit-tested without an anvil.
     */
    void create(Player player, PlayerRef viewer, String name) {
        String clean = sanitizeId(name);
        if (clean.isEmpty()) {
            player.sendMessage(StyledText.render(
                    messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME, Map.of())));
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

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_NONE, Map.of());
    }

    private String text(PlayerRef viewer, KitsMessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    private static KitCategory categoryOf(MenuContext ctx) {
        return ctx.entry(KitCategory.class);
    }

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    /**
     * The subject of an open manager: the configured categories, snapshotted before the open so the engine renders
     * without a repository read of its own.
     *
     * @param categories the configured categories, in repository order
     */
    public record Listing(List<KitCategory> categories) {

        public Listing {
            categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        }
    }
}

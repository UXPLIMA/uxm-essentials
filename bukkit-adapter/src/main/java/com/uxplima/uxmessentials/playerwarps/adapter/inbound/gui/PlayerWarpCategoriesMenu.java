package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the player-warps landing ({@code pwarp-categories}) that a bare {@code /pwarp} opens. It is a
 * hub, not a warp list: four quick-entry buttons each open the paged {@link PlayerWarpBrowseMenu} with a preset filter
 * — the public browse, the viewer's own warps ({@code owner=<uuid>}), their favourites ({@code favouritesOf=<uuid>}),
 * and the top-rated view — and a button per defined category drills into the browse filtered to that category
 * ({@code category=<id>}). The landing never materialises the warp table; every entry hands off to the browse, whose
 * one bounded page query does the work.
 *
 * <p>The category buttons are the {@code playerwarps:categories} list source: a bounded snapshot of the shared
 * {@code warp_categories} set (player-warps share the categories the warps context defines) taken off the tick thread
 * when the menu opens and carried on the engine {@link Subject}. The snapshot is defensive — a read failure or an empty
 * set simply draws no category buttons rather than aborting the open. The sponsor slots are reserved for P6 (paid
 * placement): their view condition reads a sub-feature flag that is off until then, so today they never render.
 */
@NullMarked
public final class PlayerWarpCategoriesMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "pwarp-categories";

    /** The category list-source id the spec's grid binds and this menu registers. */
    public static final String CATEGORY_SOURCE = "playerwarps:categories";

    /** Disk-first then bundled, mirroring the sibling player-warps menus, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/playerwarps/gui/pwarp-categories.conf";

    /** The icon a category with no configured display material falls back to. */
    private static final Material FALLBACK_ICON = Material.CHEST;

    /**
     * Whether the sponsor sub-feature is enabled; false until P6 wires the flag, so the reserved sponsor slots never
     * render yet. Mirrors the manage panel's sponsor button, which is gated on the same not-yet-live flag.
     */
    private static final boolean SPONSOR_ENABLED = false;

    private final Menus menus;
    private final Scheduler scheduler;
    private final WarpCategoryRepository categories;
    private final BrowseOpener browseOpener;

    public PlayerWarpCategoriesMenu(
            Menus menus, Scheduler scheduler, WarpCategoryRepository categories, BrowseOpener browseOpener) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.browseOpener = Objects.requireNonNull(browseOpener, "browseOpener");
    }

    /** Register the category source, the entry placeholders, the sponsor condition, the actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(CATEGORY_SOURCE, ctx -> ctx.subject(Subject.class).categories());
        bindings.placeholder("pwarp_category_icon", this::icon);
        bindings.placeholder(
                "pwarp_category_name", ctx -> ctx.entry(WarpCategory.class).displayName());
        bindings.condition(
                "playerwarps:categories-sponsor",
                (ctx, args) -> ctx.subject(Subject.class).sponsorEnabled());
        bindings.action("playerwarps:browse-all", ctx -> browseOpener.open(ctx.player(), ctx.viewer(), Map.of()));
        bindings.action("playerwarps:browse-mine", ctx -> openBrowse(ctx, "owner"));
        bindings.action("playerwarps:browse-favourites", ctx -> openBrowse(ctx, "favouritesOf"));
        bindings.action(
                "playerwarps:browse-top",
                ctx -> browseOpener.open(ctx.player(), ctx.viewer(), Map.of("sort", "rating")));
        bindings.action("playerwarps:category-open", this::openCategory);
        menus.registerSpec(SPEC_ID, loadSpec(dataFolder, log));
    }

    /**
     * Open the landing for {@code viewer}. The bounded category set is snapshotted off the tick thread, then the window
     * is painted on the viewer's entity thread. The live {@code player} is kept only for call-site symmetry with the
     * browse opener the command drives; the engine resolves the live player from the viewer to render.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            Subject subject = new Subject(snapshotCategories(), SPONSOR_ENABLED);
            scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, subject));
        });
    }

    /** Open the browse pre-filtered to the viewer under {@code key} (owner or favourites), from a quick-entry click. */
    private void openBrowse(MenuActionContext ctx, String key) {
        browseOpener.open(
                ctx.player(), ctx.viewer(), Map.of(key, ctx.viewer().uuid().toString()));
    }

    /** Drill into the browse filtered to the clicked category. */
    private void openCategory(MenuActionContext ctx) {
        WarpCategory category = ctx.entry(WarpCategory.class);
        browseOpener.open(ctx.player(), ctx.viewer(), Map.of("category", category.id()));
    }

    /** The category cell's icon token — the category's configured display material, else the fallback. */
    private String icon(MenuContext ctx) {
        return ctx.entry(WarpCategory.class).displayMaterial().orElse(FALLBACK_ICON.name());
    }

    /**
     * The bounded category snapshot for the list source, ordered by the operator's configured slot then id for a stable
     * layout. Read off the tick thread at open; any failure (a store hiccup, a missing table on a stripped install)
     * degrades to no category buttons rather than aborting the landing.
     */
    private List<WarpCategory> snapshotCategories() {
        try {
            return categories.all().stream()
                    .sorted(Comparator.comparingInt(WarpCategory::slot).thenComparing(WarpCategory::id))
                    .toList();
        } catch (RuntimeException failure) {
            return List.of();
        }
    }

    /**
     * Load the spec, preferring an operator's edit on disk over the bundled resource and finally a built-in empty
     * fallback, so a typo or a missing file degrades to a closeable empty window rather than aborting player-warps
     * wiring. Resolution mirrors the sibling player-warps menus: disk first, then the classpath default.
     */
    private MenuSpec loadSpec(Path dataFolder, Logger log) {
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(SPEC_RESOURCE);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundledSpec(specLoader, log);
    }

    private MenuSpec loadBundledSpec(MenuSpecLoader specLoader, Logger log) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", SPEC_RESOURCE);
                return emptySpec();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + SPEC_RESOURCE, failure);
            return emptySpec();
        }
    }

    /** A minimal valid spec used only when the real one cannot be read, so player-warps still wires cleanly. */
    private static MenuSpec emptySpec() {
        return new MenuSpec("", 6, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Opens the paged browse with a preset filter, on the viewer's entity thread. The landing's quick entries and each
     * category button drive this; production binds it to {@link PlayerWarpBrowseMenu#open(Player, PlayerRef, Map)}.
     */
    @FunctionalInterface
    public interface BrowseOpener {
        void open(Player player, PlayerRef viewer, Map<String, String> filters);
    }

    /**
     * The subject of an open landing: the snapshotted category set the list source reads and whether the sponsor
     * sub-feature is enabled. Both are resolved at open and read only by the render-time list source and the sponsor
     * view condition, so the menu touches no per-viewer mutable state.
     *
     * @param categories the bounded category snapshot taken off the tick thread at open (empty when none or unreadable)
     * @param sponsorEnabled whether the P6 sponsor sub-feature is enabled; false until P6, so the sponsor slots never
     *     render yet
     */
    public record Subject(List<WarpCategory> categories, boolean sponsorEnabled) {

        public Subject {
            categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        }
    }
}

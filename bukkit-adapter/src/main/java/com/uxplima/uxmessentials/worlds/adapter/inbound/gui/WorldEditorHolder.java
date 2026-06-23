package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a {@code /worlds editor} window, so the editor listener can recognise a
 * click or close as belonging to one of these views (and never to a vanilla container the editor happens to have
 * open) and read which {@link WorldEditorScreen} it is, the world being edited, the page within it, and who is
 * editing. The {@link #world()} is {@code null} on the {@link WorldEditorScreen#LIST} world-picker screen, which
 * has no world selected yet.
 *
 * <p>The holder is created first and the menu is built against it; {@link #attach} then stores the built
 * inventory so {@link #getInventory()} can answer it, the way Bukkit's holder contract expects.
 *
 * <p>The {@link #draft()} is non-null only on the {@link WorldEditorScreen#CREATE} new-world screen, which carries
 * its in-flight {@link WorldCreateDraft} on the window so a selector cycle or an input submission can rebuild the
 * screen from the prior choices; every other screen leaves it {@code null}.
 */
@NullMarked
public final class WorldEditorHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final WorldEditorScreen screen;
    private final @Nullable WorldName world;
    private final int page;
    private final @Nullable WorldCreateDraft draft;
    private @Nullable Inventory inventory;

    public WorldEditorHolder(PlayerRef viewer, WorldEditorScreen screen, @Nullable WorldName world, int page) {
        this(viewer, screen, world, page, null);
    }

    public WorldEditorHolder(
            PlayerRef viewer,
            WorldEditorScreen screen,
            @Nullable WorldName world,
            int page,
            @Nullable WorldCreateDraft draft) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.world = world;
        this.page = page;
        this.draft = draft;
    }

    /** The staff member viewing the editor; prompts and feedback are attributed to them. */
    public PlayerRef viewer() {
        return viewer;
    }

    /** Which editor screen this window is showing. */
    public WorldEditorScreen screen() {
        return screen;
    }

    /** The world being edited, or {@code null} on the {@link WorldEditorScreen#LIST} world-picker screen. */
    public @Nullable WorldName world() {
        return world;
    }

    /** The page within the current screen, for screens that paginate. */
    public int page() {
        return page;
    }

    /** The in-flight new-world draft on the {@link WorldEditorScreen#CREATE} screen, or {@code null} elsewhere. */
    public @Nullable WorldCreateDraft draft() {
        return draft;
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    public void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("world editor inventory not attached yet");
        }
        return built;
    }
}

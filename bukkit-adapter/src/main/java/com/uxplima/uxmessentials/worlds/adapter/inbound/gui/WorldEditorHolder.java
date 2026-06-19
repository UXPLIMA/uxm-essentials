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
 */
@NullMarked
public final class WorldEditorHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final WorldEditorScreen screen;
    private final @Nullable WorldName world;
    private final int page;
    private @Nullable Inventory inventory;

    public WorldEditorHolder(PlayerRef viewer, WorldEditorScreen screen, @Nullable WorldName world, int page) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.world = world;
        this.page = page;
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

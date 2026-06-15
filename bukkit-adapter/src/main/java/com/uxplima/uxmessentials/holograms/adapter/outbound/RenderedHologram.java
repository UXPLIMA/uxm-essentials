package com.uxplima.uxmessentials.holograms.adapter.outbound;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.ModelHologram;
import org.jspecify.annotations.NullMarked;

/**
 * The renderer's view of a live uxmLib hologram, unifying the only operations the renderer performs on a
 * spawned entity — despawn, restrict-to-viewers, and per-viewer show/hide — across the text {@code Hologram}
 * and the item/block {@link ModelHologram}, which share that lifecycle but no common Java interface in the lib.
 * Each hologram type's spawn returns one of the two adapters below so the renderer's spawn, despawn and
 * visibility code is type-agnostic past the dispatch point.
 */
@NullMarked
interface RenderedHologram {

    /** Despawn the backing entity through {@code manager} so it is also untracked. */
    void removeFrom(HologramManager manager);

    /** Make the entity visible only to explicitly shown players. */
    void restrictToViewers();

    /** Show the entity to {@code viewer}. */
    void show(Plugin plugin, Player viewer);

    /** Hide the entity from {@code viewer}. */
    void hide(Plugin plugin, Player viewer);

    /** A {@link RenderedHologram} over a text {@code Hologram}. */
    static RenderedHologram ofText(com.uxplima.uxmlib.hologram.Hologram text) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                manager.remove(text);
            }

            @Override
            public void restrictToViewers() {
                text.restrictToViewers();
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                text.show(plugin, viewer);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                text.hide(plugin, viewer);
            }
        };
    }

    /** A {@link RenderedHologram} over an item or block {@link ModelHologram}. */
    static RenderedHologram ofModel(ModelHologram model) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                manager.remove(model);
            }

            @Override
            public void restrictToViewers() {
                model.restrictToViewers();
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                model.show(plugin, viewer);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                model.hide(plugin, viewer);
            }
        };
    }
}

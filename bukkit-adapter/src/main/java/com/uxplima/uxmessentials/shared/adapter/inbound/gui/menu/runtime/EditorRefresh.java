package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The one in-place re-render of an open editor, shared by the {@code Menus} façade (the {@code reRenderEditor} call
 * a property's reopen hook runs) and the click listener. It mirrors {@code Menus.reRender} for spec menus: hop to the
 * viewer's entity thread, confirm the live top inventory is still this holder's editor window, clear the editor's
 * slot routing, and repaint the same inventory from the live subject. There is no second {@code openInventory} and no
 * new holder — the window the viewer is looking at is reused, so the one listener and one teardown keep owning it.
 *
 * <p>Centralising it here keeps the {@code runtime}→{@code menu} edge acyclic: the listener (in {@code runtime}) and
 * the façade (in {@code menu}) both call this rather than the listener reaching into the façade.
 */
@NullMarked
public final class EditorRefresh {

    private EditorRefresh() {}

    /** Hop to the viewer's thread and repaint {@code holder}'s editor in place, if its window is still showing. */
    public static void reRender(MenuHolder holder, EditorRenderer renderer, Scheduler scheduler) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(scheduler, "scheduler");
        scheduler.onEntity(holder.ctx().viewer(), () -> repaint(holder, renderer));
    }

    private static void repaint(MenuHolder holder, EditorRenderer renderer) {
        EditorState state = holder.editor().orElse(null);
        if (state == null) {
            return;
        }
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        if (!(live.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
            return;
        }
        state.clearSlots();
        EditorSpec spec = (EditorSpec) state.spec();
        renderer.populate(holder.getInventory(), spec, state, live, holder.ctx().viewer());
    }
}

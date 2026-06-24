package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point a feature uses to open a registered menu for a viewer. A feature registers its specs once at
 * wiring time, then calls {@link #open} to show one to a player. The façade hops onto the viewer's entity thread
 * (where the live inventory may legally be touched), builds the {@link MenuHolder} that owns every per-open piece
 * of state, renders the spec into a fresh inventory the holder backs, and arms the refresh task — so the click
 * listener can recover all of it from the window alone and no player-keyed side map is needed.
 */
public final class Menus {

    private final MenuRenderer renderer;
    private final GuiText guiText;
    private final Scheduler scheduler;

    private final Map<String, MenuSpec> specs = new ConcurrentHashMap<>();

    public Menus(MenuRenderer renderer, GuiText guiText, Scheduler scheduler) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Registers a parsed spec under its id; a feature does this once at wiring time. */
    public void registerSpec(String id, MenuSpec spec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        specs.put(id, spec);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer}, carrying {@code subject} as the domain
     * object the menu is about (or null for a subject-less menu). An unknown spec id is a coding error in the
     * caller's wiring, so it fails loudly here rather than opening an empty window a player would meet.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        MenuSpec spec = specs.get(specId);
        if (spec == null) {
            throw new IllegalArgumentException("no menu spec registered under id: " + specId);
        }
        scheduler.onEntity(viewer, () -> openResolved(viewer, specId, spec, subject));
    }

    /** On the viewer's entity thread: build the holder-backed window, render it, show it, and arm refresh. */
    private void openResolved(PlayerRef viewer, String specId, MenuSpec spec, @Nullable Object subject) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(specId, spec, ctx);
        Inventory inv = Bukkit.createInventory(holder, spec.rows() * 9, title(viewer, spec));
        holder.attach(inv);
        renderer.populate(inv, spec, ctx, holder::recordSlot);
        live.openInventory(inv);
        MenuRefresh.start(holder, scheduler, () -> reRender(holder));
    }

    /** Redraw an open menu in place on its viewer's thread, but only if that window is still this holder's. */
    private void reRender(MenuHolder holder) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player p = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (p == null) {
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
                return;
            }
            holder.clearClickMap();
            renderer.populate(holder.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot);
        });
    }

    /**
     * The window title for {@code spec}. An empty title is left empty; a {@code @key} title resolves through the
     * locale catalog in the viewer's language; any other title is an inline MiniMessage literal.
     */
    private Component title(PlayerRef viewer, MenuSpec spec) {
        if (spec.title().isEmpty()) {
            return Component.empty();
        }
        if (spec.title().startsWith("@")) {
            return guiText.text(viewer, () -> spec.title().substring(1), Map.of());
        }
        return StyledText.render(spec.title());
    }

    /**
     * Close every menu this engine owns, cancelling its refresh task first so no timer survives the disable. The
     * online-roster sweep runs on the global region thread (the only thread on which the roster is coherent on
     * Folia), matching the scoreboard/tablist tear-down pattern. The click listener is uninstalled by the bootstrap
     * wiring, not here, so a closed window cannot be re-clicked.
     */
    public void shutdown() {
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder open =
                        player.getOpenInventory().getTopInventory().getHolder();
                if (open instanceof MenuHolder holder) {
                    holder.cancelRefresh();
                    player.closeInventory();
                }
            }
        });
    }
}

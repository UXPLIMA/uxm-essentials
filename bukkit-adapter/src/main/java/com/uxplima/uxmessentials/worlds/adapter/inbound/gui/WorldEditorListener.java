package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.CycleAction;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldPropertyCycle;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.jspecify.annotations.NullMarked;

/**
 * The single click-routing listener tying the four world-editor views together. It recognises a click in any
 * {@code /worlds editor} window by its {@link WorldEditorHolder}, cancels it, and dispatches by the holder's
 * {@link WorldEditorScreen}: list navigation and world selection (left-click edits, right-click teleports the viewer
 * to that world), the main hub's branch/back/toggle buttons, the
 * read-only generation back button, and the rules/access property grids where a click cycles the clicked property's
 * value through {@link WorldPropertyCycle} and persists it via {@link com.uxplima.uxmessentials.worlds.application.SetWorldProperty}.
 *
 * <p>A property cycle is optimistic: the new value is written through the use case (which persists off-tick) and the
 * single clicked button is rebuilt in place showing that value, so the viewer sees the change immediately without
 * re-reading the cache the async save updates. The listener never writes settings directly — every mutation goes
 * through the {@code SetWorldProperty}, {@code LoadWorld}, or {@code UnloadWorld} use cases.
 */
@NullMarked
public final class WorldEditorListener implements Listener {

    private final WorldListView listView;
    private final WorldCreateView createView;
    private final WorldMainView mainView;
    private final WorldGenerationView generationView;
    private final WorldPropertyGridView gridView;
    private final WorldsServices services;
    private final WorldRepository repository;
    private final WorldEngine engine;

    public WorldEditorListener(
            WorldListView listView,
            WorldCreateView createView,
            WorldMainView mainView,
            WorldGenerationView generationView,
            WorldPropertyGridView gridView,
            WorldsServices services,
            WorldRepository repository,
            WorldEngine engine) {
        this.listView = Objects.requireNonNull(listView, "listView");
        this.createView = Objects.requireNonNull(createView, "createView");
        this.mainView = Objects.requireNonNull(mainView, "mainView");
        this.generationView = Objects.requireNonNull(generationView, "generationView");
        this.gridView = Objects.requireNonNull(gridView, "gridView");
        this.services = Objects.requireNonNull(services, "services");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        // The views and SetWorldProperty self-schedule their entity/async hops, so the listener holds no scheduler
        // of its own — the optimistic single-slot rebuild is the only synchronous work, and it runs on the click
        // thread.
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WorldEditorHolder h)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        switch (h.screen()) {
            case LIST -> onList(player, h, slot, event);
            case CREATE -> onCreate(player, h, slot, event);
            case MAIN -> onMain(player, h, slot);
            case GENERATION -> onGeneration(player, h, slot);
            case RULES, ACCESS -> onGrid(player, h, slot, event);
        }
    }

    private void onList(Player player, WorldEditorHolder h, int slot, InventoryClickEvent event) {
        GuiLayout layout = listView.layout();
        if (slot == layout.prevSlot()) {
            listView.open(player, h.viewer(), Math.max(0, h.page() - 1));
        } else if (slot == layout.nextSlot()) {
            listView.open(player, h.viewer(), h.page() + 1);
        } else if (listView.isCreate(slot)) {
            createView.open(player, h.viewer());
        } else {
            listView.worldAt(h.page(), slot).ifPresent(world -> selectWorld(player, h, world, event.isRightClick()));
        }
    }

    private void selectWorld(Player player, WorldEditorHolder h, WorldName world, boolean teleport) {
        if (teleport) {
            visit(player, h, world);
        } else {
            mainView.open(player, h.viewer(), world);
        }
    }

    private void visit(Player player, WorldEditorHolder h, WorldName world) {
        // Right-clicking a world icon teleports the viewer there, mirroring /worlds tp <self>: the staff override
        // skips the access gate and entry fee (the editor is already permission-gated), and the load-then-teleport
        // runs on the global region thread because loading a world is legal under Folia only there.
        player.closeInventory();
        services.scheduler().onGlobal(() -> services.worldTeleport().forced(h.viewer(), h.viewer(), world));
    }

    private void onCreate(Player player, WorldEditorHolder h, int slot, InventoryClickEvent event) {
        WorldCreateDraft draft = h.draft();
        if (draft != null) {
            createView.onClick(player, h.viewer(), draft, slot, event.isRightClick());
        }
    }

    private void onMain(Player player, WorldEditorHolder h, int slot) {
        WorldName world = h.world();
        if (world == null) {
            return;
        }
        mainView.actionAt(slot).ifPresent(action -> dispatchMain(player, h, world, action));
    }

    private void dispatchMain(Player player, WorldEditorHolder h, WorldName world, MainAction action) {
        switch (action) {
            case RULES ->
                gridView.open(
                        player, h.viewer(), world, WorldEditorScreen.RULES, WorldPropertyGridView.RULES_PROPERTIES);
            case GENERATION -> generationView.open(player, h.viewer(), world);
            case ACCESS ->
                gridView.open(
                        player, h.viewer(), world, WorldEditorScreen.ACCESS, WorldPropertyGridView.ACCESS_PROPERTIES);
            case BACK -> listView.open(player, h.viewer(), 0);
            case TOGGLE_LOAD -> toggleLoad(player, h, world);
        }
    }

    private void onGeneration(Player player, WorldEditorHolder h, int slot) {
        WorldName world = h.world();
        if (world != null && generationView.isBack(slot)) {
            mainView.open(player, h.viewer(), world);
        }
    }

    private void onGrid(Player player, WorldEditorHolder h, int slot, InventoryClickEvent event) {
        WorldName world = h.world();
        if (world == null) {
            return;
        }
        if (gridView.isBack(slot)) {
            mainView.open(player, h.viewer(), world);
            return;
        }
        List<WorldProperty<?>> props = propertiesFor(h.screen());
        gridView.propertyAt(slot, props).ifPresent(prop -> cycle(event, h, world, slot, prop));
    }

    private void cycle(
            InventoryClickEvent event, WorldEditorHolder h, WorldName world, int slot, WorldProperty<?> prop) {
        String current = currentRaw(world, prop);
        CycleAction action = actionFor(event);
        String next = WorldPropertyCycle.next(prop, current, action, worldNames());
        if (!next.equals(current)) {
            services.setWorldProperty().set(h.viewer(), world, prop.key(), next);
            event.getInventory().setItem(slot, gridView.button(h.viewer(), prop, next));
        }
    }

    private void toggleLoad(Player player, WorldEditorHolder h, WorldName world) {
        // The world load/unload reaches WorldCreator.createWorld()/server.unloadWorld(), which Folia permits only
        // on the global region thread — the inventory click fires on the clicking player's region thread, so hop
        // to global before touching world state, then re-open the hub (which self-schedules back onto the viewer's
        // entity thread) so the toggled load state is reflected.
        services.scheduler().onGlobal(() -> {
            if (engine.isLoaded(world)) {
                services.unloadWorld().unload(h.viewer(), world, true);
            } else {
                services.loadWorld().load(h.viewer(), world);
            }
            mainView.open(player, h.viewer(), world);
        });
    }

    private String currentRaw(WorldName world, WorldProperty<?> prop) {
        return repository.find(world).map(mw -> encode(mw.settings(), prop)).orElse("");
    }

    private <T> String encode(WorldSettings settings, WorldProperty<T> property) {
        return property.encode(settings.get(property));
    }

    private List<String> worldNames() {
        return repository.all().stream().map(world -> world.name().value()).toList();
    }

    private static CycleAction actionFor(InventoryClickEvent event) {
        if (event.isShiftClick()) {
            return CycleAction.CLEAR;
        }
        return event.isRightClick() ? CycleAction.BACKWARD : CycleAction.FORWARD;
    }

    private static List<WorldProperty<?>> propertiesFor(WorldEditorScreen screen) {
        return switch (screen) {
            case RULES -> WorldPropertyGridView.RULES_PROPERTIES;
            case ACCESS -> WorldPropertyGridView.ACCESS_PROPERTIES;
            default -> List.of();
        };
    }
}

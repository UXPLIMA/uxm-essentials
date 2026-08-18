package com.uxplima.uxmessentials.shared.menu;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Shared fixtures for the four world-editor screen golden tests: a synchronous engine wired off the same renderer,
 * bindings, listener and façade production uses, plus the in-memory repository/engine fakes the bespoke views were
 * tested against. The four screens share one holder, listener and {@code closeMenu}, so they share one harness too.
 * Each test wires only the screen(s) it drives onto {@link #bindings} and asserts through the engine's own
 * {@link MenuListener}, exactly as the picker golden test does.
 */
final class WorldEditorTestSupport {

    private WorldEditorTestSupport() {}

    /** Build a synchronous engine and return the bindings/menus/listener bundle a screen registers onto. */
    static Engine engine(ServerMock server, Plugin plugin, GuiText guiText, Scheduler scheduler) {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        return new Engine(bindings, menus);
    }

    /** Click the given content slot of the player's open menu with the given gesture through the production path. */
    static void fireClick(ServerMock server, PlayerMock player, int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    /** The plain-text display name of {@code slot} in {@code inv}, or empty when the slot is empty or unnamed. */
    static String plainNameAt(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        return item == null ? "" : plainName(item);
    }

    static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    record Snapshot(Material material, String name) {}

    /** The engine bundle a screen registers onto and opens through. */
    record Engine(MenuBindings bindings, Menus menus) {}

    /** A repository that records managed worlds in insertion order; {@code save} overwrites by name. */
    static final class FakeRepository implements WorldRepository {
        private final Map<String, ManagedWorld> byName = new LinkedHashMap<>();

        void seed(String name, WorldEnvironment environment) {
            WorldSpec spec = new WorldSpec(
                    environment,
                    com.uxplima.uxmessentials.worlds.domain.WorldGenType.NORMAL,
                    Optional.empty(),
                    Optional.empty(),
                    true,
                    Optional.empty());
            save(ManagedWorld.created(WorldName.of(name), spec, true, Optional.empty(), Instant.EPOCH));
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name.value());
        }
    }

    /** A world engine fake that reports every world loaded with a fixed player count. */
    static final class FakeEngine implements WorldEngine {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.worlds.domain.WorldError>
                create(ManagedWorld world) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.worlds.domain.WorldError>
                load(ManagedWorld world) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.worlds.domain.WorldError>
                unload(WorldName name, boolean save) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<
                        com.uxplima.uxmessentials.shared.domain.Unit,
                        com.uxplima.uxmessentials.worlds.domain.WorldError>
                deleteFiles(WorldName name) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            // No unregistered on-disk worlds: a create gate (repository.exists || engine.exists) sees a fresh name.
            return false;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return true;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    /** A message resolver that surfaces a chosen GUI token through the placeholder map, else the bare key. */
    static final class TokenMessages implements Messages {
        private final String key;
        private final String token;

        TokenMessages(MessageKey key, String token) {
            this.key = key.key();
            this.token = token;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, Map<String, String> placeholders) {
            if (lookup.key().equals(key)) {
                return placeholders.getOrDefault(token, "");
            }
            return lookup.key();
        }
    }

    /** A message resolver that returns the bare key for every lookup, surfacing no token. */
    static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, Map<String, String> placeholders) {
            return lookup.key();
        }
    }

    static final class RecordingEvents implements DomainEventPublisher {
        final List<DomainEvent> published = new java.util.ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

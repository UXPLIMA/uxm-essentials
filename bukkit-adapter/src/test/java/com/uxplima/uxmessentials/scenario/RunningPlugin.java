package com.uxplima.uxmessentials.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.bootstrap.UxmEssentialsPlugin;
import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

/**
 * The plugin enabled on a MockBukkit server, driven the way players and an operator drive it: joining, running
 * commands, clicking windows and leaving. Every warning and error the plugin logs, and anything its async work throws,
 * is kept for the scenario to check.
 *
 * <p>The files a scenario hands in are written into the data folder before the plugin enables, which is how an
 * operator's edited or older file reaches the start.
 */
final class RunningPlugin implements AutoCloseable {

    /**
     * Modules whose wiring reaches into the server's internals, which MockBukkit does not have. They are checked on a
     * development server instead.
     */
    static final List<String> NEEDS_SERVER_INTERNALS = List.of("holograms", "npc", "poses");

    /**
     * What the plugin rightly warns about on MockBukkit and nowhere else: the offline adapters reach into the
     * server's internals to read player files, and turn themselves off without them.
     */
    private static final List<String> WARNED_ONLY_WITHOUT_SERVER_INTERNALS =
            List.of("event=tpoffline_unavailable", "event=offline_inventories_unavailable");

    private static final String WORLD = "world";

    final ScenarioServer server;
    final UxmEssentialsPlugin plugin;
    final World world;
    private final List<LogRecord> problems = new ArrayList<>();
    private final Problems listener = new Problems();

    private RunningPlugin(ScenarioServer server, UxmEssentialsPlugin plugin, World world) {
        this.server = server;
        this.plugin = plugin;
        this.world = world;
    }

    static RunningPlugin start() {
        return start(Map.of());
    }

    /** Starts with these files, paths relative to the data folder, in place of what the plugin would write. */
    static RunningPlugin start(Map<String, String> files) {
        Map<String, String> seeded = new LinkedHashMap<>();
        NEEDS_SERVER_INTERNALS.forEach(module -> seeded.put("modules/" + module + "/config.conf", "enabled = false\n"));
        seeded.putAll(files);
        ScenarioServer server = MockBukkit.mock(new ScenarioServer());
        @Nullable Handler attached = null;
        try {
            World world = server.addSimpleWorld(WORLD);
            UxmEssentialsPlugin plugin = (UxmEssentialsPlugin)
                    server.getPluginManager().loadPlugin(UxmEssentialsPlugin.class, new Object[0]);
            RunningPlugin running = new RunningPlugin(server, plugin, world);
            // The root logger, because the plugin also writes through SLF4J loggers of other names.
            Logger.getLogger("").addHandler(running.listener);
            attached = running.listener;
            Path folder = plugin.getDataFolder().toPath();
            seeded.forEach((path, content) -> write(folder.resolve(path), content));
            // bStats refuses to start unshaded, which is how the test class path has it, and it must send nothing.
            System.setProperty("bstats.relocatecheck", "false");
            write(folder.resolveSibling("bStats").resolve("config.yml"), "enabled: false\n");
            server.getPluginManager().enablePlugin(plugin);
            running.settle();
            return running;
        } catch (RuntimeException | Error failure) {
            close(server, attached);
            throw failure;
        }
    }

    Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    UxmEssentialsApi api() {
        return Objects.requireNonNull(
                server.getServicesManager().load(UxmEssentialsApi.class), "the plugin published no developer API");
    }

    /** A player joining through pre-login, login and join, standing on the ground in the world. */
    ScenarioPlayer join(String name) {
        return join(name, player -> {});
    }

    /**
     * A player joining as above, with {@code beforeJoin} run on them first: what the server already knows of a player
     * as they arrive, such as a permission or the client they connect from.
     */
    ScenarioPlayer join(String name, Consumer<ScenarioPlayer> beforeJoin) {
        ScenarioPlayer player = new ScenarioPlayer(server, name);
        beforeJoin.accept(player);
        player.setLocation(world.getSpawnLocation());
        server.addPlayer(player);
        settle();
        return player;
    }

    void quit(ScenarioPlayer player) {
        player.disconnect();
        settle();
    }

    /** Runs a command as this player and lets the work it starts finish. */
    boolean run(ScenarioPlayer player, String command) {
        boolean known = player.performCommand(command);
        settle();
        return known;
    }

    /** Runs a command from the console and lets the work it starts finish. */
    boolean console(String command) {
        boolean known = server.dispatchCommand(server.getConsoleSender(), command);
        settle();
        return known;
    }

    /** Left-clicks a slot of the window the player has open and lets the work it starts finish. */
    void click(ScenarioPlayer player, int slot) {
        new PlayerSimulation(player).simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, slot);
        settle();
    }

    /**
     * Puts this stack into a slot of the open window the way a player does, from the cursor: the click goes to the
     * plugin first, and only a click it lets through moves the stack. Returns whether it moved.
     */
    boolean place(ScenarioPlayer player, int slot, ItemStack stack) {
        InventoryView view = player.getOpenInventory();
        player.setItemOnCursor(stack);
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(click);
        boolean moved = !click.isCancelled();
        if (moved) {
            view.getTopInventory().setItem(slot, stack);
            player.setItemOnCursor(null);
        }
        settle();
        return moved;
    }

    /** The first slot of the open window holding this material. */
    int slotOf(ScenarioPlayer player, Material material) {
        Inventory top = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) {
            @Nullable ItemStack item = top.getItem(slot);
            if (item != null && item.getType() == material) {
                return slot;
            }
        }
        throw new AssertionError(player.getName() + "'s open window holds no " + material);
    }

    /** What the player was told since the last call, as plain text. */
    static List<String> said(ScenarioPlayer player) {
        List<String> lines = new ArrayList<>();
        @Nullable Component line;
        while ((line = player.nextComponentMessage()) != null) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return lines;
    }

    /** A chat line as the server raises it: asynchronously, on a chat thread, seen by everyone online. */
    AsyncChatEvent chat(ScenarioPlayer speaker, String line) {
        Component message = Component.text(line);
        AsyncChatEvent event = new AsyncChatEvent(
                true,
                speaker,
                new HashSet<>(server.getOnlinePlayers()),
                ChatRenderer.defaultRenderer(),
                message,
                message,
                SignedMessage.system(line, message));
        callOffTick(event);
        return event;
    }

    /**
     * Raises an event the server raises off the tick, such as a chat line, on a thread of its own, and lets the
     * server tick until every listener has seen it and the work they started has finished.
     *
     * <p>The wait is for the call itself, not for a number of ticks: the ticks run as fast as the machine allows while
     * nothing else is going on, so a listener that is slow against the clock, as any is on a busy machine, would
     * otherwise be outrun.
     */
    void callOffTick(Event event) {
        CompletableFuture<Void> called =
                CompletableFuture.runAsync(() -> server.getPluginManager().callEvent(event), server::runOffTick);
        settle();
        if (!called.isDone()) {
            throw new AssertionError("settled while the event was still being called");
        }
        called.join();
    }

    /** Waits for a future the plugin's API handed out, letting the server run while it does. */
    <T> T await(CompletableFuture<T> future) {
        for (int round = 0; round < 10 && !future.isDone(); round++) {
            settle();
        }
        if (!future.isDone()) {
            throw new AssertionError("the plugin did not answer within ten game seconds");
        }
        return future.join();
    }

    /** Every warning and error the plugin logged so far, and what its async work threw, one line each. */
    List<String> problems() {
        List<String> lines = new ArrayList<>();
        synchronized (problems) {
            problems.stream()
                    .filter(record -> WARNED_ONLY_WITHOUT_SERVER_INTERNALS.stream()
                            .noneMatch(String.valueOf(record.getMessage())::startsWith))
                    .forEach(record -> lines.add(record.getLevel() + " " + record.getMessage()
                            + (record.getThrown() == null ? "" : " (" + record.getThrown() + ")")));
        }
        server.asyncFailures().forEach(failure -> lines.add("async " + failure));
        return lines;
    }

    /** Runs one game second and waits for the storage work it started. */
    void settle() {
        server.settle();
    }

    @Override
    public void close() {
        close(server, listener);
    }

    private static void close(ScenarioServer server, @Nullable Handler listener) {
        try {
            MockBukkit.unmock();
        } finally {
            server.shutdownAsync();
            if (listener != null) {
                Logger.getLogger("").removeHandler(listener);
            }
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private final class Problems extends Handler {

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                synchronized (problems) {
                    problems.add(record);
                }
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.discordlink.adapter.inbound.gui.DiscordStatusView;
import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The Discord link-status golden test. The migrated {@link DiscordStatusView} now opens through
 * {@link Menus#openEditor} (its {@link SettingsPanelView} is a thin shim over the engine), and its unlink gate now
 * uses {@link Menus#confirm} in place of the uxmLib {@code ConfirmMenu}. This asserts the engine-rendered editor draws
 * the exact status panel the bespoke view drew slot-for-slot (the read-only status line, the state-dependent link /
 * unlink action, the back button), then drives the unlink flow: the unlink button opens an engine confirm window (a
 * confirm {@link MenuHolder}), and confirm-yes runs the same {@link Unlink} use case the {@code /discordunlink}
 * command drives. The baseline is frozen from the panel's geometry + catalog keys, the way the kit/warp golden tests
 * freeze a baseline.
 */
class DiscordStatusGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final int STATUS_SLOT = 11;
    private static final int ACTION_SLOT = 15;
    private static final int BACK_SLOT = 22;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Messages messages;
    private SyncScheduler scheduler;
    private InMemoryStore store;
    private BeginLink beginLink;
    private Unlink unlink;
    private LinkStatus linkStatus;
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        store = new InMemoryStore();
        Clock clock = Clock.systemUTC();
        beginLink = new BeginLink(store, clock, new Random(1), Duration.ofMinutes(10));
        unlink = new Unlink(store);
        linkStatus = new LinkStatus(store);
        notifier = new Notifier(new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenNotLinked() throws Exception {
        Map<Integer, Snapshot> baseline = baseline(false);
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenLinked() throws Exception {
        store.confirm(new ConfirmedLink(viewer.uuid(), DiscordId.of("123456789012345678"), Instant.now()));
        Map<Integer, Snapshot> baseline = baseline(true);
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void unlinkButtonOpensTheEngineConfirmAndConfirmYesRunsUnlink() throws Exception {
        store.confirm(new ConfirmedLink(viewer.uuid(), DiscordId.of("123456789012345678"), Instant.now()));
        view().open(player, viewer);

        fireClick(ACTION_SLOT, ClickType.LEFT); // opens the engine confirm window, unbinds nothing yet

        Inventory confirm = player.getOpenInventory().getTopInventory();
        assertThat(confirm.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) confirm.getHolder()).confirm()).isPresent(); // a confirm holder, not the editor
        assertThat(store.findByPlayer(viewer.uuid())).isPresent(); // still linked until confirmed

        fireClick(ConfirmRenderer.YES_SLOT, ClickType.LEFT); // confirm runs the same Unlink use case

        assertThat(store.findByPlayer(viewer.uuid())).isEmpty(); // the binding was removed through Unlink
    }

    // --- snapshots ---

    private Map<Integer, Snapshot> snapshotEngine() throws Exception {
        view().open(player, viewer);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /**
     * The frozen parity baseline: the read-only status line (a player head), the state-dependent action (a name tag
     * generate-code button when not linked, a barrier unlink button when linked), and the back button. Names and value
     * hints resolve to their catalog keys ({@code KeyMessages} echoes them), wrapped by the panel's value-lore key.
     */
    private Map<Integer, Snapshot> baseline(boolean linked) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(
                STATUS_SLOT,
                new Snapshot(
                        Material.PLAYER_HEAD, DiscordlinkMessageKey.GUI_STATUS.key(), "value=" + statusHint(linked)));
        if (linked) {
            out.put(
                    ACTION_SLOT,
                    new Snapshot(
                            Material.BARRIER,
                            DiscordlinkMessageKey.GUI_UNLINK.key(),
                            "value=" + DiscordlinkMessageKey.GUI_UNLINK_HINT.key()));
        } else {
            out.put(
                    ACTION_SLOT,
                    new Snapshot(
                            Material.NAME_TAG,
                            DiscordlinkMessageKey.GUI_LINK.key(),
                            "value=" + DiscordlinkMessageKey.GUI_LINK_HINT.key()));
        }
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, DiscordlinkMessageKey.GUI_BACK.key(), ""));
        return out;
    }

    private String statusHint(boolean linked) {
        return linked ? DiscordlinkMessageKey.GUI_STATUS_LINKED.key() : DiscordlinkMessageKey.GUI_STATUS_UNLINKED.key();
    }

    // --- harness ---

    private DiscordStatusView view() throws Exception {
        writeLayout();
        GuiLayouts layouts = new GuiLayouts(dir, NOOP);
        return new DiscordStatusView(
                guiText, scheduler, layouts, messages, beginLink, unlink, linkStatus, notifier, () -> true, engine());
    }

    /** A minimal editor-capable engine + listener so the migrated panel and its confirm open through the runtime. */
    private Menus engine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void writeLayout() throws Exception {
        Path file = dir.resolve("modules").resolve("discordlink").resolve("gui").resolve("discord-status.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                property-slots = [11, 15]
                back-slot = 22
                delete-slot = -1
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item), valueLoreOrEmpty(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static String valueLoreOrEmpty(ItemStack item) {
        List<Component> lore = item.lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name, String valueLore) {}

    // --- fakes ---

    /** A simple in-memory link store standing in for the DB-backed jOOQ store. */
    private static final class InMemoryStore implements DiscordLinkStore {
        private final Map<UUID, PendingLink> pending = new HashMap<>();
        private final Map<UUID, ConfirmedLink> confirmed = new HashMap<>();

        @Override
        public void savePending(PendingLink p) {
            pending.put(p.player(), p);
        }

        @Override
        public Optional<PendingLink> findPendingByCode(LinkCode code) {
            return pending.values().stream().filter(p -> p.code().equals(code)).findFirst();
        }

        @Override
        public void deletePending(UUID player) {
            pending.remove(player);
        }

        @Override
        public void confirm(ConfirmedLink link) {
            confirmed.put(link.player(), link);
            pending.remove(link.player());
        }

        @Override
        public Optional<ConfirmedLink> findByPlayer(UUID player) {
            return Optional.ofNullable(confirmed.get(player));
        }

        @Override
        public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
            return confirmed.values().stream()
                    .filter(c -> c.discordId().equals(discordId))
                    .findFirst();
        }

        @Override
        public boolean unlink(UUID player) {
            return confirmed.remove(player) != null;
        }
    }

    /** Special-cases the value-lore key to wrap the substituted value; every other key echoes itself. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(DiscordlinkMessageKey.GUI_VALUE_LORE.key())) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
                @Override
                public void info(String m, Object... a) {}

                @Override
                public void warn(String m, Object... a) {}

                @Override
                public void error(String m, Throwable t) {}

                @Override
                public void debug(String m, Object... a) {}
            };

    private static final class SyncScheduler implements Scheduler {
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

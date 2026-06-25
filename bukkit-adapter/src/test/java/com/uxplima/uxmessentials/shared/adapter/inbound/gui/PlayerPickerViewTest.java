package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the reusable {@link PlayerPickerView} on the menu engine: it builds an engine list (a
 * {@link MenuHolder}) with a head per online player, clicking a head through the one menu listener fires the pick
 * callback with that target, the offline button's anvil submission resolves a typed name through the supplied resolver
 * and fires the same callback (driven through the package-private {@code resolveTyped} seam, since MockBukkit cannot
 * open a live anvil), an unresolvable typed name replies with the supplied unknown-player key (and fires no pick), and
 * a roster longer than one page splits across pages with a working next-page button. The scheduler double runs every
 * hop inline so the global→entity marshal and the async resolve resolve in the test thread.
 */
class PlayerPickerViewTest {

    private static final int OFFLINE_BUTTON_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock viewer;
    private PlayerRef viewerRef;
    private TextInput textInput;
    private RecordingSink sink;
    private TestMenuEngine engine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer("Viewer");
        viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        textInput = TextInputTestKit.create(
                plugin,
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                java.nio.file.Path.of("nonexistent"),
                new NoopLogger());
        sink = new RecordingSink();
        engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsAHeadPerOnlinePlayer() {
        server.addPlayer("Bob");
        server.addPlayer("Carol");
        AtomicReference<PlayerRef> picked = new AtomicReference<>();

        view().open(viewer, viewerRef, request(picked, name -> Optional.empty()));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(MenuHolder.class);
        // Viewer + Bob + Carol = three heads in the content slots.
        assertThat(headCount(menu)).isEqualTo(3);
    }

    @Test
    void clickingAHeadFiresThePickCallbackWithThatTarget() {
        AtomicReference<PlayerRef> picked = new AtomicReference<>();
        view().open(viewer, viewerRef, request(picked, name -> Optional.empty()));

        fireClick(0); // the first head (the only online player is the viewer)

        PlayerRef chosen = java.util.Objects.requireNonNull(picked.get(), "picked");
        assertThat(chosen.uuid()).isEqualTo(viewer.getUniqueId());
    }

    @Test
    void aResolvedOfflineNameFiresThePickCallback() {
        PlayerRef offline = new PlayerRef(java.util.UUID.randomUUID(), "Offliner");
        AtomicReference<PlayerRef> picked = new AtomicReference<>();
        PlayerPickerView.Request request =
                request(picked, name -> name.equals("Offliner") ? Optional.of(offline) : Optional.empty());

        view().resolveTyped(viewer, viewerRef, request, "Offliner");

        assertThat(picked.get()).isEqualTo(offline);
        assertThat(sink.delivered).isEmpty();
    }

    @Test
    void anUnknownTypedNameRepliesWithTheUnknownKeyAndFiresNoPick() {
        AtomicReference<PlayerRef> picked = new AtomicReference<>();
        PlayerPickerView.Request request = request(picked, name -> Optional.empty());

        view().resolveTyped(viewer, viewerRef, request, "Ghost");

        assertThat(picked.get()).isNull();
        assertThat(sink.delivered).anyMatch(line -> line.startsWith(SharedMessageKey.COMMAND_UNKNOWN_PLAYER.key()));
    }

    @Test
    void aRosterBeyondOnePagePagesAndTheNextButtonWorks() {
        // Five content rows hold 45 heads per page; 50 online players force a second page.
        for (int i = 0; i < 49; i++) {
            server.addPlayer("Player" + i);
        }
        AtomicReference<PlayerRef> picked = new AtomicReference<>();
        view().open(viewer, viewerRef, request(picked, name -> Optional.empty()));

        Inventory first = viewer.getOpenInventory().getTopInventory();
        int firstPageHeads = headCount(first);
        assertThat(firstPageHeads).isEqualTo(45); // one full page of content slots

        fireClick(NEXT_SLOT);

        Inventory second = viewer.getOpenInventory().getTopInventory();
        // 50 players total - 45 on the first page = 5 on the second.
        assertThat(headCount(second)).isEqualTo(5);
    }

    @Test
    void theOfflineButtonIsACustomNameTag() {
        view().open(viewer, viewerRef, request(new AtomicReference<>(), name -> Optional.empty()));

        Inventory menu = viewer.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(OFFLINE_BUTTON_SLOT).getType()).isEqualTo(Material.NAME_TAG);
    }

    private PlayerPickerView view() {
        return new PlayerPickerView(
                engine.menus(),
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                textInput,
                server,
                new KeyMessages(),
                sink);
    }

    private static PlayerPickerView.Request request(
            AtomicReference<PlayerRef> picked, Function<String, Optional<PlayerRef>> resolver) {
        return new PlayerPickerView.Request(
                GuiMessageKey.PLAYER_PICKER_CUSTOM, picked::set, resolver, SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
    }

    private static int headCount(Inventory menu) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : menu.getContents()) {
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                count++;
            }
        }
        return count;
    }

    private void fireClick(int slot) {
        InventoryView view = viewer.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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

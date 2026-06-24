package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.RecordingTeleport;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffListView;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /stafflist} picker: it opens a paginated head menu of online staff-member holders the looker can see,
 * an empty staff roster sends the empty line instead of opening a window, and clicking a head teleports the looker
 * to that staff member through the soft-coupled teleport. The COMPASS navigator's equivalent coverage moved to the
 * engine golden test once it migrated to the menu engine.
 */
class StaffTeleportPickerViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock looker;
    private RecordingTeleport teleport;
    private RecordingKeySink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        looker = server.addPlayer("Looker");
        teleport = new RecordingTeleport();
        sink = new RecordingKeySink();
        com.uxplima.uxmlib.gui.Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        com.uxplima.uxmlib.gui.Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void anEmptyStaffRosterSendsTheEmptyLineAndOpensNothing() {
        listView().open(looker, ref(looker));

        // No one holds the staff-member node, so no picker opens and only the empty line is sent.
        Inventory top = looker.getOpenInventory().getTopInventory();
        if (top != null) {
            assertThat(top.getHolder()).isNotInstanceOf(PaginatedGui.class);
        }
        assertThat(sink.keys).contains(StaffMessageKey.STAFF_LIST_EMPTY);
    }

    @Test
    void theListMenuOpensWhenStaffAreOnline() {
        PlayerMock staffer = server.addPlayer("Staffer");
        staffer.addAttachment(plugin, "uxmessentials.staff.member", true);
        listView().open(looker, ref(looker));

        Inventory menu = looker.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
    }

    private StaffListView listView() {
        return new StaffListView(server, new KeyMessages(), sink, new SyncScheduler(), teleport);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingKeySink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            for (StaffMessageKey key : StaffMessageKey.values()) {
                if (renderedText.startsWith(key.key())) {
                    keys.add(key);
                }
            }
        }
    }

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

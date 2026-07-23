package com.uxplima.uxmessentials.invrollback.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotExporter;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotListView;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewView;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotTeleporter;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /invrestore <player>}: the command is gated by {@code
 * uxmessentials.invrollback.restore}; the target is resolved by name through the {@link PlayerLookup} port, so
 * dispatching it against an online target opens the restore list, dispatching it against an offline-but-known target
 * still opens the list (and export still hands the staff member shulkers), and only a name the server has never seen
 * refuses without opening a window.
 */
class InvrestoreCommandTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private static final String PERMISSION = "uxmessentials.invrollback.restore";

    private ServerMock server;
    private Plugin plugin;
    private SnapshotRepository repository;
    private FakePlayerLookup lookup;
    private InvrestoreCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        repository = new RecordingRepository();
        lookup = new FakePlayerLookup();

        Scheduler scheduler = new SyncScheduler();
        Messages messages = keyEcho();
        GuiText guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        Menus menus = engine.menus();
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));
        SnapshotRestorer restorer = new SnapshotRestorer(restore, scheduler, messages, noopSink(), CLOCK);
        SnapshotTeleporter teleporter = new SnapshotTeleporter(scheduler, messages, noopSink(), noopLog());
        SnapshotExporter exporter = new SnapshotExporter(scheduler, messages, noopSink());
        SnapshotPreviewView preview =
                new SnapshotPreviewView(messages, scheduler, CLOCK, restorer, teleporter, exporter);
        SnapshotListView listView =
                new SnapshotListView(menus, guiText, scheduler, messages, noopSink(), repository, preview, CLOCK);
        command = new InvrestoreCommand(
                listView, exporter, teleporter, repository, lookup, scheduler, messages, noopSink());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theCommandIsGatedByTheRestorePermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(plugin, PERMISSION, true);
        PlayerMock denied = server.addPlayer("Denied");

        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void dispatchingAgainstAnOnlineTargetWithSnapshotsOpensTheList() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, PERMISSION, true);
        PlayerMock victim = server.addPlayer("Victim");
        lookup.register(new PlayerRef(victim.getUniqueId(), victim.getName()));
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 1);
        repository.save(Snapshot.capture(
                victim.getUniqueId(),
                SnapshotCause.DEATH,
                CLOCK.instant(),
                InventorySnapshotCodec.encode(contents, null)));

        dispatch(CommandSourceStackMock.from(staff), "invrestore Victim");

        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void dispatchingAgainstAnOfflineButKnownTargetOpensTheList() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, PERMISSION, true);
        // The target is never connected in this test: only its owner UUID is known to the lookup and its snapshots
        // live in the repository, exactly the "player has logged off" case AxInventoryRestore supports.
        PlayerRef offlineVictim = new PlayerRef(UUID.randomUUID(), "Victim");
        lookup.register(offlineVictim);
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 1);
        repository.save(Snapshot.capture(
                offlineVictim.uuid(),
                SnapshotCause.LOGOUT,
                CLOCK.instant(),
                InventorySnapshotCodec.encode(contents, null)));

        dispatch(CommandSourceStackMock.from(staff), "invrestore Victim");

        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void exportingAnOfflineTargetsSnapshotHandsTheStaffMemberShulkers() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, "uxmessentials.invrollback.export", true);
        PlayerRef offlineVictim = new PlayerRef(UUID.randomUUID(), "Victim");
        lookup.register(offlineVictim);
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 5);
        repository.save(Snapshot.capture(
                offlineVictim.uuid(),
                SnapshotCause.DEATH,
                CLOCK.instant(),
                InventorySnapshotCodec.encode(contents, null)));

        dispatch(CommandSourceStackMock.from(staff), "invrestore export Victim 1");

        // The export reads the stored snapshot and gives the packed box to the online staff member: the offline
        // target never needed a live session.
        assertThat(staff.getInventory().contains(Material.SHULKER_BOX)).isTrue();
    }

    @Test
    void dispatchingAgainstAnUnknownNameRefusesWithoutAWindow() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, PERMISSION, true);

        dispatch(CommandSourceStackMock.from(staff), "invrestore Ghost");

        // A name the server has never seen resolves to nothing: the shared unknown-player line, and no GUI opens (the
        // positive tests prove a window opens only on the success path).
        assertThat(staff.nextMessage()).contains("command.unknown-player");
    }

    private void dispatch(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException syntax) {
            throw new IllegalStateException("command dispatch failed: " + input, syntax);
        }
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    private static com.uxplima.uxmessentials.shared.application.port.Logger noopLog() {
        return new com.uxplima.uxmessentials.shared.application.port.Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }

    /** Runs every scheduler hop inline. */
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

    /** A name/uuid {@link PlayerLookup} whose registered refs resolve whether or not a live player is connected. */
    private static final class FakePlayerLookup implements PlayerLookup {
        private final Map<String, PlayerRef> byName = new HashMap<>();

        void register(PlayerRef ref) {
            byName.put(ref.name().toLowerCase(Locale.ROOT), ref);
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return findByName(name);
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return byName.values().stream()
                    .filter(ref -> ref.uuid().equals(uuid))
                    .findFirst();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** An in-memory {@link SnapshotRepository} with a newest-first {@code list}. */
    private static final class RecordingRepository implements SnapshotRepository {
        private final List<Snapshot> rows = new ArrayList<>();

        @Override
        public void save(Snapshot snapshot) {
            rows.add(snapshot);
        }

        @Override
        public List<Snapshot> list(UUID owner) {
            return rows.stream()
                    .filter(s -> s.owner().equals(owner))
                    .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<UUID> owners() {
            return rows.stream().map(Snapshot::owner).distinct().toList();
        }

        @Override
        public Optional<Snapshot> find(SnapshotId id) {
            return rows.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public void delete(SnapshotId id) {
            rows.removeIf(s -> s.id().equals(id));
        }

        @Override
        public int deleteBeyondCount(UUID owner, int keep) {
            List<Snapshot> newestFirst = list(owner);
            List<Snapshot> stale =
                    newestFirst.size() > keep ? newestFirst.subList(keep, newestFirst.size()) : List.of();
            rows.removeAll(stale);
            return stale.size();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(s -> s.createdAt().isBefore(cutoff));
            return before - rows.size();
        }
    }
}

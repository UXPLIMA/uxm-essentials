package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link SnapshotListView} over a real menu engine: the {@code /invrestore} list renders one
 * icon per snapshot newest-first (regardless of the order the rows were saved), and clicking an entry opens the
 * read-only {@link SnapshotPreviewView} for that snapshot.
 */
class SnapshotListViewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private PlayerRef target;
    private SnapshotRepository repository;
    private SnapshotListView listView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        target = new PlayerRef(UUID.randomUUID(), "Victim");
        repository = new RecordingRepository();

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
        listView = new SnapshotListView(menus, guiText, scheduler, messages, noopSink(), repository, preview, CLOCK);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void listsTheTargetsSnapshotsNewestFirst() {
        // Saved out of chronological order; the list must still show newest first.
        repository.save(snapshot(SnapshotCause.DEATH, minusSeconds(20), Material.IRON_INGOT));
        repository.save(snapshot(SnapshotCause.LOGOUT, minusSeconds(30), Material.GOLD_INGOT));
        repository.save(snapshot(SnapshotCause.RESTORE, minusSeconds(10), Material.DIAMOND));

        listView.open(staffRef, target);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        // Icon material tracks the cause: RESTORE=CLOCK (newest), DEATH=SKELETON_SKULL, LOGOUT=ENDER_PEARL (oldest).
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.CLOCK);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.SKELETON_SKULL);
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.ENDER_PEARL);
    }

    @Test
    void clickingASnapshotOpensItsReadOnlyPreview() {
        repository.save(snapshot(SnapshotCause.DEATH, minusSeconds(10), Material.DIAMOND));

        listView.open(staffRef, target);
        fireClick(0);

        Inventory preview = staff.getOpenInventory().getTopInventory();
        assertThat(preview.getHolder()).isInstanceOf(SnapshotPreviewHolder.class);
        // The previewed snapshot's diamond is mirrored into the first slot; the restore button sits in the bottom row.
        assertThat(preview.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(preview.getItem(SnapshotPreviewView.RESTORE_SLOT).getType()).isEqualTo(Material.LIME_WOOL);
    }

    private void fireClick(int slot) {
        InventoryView view = staff.getOpenInventory();
        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    @Test
    void theIconLoreShowsALocationLineOnlyForSnapshotsThatRecordedOne() {
        // Newest carries a location; the older one predates location capture.
        Position where = new Position(new WorldRef(UUID.randomUUID(), "world"), 12.0, 64.0, -8.0, 0f, 0f);
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 1);
        repository.save(Snapshot.capture(
                target.uuid(),
                SnapshotCause.DEATH,
                minusSeconds(5),
                InventorySnapshotCodec.encode(contents, null, where)));
        repository.save(snapshot(SnapshotCause.LOGOUT, minusSeconds(50), Material.GOLD_INGOT));

        listView.open(staffRef, target);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        // The located icon carries exactly one extra lore line (the location) over the locationless one.
        int located = inv.getItem(0).lore().size();
        int locationless = inv.getItem(1).lore().size();
        assertThat(located).isEqualTo(locationless + 1);
    }

    private Snapshot snapshot(SnapshotCause cause, Instant createdAt, Material contentMaterial) {
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(contentMaterial, 1);
        return Snapshot.capture(target.uuid(), cause, createdAt, InventorySnapshotCodec.encode(contents, null));
    }

    private static Instant minusSeconds(int seconds) {
        return CLOCK.instant().minusSeconds(seconds);
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

package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.junit.jupiter.api.Test;

/**
 * A category menu's write leaves the click's thread, two quick edits of one category both land, and the operator sees
 * the category as stored afterwards, on their own thread.
 */
class WarpCategoryEditsTest {

    private static final PlayerRef ADMIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Admin");

    private final Lanes lanes = new Lanes();
    private final Categories categories = new Categories();
    private final WarpCategoryEdits edits = new WarpCategoryEdits(categories, lanes);
    private final Seen seen = new Seen();

    @Test
    void aWriteRunsOnTheAsyncLaneAndTheMenuReopensOnTheOperatorsThread() {
        edits.create(ADMIN, category("towns", "Towns"), seen);

        assertThat(categories.stored).isEmpty();
        assertThat(seen.lines).isEmpty();
        lanes.runAsync();
        assertThat(categories.stored).containsKey("towns");
        assertThat(seen.lines).isEmpty();
        lanes.runEntity();
        assertThat(seen.lines).containsExactly("show Towns");
    }

    @Test
    void twoQuickEditsOfOneCategoryBothLand() {
        categories.stored.put("towns", category("towns", "Towns"));

        edits.edit(ADMIN, "towns", stored -> stored.withDisplayName("Cities"), seen);
        edits.edit(ADMIN, "towns", stored -> stored.withSlot(4), seen);
        lanes.runAll();

        WarpCategory saved = categories.stored.get("towns");
        assertThat(saved.displayName()).isEqualTo("Cities");
        assertThat(saved.slot()).isEqualTo(4);
        assertThat(seen.lines).containsExactly("show Cities", "show Cities");
    }

    @Test
    void anEditOfACategoryDeletedMeanwhileDoesNotBringItBack() {
        categories.stored.put("towns", category("towns", "Towns"));

        edits.delete(ADMIN, "towns", seen);
        edits.edit(ADMIN, "towns", stored -> stored.withDisplayName("Cities"), seen);
        lanes.runAll();

        assertThat(categories.stored).isEmpty();
        assertThat(seen.lines).containsExactly("gone", "gone");
    }

    @Test
    void aFailedWriteIsToldAndTheCategoryReopensAsStored() {
        categories.stored.put("towns", category("towns", "Towns"));
        categories.failSaves = true;

        edits.edit(ADMIN, "towns", stored -> stored.withDisplayName("Cities"), seen);

        assertThatThrownBy(lanes::runAsync).hasMessage("database down");
        lanes.runEntity();
        assertThat(seen.lines).containsExactly("failed", "show Towns");
    }

    private static WarpCategory category(String id, String name) {
        return new WarpCategory(id, name, Optional.empty(), List.of(), 0, Optional.empty());
    }

    /** What the operator was shown, in order. */
    private static final class Seen implements WarpCategoryEdits.After {

        final List<String> lines = new ArrayList<>();

        @Override
        public void show(WarpCategory category) {
            lines.add("show " + category.displayName());
        }

        @Override
        public void gone() {
            lines.add("gone");
        }

        @Override
        public void failed() {
            lines.add("failed");
        }
    }

    /** The stored categories, in memory, with saves that can fail as a database that went down does. */
    private static final class Categories implements WarpCategoryRepository {

        final Map<String, WarpCategory> stored = new LinkedHashMap<>();
        boolean failSaves;

        @Override
        public Optional<WarpCategory> find(String id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<WarpCategory> all() {
            return List.copyOf(stored.values());
        }

        @Override
        public void save(WarpCategory category) {
            if (failSaves) {
                throw new IllegalStateException("database down");
            }
            stored.put(category.id(), category);
        }

        @Override
        public void delete(String id) {
            stored.remove(id);
        }
    }

    /** An async lane and a player's thread that hold their tasks until the test runs them. */
    private static final class Lanes implements Scheduler {

        private final Deque<Runnable> async = new ArrayDeque<>();
        private final Deque<Runnable> entity = new ArrayDeque<>();

        void runAsync() {
            while (!async.isEmpty()) {
                async.removeFirst().run();
            }
        }

        void runEntity() {
            while (!entity.isEmpty()) {
                entity.removeFirst().run();
            }
        }

        void runAll() {
            while (!async.isEmpty() || !entity.isEmpty()) {
                runAsync();
                runEntity();
            }
        }

        @Override
        public void async(Runnable task) {
            async.addLast(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entity.addLast(task);
        }
    }
}

package com.uxplima.uxmessentials.guard.application;

import static com.uxplima.uxmessentials.guard.GuardWorlds.WORLD;
import static com.uxplima.uxmessentials.guard.GuardWorlds.player;
import static com.uxplima.uxmessentials.guard.GuardWorlds.rule;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.guard.application.port.GuardSync;
import com.uxplima.uxmessentials.guard.application.port.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.Test;

/** The guard's use cases over in-memory ports: what an operator does, and what the players then meet. */
class GuardServiceTest {

    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final EditContext STAFF = new EditContext("Staff", false);
    private static final EditContext ADMIN = new EditContext("Admin", true);
    private static final RegionKey SPAWN = new RegionKey(WORLD, RegionId.of("spawn"));
    private static final Shape BOX = new Shape.Cuboid(Bounds.of(0, 0, 0, 10, 10, 10));

    private final InMemoryGuardStore store = new InMemoryGuardStore();
    private final List<RegionKey> announced = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** Runs everything at once on the calling thread. */
    private static final Scheduler INLINE = new Scheduler() {
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
    };

    private static final GuardWorlds ONE_WORLD = new GuardWorlds() {
        @Override
        public List<String> loaded() {
            return List.of(WORLD);
        }

        @Override
        public Optional<Height> height(String world) {
            return world.equals(WORLD) ? Optional.of(new Height(-64, 319)) : Optional.empty();
        }
    };

    /** Every key read falls back to its default, which is the shipped behaviour. */
    private static final ConfigStore DEFAULTS = new ConfigStore() {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    };

    private GuardService server(String id) {
        GuardSync sync = (scope, key) -> announced.add(key);
        GuardService service = new GuardService(
                store, sync, ONE_WORLD, INLINE, GuardConfig.from(DEFAULTS, id, warnings::add), warnings::add);
        var loaded = service.load();
        assertThat(loaded).isCompleted();
        return service;
    }

    private static <T> T done(CompletableFuture<T> future) {
        assertThat(future).isCompleted();
        return future.join();
    }

    private static Verdict breakAt(GuardService service, int x, int y, int z) {
        return service.snapshot().check(WORLD, x, y, z, ActionId.of("break"), player(STRANGER));
    }

    @Test
    void aRegionProtectsAsSoonAsItsRuleIsStored() {
        GuardService service = server("lobby");

        done(service.create(SPAWN, BOX, null, STAFF));
        Result<Region, GuardError> edited =
                done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        assertThat(edited.isOk()).isTrue();
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.DENY);
        assertThat(breakAt(service, 50, 5, 5)).isEqualTo(Verdict.UNSET);
    }

    @Test
    void aNameIsTakenOncePerWorld() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));

        Result<Region, GuardError> again = done(service.create(SPAWN, BOX, null, STAFF));
        Result<Region, GuardError> nowhere =
                done(service.create(new RegionKey("void", RegionId.of("spawn")), BOX, null, STAFF));

        assertThat(again.errorOrThrow()).isEqualTo(GuardError.ALREADY_EXISTS);
        assertThat(nowhere.errorOrThrow()).isEqualTo(GuardError.UNKNOWN_WORLD);
    }

    @Test
    void everyChangeIsRecordedAndTheLastOneCanBeUndone() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));
        done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        List<HistoryEntry> history = done(service.history(SPAWN, 0, 10));
        assertThat(history).extracting(HistoryEntry::change).containsExactly("rule", "create");

        done(service.undo(history.get(0).id(), STAFF));
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.UNSET);
        assertThat(done(service.history(SPAWN, 0, 10))).hasSize(3);
    }

    @Test
    void aRemovedRegionCanBeBroughtBack() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));
        done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        done(service.remove(SPAWN, STAFF));
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.UNSET);
        assertThat(service.region(SPAWN)).isEmpty();

        Result<Region, GuardError> restored = done(service.restore(SPAWN, STAFF));
        assertThat(restored.isOk()).isTrue();
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.DENY);
    }

    @Test
    void aLockedRegionChangesOnlyForThoseAllowedToEditLockedRegions() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, ADMIN));
        done(service.edit(SPAWN, "lock", r -> r.withLocked(true), ADMIN));

        Result<Region, GuardError> refused = done(service.edit(SPAWN, "priority", r -> r.withPriority(5), STAFF));
        Result<Region, GuardError> removal = done(service.remove(SPAWN, STAFF));
        Result<Region, GuardError> allowed = done(service.edit(SPAWN, "priority", r -> r.withPriority(5), ADMIN));

        assertThat(refused.errorOrThrow()).isEqualTo(GuardError.REGION_LOCKED);
        assertThat(removal.errorOrThrow()).isEqualTo(GuardError.REGION_LOCKED);
        assertThat(allowed.isOk()).isTrue();
    }

    @Test
    void aBackupPutsItsRegionsBackAndKeepsRegionsMadeSince() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));
        done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));
        List<Region> backup = service.all();
        RegionKey later = new RegionKey(WORLD, RegionId.of("later"));
        done(service.remove(SPAWN, STAFF));
        done(service.create(later, new Shape.Cuboid(Bounds.of(100, 0, 100, 110, 10, 110)), null, STAFF));

        GuardService.RestoreOutcome outcome = done(service.restoreBackup(backup, STAFF));

        assertThat(outcome).isEqualTo(new GuardService.RestoreOutcome(1, 0, 0));
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.DENY);
        assertThat(service.region(later)).isPresent();
        assertThat(done(service.history(SPAWN, 0, 10)))
                .extracting(HistoryEntry::change)
                .first()
                .isEqualTo("backup-restore");
    }

    @Test
    void aBackupLeavesALockedRegionToThoseAllowedToEditIt() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, ADMIN));
        List<Region> backup = service.all();
        done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), ADMIN));
        done(service.edit(SPAWN, "lock", r -> r.withLocked(true), ADMIN));

        GuardService.RestoreOutcome refused = done(service.restoreBackup(backup, STAFF));
        assertThat(refused).isEqualTo(new GuardService.RestoreOutcome(0, 0, 1));
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.DENY);

        GuardService.RestoreOutcome allowed = done(service.restoreBackup(backup, ADMIN));
        assertThat(allowed).isEqualTo(new GuardService.RestoreOutcome(1, 0, 0));
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.UNSET);
    }

    @Test
    void aStoreThatCannotBeReadLocksEveryWorldInsteadOfLeavingItOpen() {
        store.failLoad = true;
        GuardService service = server("lobby");

        assertThat(service.loadFailed()).isTrue();
        assertThat(breakAt(service, 500, 70, 500)).isEqualTo(Verdict.DENY);
        assertThat(done(service.create(SPAWN, BOX, null, ADMIN)).errorOrThrow()).isEqualTo(GuardError.WORLD_LOCKED);
        assertThat(warnings).isNotEmpty();

        store.failLoad = false;
        done(service.load());
        assertThat(service.loadFailed()).isFalse();
        assertThat(breakAt(service, 500, 70, 500)).isEqualTo(Verdict.UNSET);
    }

    @Test
    void aChangeThatCannotBeStoredDoesNotProtectHalfway() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));
        store.failWrite = true;

        Result<Region, GuardError> edited =
                done(service.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        assertThat(edited.isErr()).isTrue();
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.UNSET);
    }

    @Test
    void anotherServerSeesAnEditOnceItIsAnnounced() {
        GuardService lobby = server("lobby");
        GuardService hub = new GuardService(
                store,
                GuardSync.NONE,
                ONE_WORLD,
                INLINE,
                GuardConfig.from(DEFAULTS, "lobby", warnings::add),
                warnings::add);
        done(hub.load());
        done(lobby.create(SPAWN, BOX, null, STAFF));
        done(lobby.edit(SPAWN, "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        assertThat(announced).contains(SPAWN);
        assertThat(breakAt(hub, 5, 5, 5)).isEqualTo(Verdict.UNSET);
        hub.remoteChanged(GuardScope.server("lobby"), SPAWN);
        assertThat(breakAt(hub, 5, 5, 5)).isEqualTo(Verdict.DENY);
    }

    @Test
    void withoutAnnouncementsAServerCatchesUpByPolling() {
        GuardService lobby = server("lobby");
        store.writeFromElsewhere(
                Region.create(SPAWN, GuardScope.server("lobby"), BOX).withRule(rule("break", "everyone", Effect.DENY)));

        lobby.poll();

        assertThat(breakAt(lobby, 5, 5, 5)).isEqualTo(Verdict.DENY);
    }

    @Test
    void aServerIgnoresRegionsOfScopesItDoesNotLoad() {
        GuardService lobby = server("lobby");
        store.writeFromElsewhere(Region.create(SPAWN, GuardScope.server("survival"), BOX)
                .withRule(rule("break", "everyone", Effect.DENY)));

        lobby.poll();
        lobby.remoteChanged(GuardScope.server("survival"), SPAWN);

        assertThat(breakAt(lobby, 5, 5, 5)).isEqualTo(Verdict.UNSET);
    }

    @Test
    void oneChangeReachesEveryRegionWithATag() {
        GuardService service = server("lobby");
        RegionKey market = new RegionKey(WORLD, RegionId.of("market"));
        done(service.create(SPAWN, BOX, null, STAFF));
        done(service.create(market, new Shape.Cuboid(Bounds.of(100, 0, 0, 110, 10, 10)), null, STAFF));
        done(service.edit(SPAWN, "tag", r -> r.withTags(Set.of("safe")), STAFF));
        done(service.edit(market, "tag", r -> r.withTags(Set.of("safe")), STAFF));

        Result<Integer, GuardError> changed = done(
                service.editTagged("safe", "rule", r -> r.withRule(rule("break", "everyone", Effect.DENY)), STAFF));

        assertThat(changed.orElseThrow()).isEqualTo(2);
        assertThat(breakAt(service, 5, 5, 5)).isEqualTo(Verdict.DENY);
        assertThat(breakAt(service, 105, 5, 5)).isEqualTo(Verdict.DENY);
    }

    @Test
    void anImportSkipsNamesThatExistAndItsBatchCanBeRemovedAgain() {
        GuardService service = server("lobby");
        done(service.create(SPAWN, BOX, null, STAFF));
        Region clash = Region.create(SPAWN, GuardScope.server("lobby"), BOX).withImportBatch("wg-1");
        Region fresh = Region.create(
                        new RegionKey(WORLD, RegionId.of("mine")),
                        GuardScope.server("lobby"),
                        new Shape.Cuboid(Bounds.of(200, 0, 0, 210, 10, 10)))
                .withImportBatch("wg-1")
                .withRule(rule("break", "everyone", Effect.DENY));

        List<RegionKey> skipped = done(service.importRegions(List.of(clash, fresh), STAFF));
        assertThat(skipped).containsExactly(SPAWN);
        assertThat(breakAt(service, 205, 5, 5)).isEqualTo(Verdict.DENY);

        assertThat(done(service.removeBatch("wg-1", STAFF))).isEqualTo(1);
        assertThat(breakAt(service, 205, 5, 5)).isEqualTo(Verdict.UNSET);
        assertThat(service.region(SPAWN)).isPresent();
    }
}

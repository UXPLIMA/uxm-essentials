package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.TestSupport.CapturingNotifier;
import com.uxplima.uxmessentials.worlds.application.port.PendingRestoreRegistry;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.PendingRestore;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * {@code RestoreWorld} is two-phase. {@code request} refuses an unmanaged world, the protected default
 * world, and a backup id that is not on record; on a valid id it stages a pending restore and asks for
 * confirmation without touching the world. {@code confirm} refuses when nothing is staged and otherwise
 * hands the staged id off to the archive.
 */
class RestoreWorldTest {

    private static final WorldName WORLD = WorldName.of("creative");
    private static final BackupId ID = BackupId.of("snap-1");

    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final FakeWorldArchive archive = new FakeWorldArchive();
    private final FakePendingRestore pending = new FakePendingRestore();
    private final CapturingNotifier notifier = new CapturingNotifier();
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    private final RestoreWorld restore =
            new RestoreWorld(repository, engine, archive, pending, notifier.notifier(), TestSupport.inlineScheduler());

    private void register() {
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.defaultWorld = WorldName.of("world");
        archive.listing = List.of(new BackupRef(ID, Instant.EPOCH, 42L));
    }

    @Test
    void requestRejectsAnUnmanagedWorld() {
        var result = restore.request(who, WORLD, ID);
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_FOUND);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_NOT_FOUND);
        assertThat(pending.peek(who.uuid())).isEmpty();
    }

    @Test
    void requestRejectsTheProtectedDefaultWorld() {
        register();
        engine.defaultWorld = WORLD;
        var result = restore.request(who, WORLD, ID);
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.IS_PROTECTED);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_PROTECTED);
        assertThat(pending.peek(who.uuid())).isEmpty();
    }

    @Test
    void requestRejectsABackupIdThatIsNotOnRecord() {
        register();
        var result = restore.request(who, WORLD, BackupId.of("nope"));
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.BACKUP_NOT_FOUND);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_BACKUP_NOT_FOUND);
        assertThat(pending.peek(who.uuid())).isEmpty();
    }

    @Test
    void requestStagesAValidRestoreAndAsksForConfirmation() {
        register();
        var result = restore.request(who, WORLD, ID);
        assertThat(result.isOk()).isTrue();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_RESTORE_CONFIRM);
        assertThat(pending.peek(who.uuid()).orElseThrow()).isEqualTo(new PendingRestore(WORLD, ID, who.uuid()));
        assertThat(archive.restoreWorld).isNull();
    }

    @Test
    void confirmWithoutAStageReportsNothingPending() {
        var result = restore.confirm(who, WORLD);
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.RESTORE_NONE_PENDING);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_RESTORE_NONE_PENDING);
        assertThat(archive.restoreWorld).isNull();
    }

    @Test
    void confirmConsumesTheStageAndDelegatesToTheArchive() {
        register();
        restore.request(who, WORLD, ID);
        var result = restore.confirm(who, WORLD);
        assertThat(result.isOk()).isTrue();
        assertThat(archive.restoreInitiator).isEqualTo(who);
        assertThat(archive.restoreWorld).isEqualTo(WORLD);
        assertThat(archive.restoreId).isEqualTo(ID);
        assertThat(pending.peek(who.uuid())).isEmpty();
    }

    private static final class FakePendingRestore implements PendingRestoreRegistry {
        private final Map<UUID, PendingRestore> map = new LinkedHashMap<>();

        @Override
        public void stage(PendingRestore pending) {
            map.put(pending.requester(), pending);
        }

        @Override
        public Optional<PendingRestore> take(WorldName world, UUID requester) {
            PendingRestore p = map.get(requester);
            if (p != null && p.world().equals(world)) {
                map.remove(requester);
                return Optional.of(p);
            }
            return Optional.empty();
        }

        @Override
        public Optional<PendingRestore> peek(UUID requester) {
            return Optional.ofNullable(map.get(requester));
        }
    }
}

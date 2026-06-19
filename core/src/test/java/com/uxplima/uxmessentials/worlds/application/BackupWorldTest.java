package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.worlds.application.TestSupport.CapturingNotifier;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * {@code BackupWorld} refuses an unmanaged world without delegating to the archive, and otherwise
 * announces the snapshot has started and hands the initiator and world straight to the archive,
 * returning whatever the archive reports.
 */
class BackupWorldTest {

    private static final WorldName WORLD = WorldName.of("creative");

    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final FakeWorldArchive archive = new FakeWorldArchive();
    private final CapturingNotifier notifier = new CapturingNotifier();
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    private final BackupWorld backup =
            new BackupWorld(repository, archive, notifier.notifier(), TestSupport.inlineScheduler());

    private void register() {
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void anUnmanagedWorldIsRejectedWithoutDelegating() {
        var result = backup.backup(who, WORLD);
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_FOUND);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_NOT_FOUND);
        assertThat(archive.backupWorld).isNull();
    }

    @Test
    void aManagedWorldAnnouncesStartedAndDelegatesToTheArchive() {
        register();
        archive.backupResult = Result.ok(BackupId.of("snap-1"));

        var result = backup.backup(who, WORLD);

        assertThat(result.orElseThrow()).isEqualTo(BackupId.of("snap-1"));
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_BACKUP_STARTED);
        assertThat(archive.backupInitiator).isEqualTo(who);
        assertThat(archive.backupWorld).isEqualTo(WORLD);
    }

    @Test
    void theArchiveFailureIsPropagatedVerbatim() {
        register();
        archive.backupResult = Result.err(WorldError.BACKUP_FAILED);

        var result = backup.backup(who, WORLD);

        assertThat(result.errorOrThrow()).isEqualTo(WorldError.BACKUP_FAILED);
    }
}

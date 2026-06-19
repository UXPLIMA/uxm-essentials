package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/** {@code ListBackups} reads the archive's list for a managed world, and shows nothing for an unmanaged one. */
class ListBackupsTest {

    private static final WorldName WORLD = WorldName.of("creative");

    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final FakeWorldArchive archive = new FakeWorldArchive();
    private final ListBackups listBackups = new ListBackups(repository, archive);

    @Test
    void aManagedWorldShowsTheArchiveListing() {
        repository.save(ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        BackupRef ref = new BackupRef(BackupId.of("snap-1"), Instant.EPOCH, 42L);
        archive.listing = List.of(ref);

        assertThat(listBackups.list(WORLD)).containsExactly(ref);
    }

    @Test
    void anUnmanagedWorldShowsNoBackups() {
        archive.listing = List.of(new BackupRef(BackupId.of("snap-1"), Instant.EPOCH, 42L));

        assertThat(listBackups.list(WORLD)).isEmpty();
    }
}

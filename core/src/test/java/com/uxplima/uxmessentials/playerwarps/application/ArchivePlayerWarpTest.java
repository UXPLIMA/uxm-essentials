package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Removal is split: {@code archive} retires the warp recoverably (status → ARCHIVED, row kept, no event), while
 * {@code hardDelete} drops the row and publishes {@code PlayerWarpDeleted}. Both gate on DELETE, which a co-owner
 * and a manager lack.
 */
class ArchivePlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Events events;
    private PlayerWarpTestSupport.Sink sink;
    private ArchivePlayerWarp archive;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef manager;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        events = new PlayerWarpTestSupport.Events();
        sink = new PlayerWarpTestSupport.Sink();
        archive = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                events,
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        manager = PlayerWarpTestSupport.ref("Manager");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    @Test
    void archiveSetsTheStatusToArchivedWithoutDeletingTheRow() {
        Result<Unit, PlayerWarpError> result = archive.archive(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findByName(HUB)).isPresent();
        assertThat(repository.stored("hub").status()).isEqualTo(WarpStatus.ARCHIVED);
        assertThat(repository.deleted).isEmpty();
        assertThat(events.published).isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.archived"));
    }

    @Test
    void restoreReturnsAnArchivedWarpToActive() {
        archive.archive(owner, HUB);

        Result<Unit, PlayerWarpError> result = archive.restore(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.restored"));
    }

    @Test
    void hardDeleteDropsTheRowByIdAndPublishesTheDeletedEvent() {
        Result<Unit, PlayerWarpError> result = archive.hardDelete(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.deleted).containsExactly(warp.id().orElseThrow());
        assertThat(repository.findByName(HUB)).isEmpty();
        assertThat(events.published).hasSize(1);
        assertThat(events.published.get(0)).isInstanceOf(PlayerWarpDeleted.class);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.deleted"));
    }

    @Test
    void adminRestoreFlipsAnArchivedWarpToActiveByIdWithNoRoleGate() {
        archive.archive(owner, HUB);
        PlayerWarpId id = warp.id().orElseThrow();

        Result<Unit, PlayerWarpError> result = archive.adminRestore(id);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").status()).isEqualTo(WarpStatus.ACTIVE);
    }

    @Test
    void adminHardDeleteDropsTheRowByIdAndPublishesTheDeletedEventWithNoRoleGate() {
        PlayerWarpId id = warp.id().orElseThrow();

        Result<Unit, PlayerWarpError> result = archive.adminHardDelete(id);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.deleted).containsExactly(id);
        assertThat(repository.findByName(HUB)).isEmpty();
        assertThat(events.published).hasSize(1);
        assertThat(events.published.get(0)).isInstanceOf(PlayerWarpDeleted.class);
    }

    @Test
    void adminVerbsAgainstAStaleIdAreNotFound() {
        PlayerWarpId ghost = PlayerWarpId.of(9_999L);

        assertThat(archive.adminRestore(ghost).errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(archive.adminHardDelete(ghost).errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
        assertThat(repository.deleted).isEmpty();
    }

    @Test
    void aVetoedHardDeleteKeepsTheWarpAndPublishesNothing() {
        ArchivePlayerWarp refusing = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                events,
                proposal -> false,
                PlayerWarpTestSupport.CLOCK);

        Result<Unit, PlayerWarpError> result = refusing.hardDelete(owner, HUB);

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.VETOED);
        assertThat(repository.deleted).isEmpty();
        assertThat(repository.findByName(HUB)).isPresent();
        assertThat(events.published).isEmpty();
    }

    @Test
    void archivingIsNotSomethingAnotherPluginCanRefuse() {
        // The owner can restore it themselves, so there is nothing to protect: only the irreversible path asks.
        ArchivePlayerWarp refusing = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                events,
                proposal -> false,
                PlayerWarpTestSupport.CLOCK);

        assertThat(refusing.archive(owner, HUB).isOk()).isTrue();
        assertThat(repository.stored("hub").status()).isEqualTo(WarpStatus.ARCHIVED);
    }

    @Test
    void staffHardDeleteByIdIsNotRefusable() {
        // Staff clearing an abusive warp must not be blockable by whatever else happens to be installed.
        ArchivePlayerWarp refusing = new ArchivePlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                events,
                proposal -> false,
                PlayerWarpTestSupport.CLOCK);

        assertThat(refusing.adminHardDelete(warp.id().orElseThrow()).isOk()).isTrue();
        assertThat(repository.findByName(HUB)).isEmpty();
    }

    @Test
    void aCoOwnerMayNotArchiveOrHardDelete() {
        assertThat(archive.archive(coOwner, HUB).errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(archive.hardDelete(coOwner, HUB).errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(repository.stored("hub").status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(repository.deleted).isEmpty();
    }

    @Test
    void aManagerMayNotArchive() {
        assertThat(archive.archive(manager, HUB).errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
    }

    @Test
    void archivingAMissingWarpIsNotFound() {
        assertThat(archive.archive(owner, PlayerWarpName.of("ghost")).errorOrThrow())
                .isEqualTo(PlayerWarpError.NOT_FOUND);
    }
}

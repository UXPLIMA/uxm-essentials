package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Where the last page of {@link PlayerWarpRepository#archivedForRent} ended, in its sort order of archive deadline
 * then id, so the next page starts after it. Paging this way lets the hard-delete pass move past warps it has to
 * keep instead of reading the same oldest page every time.
 *
 * @param archiveAfter the rent archive deadline of the last warp read
 * @param id the id of the last warp read
 */
public record RentArchiveCursor(Instant archiveAfter, PlayerWarpId id) {

    public RentArchiveCursor {
        Objects.requireNonNull(archiveAfter, "archiveAfter");
        Objects.requireNonNull(id, "id");
    }
}

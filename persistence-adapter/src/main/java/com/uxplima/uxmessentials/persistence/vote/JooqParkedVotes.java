package com.uxplima.uxmessentials.persistence.vote;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VoteParked.VOTE_PARKED;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.VoteParkedRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.vote.application.port.ParkedVotes;
import com.uxplima.uxmessentials.vote.domain.ParkedVote;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link ParkedVotes} over the {@code vote_parked} table, one row per parked vote. A take reads the rows of
 * the joiner's names by their lower-cased form, keeps those the joiner may claim, and deletes each on its own,
 * returning only the votes whose delete removed a row, so two takes at once hand each vote to one of them.
 */
@NullMarked
public final class JooqParkedVotes extends JooqRepository implements ParkedVotes {

    public JooqParkedVotes(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void park(ParkedVote vote) {
        Objects.requireNonNull(vote, "vote");
        write(dsl -> dsl.insertInto(VOTE_PARKED)
                .set(VOTE_PARKED.ID, UUID.randomUUID().toString())
                .set(VOTE_PARKED.NAME, vote.name())
                .set(VOTE_PARKED.NAME_LOWER, lower(vote.name()))
                .set(VOTE_PARKED.SERVICE, vote.serviceName())
                .set(VOTE_PARKED.VOTED_AT, vote.at().toEpochMilli())
                .set(VOTE_PARKED.EXACT_CASE, (short) (vote.exactCase() ? 1 : 0))
                .execute());
    }

    @Override
    public List<ParkedVote> take(Set<String> names) {
        Objects.requireNonNull(names, "names");
        if (names.isEmpty()) {
            return List.of();
        }
        List<String> lowered =
                names.stream().map(JooqParkedVotes::lower).distinct().toList();
        List<VoteParkedRecord> rows = read(dsl -> dsl.selectFrom(VOTE_PARKED)
                .where(VOTE_PARKED.NAME_LOWER.in(lowered))
                .orderBy(VOTE_PARKED.VOTED_AT, VOTE_PARKED.ID)
                .fetch());
        List<ParkedVote> taken = new ArrayList<>();
        for (VoteParkedRecord row : rows) {
            ParkedVote vote = new ParkedVote(
                    row.getName(), row.getService(), Instant.ofEpochMilli(row.getVotedAt()), row.getExactCase() != 0);
            if (vote.exactCase() && !names.contains(vote.name())) {
                continue;
            }
            String id = row.getId();
            boolean removed = write(dsl ->
                    dsl.deleteFrom(VOTE_PARKED).where(VOTE_PARKED.ID.eq(id)).execute() == 1);
            if (removed) {
                taken.add(vote);
            }
        }
        return taken;
    }

    @Override
    public int forgetOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(VOTE_PARKED)
                .where(VOTE_PARKED.VOTED_AT.lt(cutoff.toEpochMilli()))
                .execute());
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

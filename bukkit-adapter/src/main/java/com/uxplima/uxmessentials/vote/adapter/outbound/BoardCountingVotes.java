package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.function.ObjLongConsumer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ForwardingVoteRepository;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jspecify.annotations.NullMarked;

/**
 * The vote repository with every vote a saved tally adds also counted on the votes board. A tally is saved for every
 * vote, the voter online or not, so the board counts what the vote module counts. Every other call goes straight
 * through.
 */
@NullMarked
public final class BoardCountingVotes extends ForwardingVoteRepository {

    private final ObjLongConsumer<UUID> board;

    /** @param board what counts votes on the board */
    public BoardCountingVotes(VoteRepository delegate, ObjLongConsumer<UUID> board) {
        super(delegate);
        this.board = Objects.requireNonNull(board, "board");
    }

    @Override
    public void saveTotals(PlayerRef player, VoteTally tally) {
        long before = delegate.totalsOf(player).alltime();
        delegate.saveTotals(player, tally);
        if (tally.alltime() > before) {
            board.accept(player.uuid(), tally.alltime() - before);
        }
    }
}

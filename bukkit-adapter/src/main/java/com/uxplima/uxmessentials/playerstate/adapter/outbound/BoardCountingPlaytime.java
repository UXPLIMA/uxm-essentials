package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ObjLongConsumer;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;

/**
 * The playtime ledger with every active second the sampler credits also counted on the playtime board. AFK seconds
 * are left off the board, so it ranks time played rather than time logged in. Every other call goes straight through.
 */
public final class BoardCountingPlaytime implements PlaytimeRepository {

    private final PlaytimeRepository ledger;
    private final ObjLongConsumer<UUID> board;

    /** @param board what counts an active second on the board */
    public BoardCountingPlaytime(PlaytimeRepository ledger, ObjLongConsumer<UUID> board) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.board = Objects.requireNonNull(board, "board");
    }

    @Override
    public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
        ledger.addSeconds(uuid, day, activeDelta, afkDelta);
        if (activeDelta > 0) {
            board.accept(uuid, activeDelta);
        }
    }

    @Override
    public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
        return ledger.summaryOf(uuid, today);
    }

    @Override
    public Map<UUID, Long> activeTotals() {
        return ledger.activeTotals();
    }

    @Override
    public void adjust(UUID uuid, long deltaSeconds) {
        ledger.adjust(uuid, deltaSeconds);
    }

    @Override
    public boolean imported(UUID uuid) {
        return ledger.imported(uuid);
    }

    @Override
    public void markImported(UUID uuid) {
        ledger.markImported(uuid);
    }

    @Override
    public void reset(UUID uuid) {
        ledger.reset(uuid);
    }

    @Override
    public void resetAll() {
        ledger.resetAll();
    }
}

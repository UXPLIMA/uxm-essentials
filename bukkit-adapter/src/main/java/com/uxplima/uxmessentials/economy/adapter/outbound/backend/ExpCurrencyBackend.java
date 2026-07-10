package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Paper experience as a currency, native to the server with no plugin behind it. Balance is the player's total
 * experience points, computed from level + within-level progress (the same vanilla curve the client shows) rather
 * than the unreliable {@code getTotalExperience()} counter, which does not account for spent levels. A credit or
 * debit re-derives the level and progress for the new total.
 *
 * <p>Experience lives on the online {@link Player}: this is the only shipped backend that cannot be written while
 * its owner is offline, so {@link #worksOffline()} is false and a write against an offline owner returns
 * {@link TransferError#PLAYER_OFFLINE}. Points are whole, so an amount is rounded to whole units at the boundary.
 */
public final class ExpCurrencyBackend implements CurrencyBackend {

    private final Server server;

    public ExpCurrencyBackend(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public String id() {
        return "exp";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean worksOffline() {
        return false;
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return Precision.INTEGRAL;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        Player player = server.getPlayer(owner.uuid());
        return player == null ? Money.zero(currency) : Money.of(currency, BigDecimal.valueOf(readTotal(player)));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Player player = server.getPlayer(owner.uuid());
        if (player == null) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        applyTotal(player, readTotal(player) + whole(amount));
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Player player = server.getPlayer(owner.uuid());
        if (player == null) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        int total = readTotal(player);
        int wanted = whole(amount);
        if (total < wanted) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        applyTotal(player, total - wanted);
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }

    private static int whole(Money amount) {
        return amount.amount().setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Total experience points for {@code player}, summed from the per-level costs up to the current level. */
    private static int readTotal(Player player) {
        int level = player.getLevel();
        int total = 0;
        for (int l = 0; l < level; l++) {
            total += expToNext(l);
        }
        return total + Math.round(player.getExp() * expToNext(level));
    }

    /** Re-derive the level and within-level progress for {@code total} points and write them back. */
    private static void applyTotal(Player player, int total) {
        int remaining = Math.max(0, total);
        int level = 0;
        // Bounded against absurd inputs; a real experience total never approaches this many levels.
        while (level < 1_000_000 && remaining >= expToNext(level)) {
            remaining -= expToNext(level);
            level++;
        }
        player.setLevel(level);
        player.setExp((float) remaining / expToNext(level));
    }

    /** The points needed to advance from {@code level} to the next, per the vanilla experience curve. */
    private static int expToNext(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }
}

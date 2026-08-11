package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.application.Prestige;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import org.jspecify.annotations.NullMarked;

/**
 * The rank use cases the published API runs.
 *
 * <p>The very instances behind {@code /rankup}, {@code /setrank} and {@code /prestige}, so a promotion a plugin
 * asks for charges the same cost, runs the same rank actions and raises the same event a player typing the
 * command would.
 *
 * <p>{@code prestige} is optional because the verb is: with {@code prestige.enabled} off the module never builds
 * the use case, and the published action then refuses rather than pretending a switched-off mechanic ran.
 *
 * @param rankup {@code /rankup}
 * @param setRank {@code /setrank}
 * @param prestige {@code /prestige}, empty when prestige is switched off
 */
@NullMarked
public record RanksApiWrites(Rankup rankup, SetRank setRank, Optional<Prestige> prestige) {

    public RanksApiWrites {
        Objects.requireNonNull(rankup, "rankup");
        Objects.requireNonNull(setRank, "setRank");
        Objects.requireNonNull(prestige, "prestige");
    }
}

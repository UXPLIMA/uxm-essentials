package com.uxplima.uxmessentials.shared.application.port;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The database record of the one-time kits a player has claimed and the cooldowns they are waiting out, shared by
 * every server on one database. It exists because the PDC copy lives in one server's world files: on a network with
 * a shared economy, each backend would grant the same one-time kit or cooldowned action again.
 *
 * <p>Every method does I/O and runs off the tick. The kit claim is the authoritative one: {@link #claimKit} inserts
 * against a unique key, so when two servers race for the same claim exactly one of them is told it won. Cooldowns are
 * last write wins.
 */
public interface PlayerClaimLedger {

    /** The kits {@code player} has claimed and every cooldown of theirs that is still running at {@code now}. */
    Claims load(UUID player, long now);

    /** The ids of the one-time kits {@code player} has claimed, read without changing anything. */
    Set<String> kits(UUID player);

    /**
     * Record that {@code player} claimed the one-time kit {@code kit}, only if nobody recorded it first. True only
     * for the call whose insert landed; every later call for the same player and kit answers false.
     */
    boolean claimKit(UUID player, String kit, long claimedAt);

    /** Forget {@code player}'s claim on {@code kit}, so it may be claimed again. */
    void releaseKit(UUID player, String kit);

    /** Forget every one-time kit claim {@code player} holds. */
    void releaseAllKits(UUID player);

    /** Store when {@code player}'s cooldown under {@code scope} ends, replacing whatever was stored before. */
    void saveCooldown(UUID player, String scope, long readyAt);

    /**
     * One player's claims as read.
     *
     * @param kits the ids of the one-time kits claimed
     * @param cooldowns when each running cooldown ends, in epoch milliseconds, by scope
     */
    record Claims(Set<String> kits, Map<String, Long> cooldowns) {

        public Claims {
            kits = Set.copyOf(Objects.requireNonNull(kits, "kits"));
            cooldowns = Map.copyOf(Objects.requireNonNull(cooldowns, "cooldowns"));
        }

        /** Nothing claimed and nothing waiting. */
        public static Claims none() {
            return new Claims(Set.of(), Map.of());
        }
    }
}

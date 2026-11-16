package com.uxplima.uxmessentials.persistence.ledger;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerCooldowns.PLAYER_COOLDOWNS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerKitClaims.PLAYER_KIT_CLAIMS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ {@link PlayerClaimLedger} over {@code player_kit_claims} and {@code player_cooldowns}.
 *
 * <p>A kit claim is an insert that does nothing on conflict with the {@code (player, kit)} primary key, so the
 * affected row count says whether this call took the claim: one for the first caller, zero for every later one, on
 * every backend (jOOQ renders {@code ON CONFLICT DO NOTHING} or {@code INSERT IGNORE}). A cooldown is an upsert.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Every method borrows a pooled connection and must run off the tick thread; the
 * primary keys serialise competing writers at the database, so no JVM lock is held here.
 */
@NullMarked
public final class JooqPlayerClaimLedger extends JooqRepository implements PlayerClaimLedger {

    public JooqPlayerClaimLedger(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Claims load(UUID player, long now) {
        Objects.requireNonNull(player, "player");
        String id = player.toString();
        return write(dsl -> {
            // A cooldown that has run out is never read again, so the read is where it is cleared away.
            dsl.deleteFrom(PLAYER_COOLDOWNS)
                    .where(PLAYER_COOLDOWNS.PLAYER.eq(id))
                    .and(PLAYER_COOLDOWNS.READY_AT.le(now))
                    .execute();
            Set<String> kits = new HashSet<>(dsl.select(PLAYER_KIT_CLAIMS.KIT)
                    .from(PLAYER_KIT_CLAIMS)
                    .where(PLAYER_KIT_CLAIMS.PLAYER.eq(id))
                    .fetch(PLAYER_KIT_CLAIMS.KIT));
            Map<String, Long> cooldowns = new HashMap<>();
            dsl.select(PLAYER_COOLDOWNS.SCOPE, PLAYER_COOLDOWNS.READY_AT)
                    .from(PLAYER_COOLDOWNS)
                    .where(PLAYER_COOLDOWNS.PLAYER.eq(id))
                    .fetch()
                    .forEach(row -> cooldowns.put(row.value1(), row.value2()));
            return new Claims(kits, cooldowns);
        });
    }

    @Override
    public Set<String> kits(UUID player) {
        Objects.requireNonNull(player, "player");
        return new HashSet<>(read(dsl -> dsl.select(PLAYER_KIT_CLAIMS.KIT)
                .from(PLAYER_KIT_CLAIMS)
                .where(PLAYER_KIT_CLAIMS.PLAYER.eq(player.toString()))
                .fetch(PLAYER_KIT_CLAIMS.KIT)));
    }

    @Override
    public boolean claimKit(UUID player, String kit, long claimedAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        return write(dsl -> dsl.insertInto(PLAYER_KIT_CLAIMS)
                        .set(PLAYER_KIT_CLAIMS.PLAYER, player.toString())
                        .set(PLAYER_KIT_CLAIMS.KIT, kit)
                        .set(PLAYER_KIT_CLAIMS.CLAIMED_AT, claimedAt)
                        .onConflictDoNothing()
                        .execute())
                == 1;
    }

    @Override
    public void releaseKit(UUID player, String kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        write(dsl -> dsl.deleteFrom(PLAYER_KIT_CLAIMS)
                .where(PLAYER_KIT_CLAIMS.PLAYER.eq(player.toString()))
                .and(PLAYER_KIT_CLAIMS.KIT.eq(kit))
                .execute());
    }

    @Override
    public void releaseAllKits(UUID player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> dsl.deleteFrom(PLAYER_KIT_CLAIMS)
                .where(PLAYER_KIT_CLAIMS.PLAYER.eq(player.toString()))
                .execute());
    }

    @Override
    public void saveCooldown(UUID player, String scope, long readyAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(scope, "scope");
        write(dsl -> dsl.insertInto(PLAYER_COOLDOWNS)
                .set(PLAYER_COOLDOWNS.PLAYER, player.toString())
                .set(PLAYER_COOLDOWNS.SCOPE, scope)
                .set(PLAYER_COOLDOWNS.READY_AT, readyAt)
                .onConflict(PLAYER_COOLDOWNS.PLAYER, PLAYER_COOLDOWNS.SCOPE)
                .doUpdate()
                .set(PLAYER_COOLDOWNS.READY_AT, readyAt)
                .execute());
    }
}

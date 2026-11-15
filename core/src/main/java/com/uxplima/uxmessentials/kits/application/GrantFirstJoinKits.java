package com.uxplima.uxmessentials.kits.application;

import java.util.Objects;
import java.util.concurrent.CompletionException;

import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Hands a player who is new to this server every kit marked {@code first-join}.
 *
 * <p>"New to this server" is all a backend can see, so on a network a starter kit would be handed out once per
 * backend. A one-time first-join kit therefore takes its claim from the {@code KitClaimStore} first, like a
 * {@code /kit} claim: on a network that is the shared database's unique key, and a player who already got the kit on
 * another backend gets nothing here. On a single server the claim answers at once and nothing changes.
 */
public final class GrantFirstJoinKits {

    private final KitRepository repository;
    private final KitGranter granter;
    private final KitAccess access;
    private final Logger log;

    public GrantFirstJoinKits(KitRepository repository, KitGranter granter, KitAccess access, Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.granter = Objects.requireNonNull(granter, "granter");
        this.access = Objects.requireNonNull(access, "access");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Hand {@code who}, joining this server for the first time, every first-join kit; on their own thread. */
    public void grant(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        for (KitDefinition kit : repository.all()) {
            if (!kit.firstJoin()) {
                continue;
            }
            KitDefinition granted = access.resolveVariant(who, kit);
            if (!kit.isOneTime()) {
                granter.grant(who, granted);
                continue;
            }
            var unused = access.acquireOneTime(who, kit).whenComplete((won, failure) -> {
                if (failure != null) {
                    // This server will not try again: the player has played here from now on.
                    log.warn(
                            "event=first_join_kit_not_given kit={} player={} reason={}",
                            kit.id().value(),
                            who.name(),
                            String.valueOf(unwrap(failure)));
                    return;
                }
                if (Boolean.TRUE.equals(won)) {
                    handOver(who, kit, granted);
                }
            });
        }
    }

    /**
     * Give a kit whose claim this server won. A grant that fails gives the claim back, so the kit is not lost on every
     * server; once the items are handed over the claim stays.
     */
    private void handOver(PlayerRef who, KitDefinition kit, KitDefinition granted) {
        try {
            granter.grant(who, granted);
        } catch (RuntimeException failure) {
            access.releaseOneTime(who, kit);
            log.error("could not hand the first-join kit " + kit.id().value() + " to " + who.name(), failure);
            return;
        }
        try {
            access.recordClaim(who, kit);
        } catch (RuntimeException failure) {
            log.error("could not record the first-join kit " + kit.id().value() + " for " + who.name(), failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure.getCause();
        return failure instanceof CompletionException && cause != null ? cause : failure;
    }
}

package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /lockdown [on|off]}: flip whether the server refuses every login except holders of the bypass
 * permission. The flag is the durable {@link ModerationRepository} row (it survives restart, the hard
 * moderation invariant), so a server locked down before a crash stays locked down on the next boot until an
 * operator lifts it. The login refusal itself lives in {@link LoginEnforcement}; this use case only flips the
 * flag, tells the actor, and announces the change to the staff broadcast audience the way the other server-wide
 * moderation actions do.
 */
public final class Lockdown {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final SanctionBroadcast broadcast;
    private final ModerationAudit audit;

    public Lockdown(
            ModerationRepository repository, Notifier notifier, SanctionBroadcast broadcast, ModerationAudit audit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /** Set the server lockdown flag, confirm to {@code actor} and announce the change to the broadcast audience. */
    public void setLockdown(PlayerRef actor, boolean enabled) {
        Objects.requireNonNull(actor, "actor");
        repository.setLockedDown(enabled);
        ModerationMessageKey publicKey =
                enabled ? ModerationMessageKey.MOD_LOCKDOWN_ENABLED : ModerationMessageKey.MOD_LOCKDOWN_DISABLED;
        ModerationMessageKey selfKey = enabled
                ? ModerationMessageKey.MOD_LOCKDOWN_ENABLED_SELF
                : ModerationMessageKey.MOD_LOCKDOWN_DISABLED_SELF;
        // The actor gets a distinct personal confirmation; the broadcast carries the public line to the
        // audience. An actor who also holds the broadcast-receive node would otherwise see the one public line
        // twice, which is why the two keys differ (the rest of the moderation context already works this way).
        notifier.send(actor, selfKey);
        broadcast.announce(publicKey, Map.of("actor", actor.name()));
        audit.lockdown(actor.uuid(), enabled);
    }

    /** Whether the server is currently locked down. */
    public boolean isLockedDown() {
        return repository.isLockedDown();
    }
}

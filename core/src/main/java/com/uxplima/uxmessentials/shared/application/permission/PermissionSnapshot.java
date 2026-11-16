package com.uxplima.uxmessentials.shared.application.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * Permission answers read on the server thread and kept for work that runs elsewhere: only the questions asked
 * before the work left, each with the answer it had then. A question nobody asked in advance has no answer here,
 * and the reader decides what that means.
 */
public final class PermissionSnapshot {

    private final Map<Held, Boolean> held;
    private final Map<Quota, QuotaResult> quotas;

    private PermissionSnapshot(Map<Held, Boolean> held, Map<Quota, QuotaResult> quotas) {
        this.held = Map.copyOf(held);
        this.quotas = Map.copyOf(quotas);
    }

    /** Start a snapshot whose answers are read from {@code live}, now, on the calling thread. */
    public static Builder from(Permissions live) {
        return new Builder(live);
    }

    /** What {@link Permissions#has} answered for this question when the snapshot was taken, if it was asked. */
    public Optional<Boolean> has(PlayerRef who, String node) {
        return Optional.ofNullable(held.get(new Held(who.uuid(), node)));
    }

    /** What {@link Permissions#resolveQuota} answered for this question when the snapshot was taken, if asked. */
    public Optional<QuotaResult> quota(
            PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        return Optional.ofNullable(quotas.get(new Quota(who.uuid(), family, world, configDefault)));
    }

    /** Asks {@code live} each question as it is added, so build it on the thread the answers are safe to read on. */
    public static final class Builder {

        private final Permissions live;
        private final Map<Held, Boolean> held = new HashMap<>();
        private final Map<Quota, QuotaResult> quotas = new HashMap<>();

        private Builder(Permissions live) {
            this.live = Objects.requireNonNull(live, "live");
        }

        /** Keep whether {@code who} holds {@code node}. */
        public Builder has(PlayerRef who, String node) {
            held.put(new Held(who.uuid(), node), live.has(who, node));
            return this;
        }

        /** Keep {@code held} as whether {@code who} holds {@code node}, an answer read elsewhere. */
        public Builder answered(PlayerRef who, String node, boolean held) {
            this.held.put(new Held(who.uuid(), node), held);
            return this;
        }

        /** Keep the quota {@code who} resolves to for {@code family}. */
        public Builder quota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            quotas.put(
                    new Quota(who.uuid(), family, world, configDefault),
                    live.resolveQuota(who, family, world, configDefault));
            return this;
        }

        public PermissionSnapshot build() {
            return new PermissionSnapshot(held, quotas);
        }
    }

    private record Held(UUID who, String node) {}

    private record Quota(
            UUID who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {}
}

package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for per-player cooldown gates. One cooldown is a "ready-at" timestamp stamped per
 * holder; the adapter keeps the transient stamp in PDC under a pre-created key. The quota (how long a
 * cooldown lasts, whether it can be bypassed) is resolved from numbered permission nodes via the
 * {@link Permissions} port, using the {@code min}-reducer: the lowest matching
 * {@code uxmessentials.<feature>.cooldown.<seconds>} wins, {@code 0} means no wait, and
 * {@code uxmessentials.<feature>.cooldown.bypass} skips the gate entirely.
 *
 * <p>Two keying styles share these mechanics. A {@link CooldownKind} keys a feature's tiered cooldown
 * (teleport, warp, kit), carrying the feature segment, the config default, and — for teleport — the
 * {@link CooldownStartPhase} that decides when the clock starts. The {@link #checkLabel}/
 * {@link #stampLabel} pair keys the generic per-command cooldown by an operator-chosen command label
 * or rule id, gated by {@code uxmessentials.cooldown.bypass.<label>} with the same min-reducer
 * semantics.
 */
public interface Cooldowns {

    /** Gate {@code who} for {@code kind}; ok when ready, else the remaining {@link Duration}. */
    Result<Unit, Duration> check(PlayerRef who, CooldownKind kind);

    /** Start the cooldown clock for {@code who} on {@code kind}, sized by the resolved quota. */
    void stamp(PlayerRef who, CooldownKind kind);

    /** Gate {@code who} for the generic per-command cooldown keyed by {@code label}. */
    Result<Unit, Duration> checkLabel(PlayerRef who, String label);

    /** Start the generic per-command cooldown clock for {@code who} keyed by {@code label}. */
    void stampLabel(PlayerRef who, String label);

    /**
     * When a teleport cooldown begins, configured per {@code teleport.conf} (default
     * {@link #TELEPORT}). Choosing {@link #TELEPORT} or {@link #ACCEPT} means a denied, expired, or
     * self-cancelled request never burns the requester's cooldown — a subtlety most plugins get
     * wrong. The same enum applies to {@code /warp}, {@code /home}, {@code /rtp}, and any cooldowned
     * teleport.
     */
    enum CooldownStartPhase {
        /** The clock starts when {@code /tpa} is issued. */
        REQUEST,
        /** The clock starts when the target accepts. */
        ACCEPT,
        /** The clock starts only when the player actually arrives — the safe default. */
        TELEPORT
    }

    /**
     * A tiered cooldown's identity: the feature segment used to build the permission nodes, the
     * config-default duration in seconds when the player holds no matching tier node, and the
     * {@link CooldownStartPhase} that decides when the clock starts.
     *
     * @param feature the node segment, e.g. {@code tp} → {@code uxmessentials.tp.cooldown.<seconds>}
     * @param defaultSeconds the config fallback in seconds when no tier node matches
     * @param startPhase when the cooldown clock starts for this kind
     */
    record CooldownKind(String feature, long defaultSeconds, CooldownStartPhase startPhase) {

        public CooldownKind {
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("feature must be non-blank");
            }
            if (defaultSeconds < 0) {
                throw new IllegalArgumentException("defaultSeconds must be >= 0: " + defaultSeconds);
            }
            Objects.requireNonNull(startPhase, "startPhase");
        }

        /** A cooldown that starts on arrival — the canonical teleport default. */
        public static CooldownKind onTeleport(String feature, long defaultSeconds) {
            return new CooldownKind(feature, defaultSeconds, CooldownStartPhase.TELEPORT);
        }

        /** The permission node prefix this kind resolves its tier against. */
        public String cooldownNode() {
            return "uxmessentials." + feature + ".cooldown";
        }

        /** The bypass node that skips the gate entirely. */
        public String bypassNode() {
            return cooldownNode() + ".bypass";
        }

        /** The default duration as a {@link Duration}. */
        public Duration defaultDuration() {
            return Duration.ofSeconds(defaultSeconds);
        }

        /** A copy of this kind with a different start phase, for operator-driven reconfiguration. */
        public CooldownKind withStartPhase(@Nullable CooldownStartPhase phase) {
            return new CooldownKind(feature, defaultSeconds, phase == null ? startPhase : phase);
        }
    }
}

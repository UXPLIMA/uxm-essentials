package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for per-player cooldown gates. One cooldown is a "ready-at" timestamp stamped per
 * holder; the adapter keeps the transient stamp in PDC under a pre-created key. How long a cooldown
 * lasts is resolved by {@link CooldownTiers} from the numbered
 * {@code uxmessentials.<feature>.cooldown.<seconds>} nodes the player holds: a held tier sets the wait,
 * the shortest one held wins, {@code 0} means no wait, and the configured wait applies only when the
 * player holds no tier. {@code uxmessentials.bypass.cooldown.<feature>} skips the gate entirely.
 *
 * <p>Two keying styles share these mechanics. A {@link CooldownKind} keys a feature's tiered cooldown
 * (teleport, warp, kit), carrying the feature segment, the config default, the {@link TierRule}, and,
 * for teleport, the {@link CooldownStartPhase} that decides when the clock starts. The {@link #checkLabel}/
 * {@link #stampLabel} pair keys the generic per-command cooldown by an operator-chosen command label
 * or rule id, sized by {@code uxmessentials.cooldown.<label>.<seconds>} with the same tier rule
 * and skipped by {@code uxmessentials.bypass.cooldown.label.<label>}.
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
     * Start the generic per-command cooldown clock keyed by {@code label}, using {@code configDefault} as the wait
     * when the player holds no matching {@code uxmessentials.cooldown.<label>.<seconds>} node. A custom command
     * declares its own cooldown in its file, and this is how that declared value reaches the stamp; the
     * label-only form keeps meaning "whatever the permission nodes say, else no wait".
     */
    default void stampLabel(PlayerRef who, String label, Duration configDefault) {
        stampLabel(who, label);
    }

    /**
     * When a teleport cooldown begins, configured per {@code teleport.conf} (default
     * {@link #TELEPORT}). Choosing {@link #TELEPORT} or {@link #ACCEPT} means a denied, expired, or
     * self-cancelled request never burns the requester's cooldown. A subtlety most plugins get
     * wrong. The same enum applies to {@code /warp}, {@code /home}, {@code /rtp}, and any cooldowned
     * teleport.
     */
    enum CooldownStartPhase {
        /** The clock starts when {@code /tpa} is issued. */
        REQUEST,
        /** The clock starts when the target accepts. */
        ACCEPT,
        /** The clock starts only when the player actually arrives, the safe default. */
        TELEPORT
    }

    /**
     * How a held tier combines with the wait a {@link CooldownKind} was configured with.
     *
     * <p>Where one tier node sizes one configured wait, the tier replaces it, so an operator can grant a longer
     * wait as well as a shorter one. Where one tier node covers several waits configured apart (each kit's own
     * cooldown, a teleport verb's or a warp's own cooldown), replacing them would flatten them all to the tier, and
     * a tier meant as a perk would make its holder wait longer on everything configured shorter. There the tier
     * only caps the configured wait.
     */
    enum TierRule {
        /** A held tier is the wait; the configured wait applies only when no tier is held. */
        SET,
        /** A held tier can lower the configured wait but never raise it. */
        CAP
    }

    /**
     * A tiered cooldown's identity: the feature segment used to build the permission nodes, the
     * {@code stampScope} the per-holder stamp is keyed under, the config-default duration in seconds
     * when the player holds no matching tier node, and the {@link CooldownStartPhase} that decides when
     * the clock starts.
     *
     * <p>The {@code feature} and {@code stampScope} are separate so a family of related cooldowns can
     * share one tier node space while each keeps its own independent stamp. {@code /kit} uses this: every
     * kit resolves its wait against the shared {@code uxmessentials.kit.cooldown.<seconds>} tier
     * ({@code feature = "kit"}), but each kit stamps under its own per-id scope ({@code stampScope =
     * "kit." + id}) so claiming one kit does not start another's cooldown. The teleport and warp kinds set
     * {@code stampScope} equal to {@code feature}, one tier, one stamp. A kind whose tier spans several stamps,
     * each with its own configured wait, caps with its tier ({@link TierRule#CAP}); a single-stamp kind lets its
     * tier set the wait ({@link TierRule#SET}).
     *
     * @param feature the node segment, e.g. {@code tp} → {@code uxmessentials.tp.cooldown.<seconds>}
     * @param stampScope the per-holder stamp key; defaults to {@code feature} for a single-stamp kind
     * @param defaultSeconds the config fallback in seconds when no tier node matches
     * @param startPhase when the cooldown clock starts for this kind
     * @param tierRule whether a held tier sets the wait or only caps the configured one
     */
    record CooldownKind(
            String feature, String stampScope, long defaultSeconds, CooldownStartPhase startPhase, TierRule tierRule) {

        public CooldownKind {
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("feature must be non-blank");
            }
            if (stampScope == null || stampScope.isBlank()) {
                throw new IllegalArgumentException("stampScope must be non-blank");
            }
            if (defaultSeconds < 0) {
                throw new IllegalArgumentException("defaultSeconds must be >= 0: " + defaultSeconds);
            }
            Objects.requireNonNull(startPhase, "startPhase");
            Objects.requireNonNull(tierRule, "tierRule");
        }

        /** A kind whose stamp is keyed by its own feature, the single-tier, single-stamp form: a held tier sets it. */
        public CooldownKind(String feature, long defaultSeconds, CooldownStartPhase startPhase) {
            this(feature, feature, defaultSeconds, startPhase, TierRule.SET);
        }

        /** A cooldown that starts on arrival, the canonical teleport default. */
        public static CooldownKind onTeleport(String feature, long defaultSeconds) {
            return new CooldownKind(feature, defaultSeconds, CooldownStartPhase.TELEPORT);
        }

        /**
         * A cooldown sharing the {@code feature} tier node space but stamped under a distinct
         * {@code stampScope}, so related instances rate-limit independently. {@code /kit} keys each kit
         * here: {@code scoped("kit", "kit." + id, seconds, TELEPORT)}. The shared tier caps each scope's own
         * wait ({@link TierRule#CAP}), so it never lengthens a scope configured shorter.
         */
        public static CooldownKind scoped(
                String feature, String stampScope, long defaultSeconds, CooldownStartPhase startPhase) {
            return new CooldownKind(feature, stampScope, defaultSeconds, startPhase, TierRule.CAP);
        }

        /** The permission node prefix this kind resolves its tier against. */
        public String cooldownNode() {
            return SharedPermissions.featureCooldown(feature);
        }

        /** The bypass node that skips the gate entirely. */
        public String bypassNode() {
            return SharedPermissions.featureCooldownBypass(feature);
        }

        /** The default duration as a {@link Duration}. */
        public Duration defaultDuration() {
            return Duration.ofSeconds(defaultSeconds);
        }

        /** A copy of this kind with a different start phase, for operator-driven reconfiguration. */
        public CooldownKind withStartPhase(@Nullable CooldownStartPhase phase) {
            return new CooldownKind(feature, stampScope, defaultSeconds, phase == null ? startPhase : phase, tierRule);
        }
    }
}

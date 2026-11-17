package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The sanctions that follow a player into the other spellings of their name on an offline-mode server, where the uuid
 * is derived from the exact spelling: a banned, muted or jailed {@code Steve} who logs in as {@code STEVE} arrives as
 * another account with no sanction of its own.
 *
 * <p>A sanction reaches another spelling according to {@link Reach}. By default it reaches only an account first seen
 * after the sanction was issued, the look-alike made to get around it; an account that was already playing under its
 * own spelling is taken to be somebody else. {@link Reach#ALL} lets it reach every spelling.
 *
 * <p>The accounts under a name come from {@link NameHolders}, which answers nothing while names are not
 * case-sensitive, so on a server with authenticated logins nothing here reads anything more.
 */
public final class CaseVariantSanctions {

    /** Which accounts under another spelling a sanction reaches: the {@code case-variant-sanctions} setting. */
    public enum Reach {
        /** An account first seen after the sanction was issued. */
        NEWER,
        /** Every account under another spelling, however long it has played. */
        ALL;

        /** The reach {@code raw} names, or {@link #NEWER} with a warning for anything else. */
        public static Reach parse(String raw, Logger log) {
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(log, "log");
            String wanted = raw.strip().toUpperCase(Locale.ROOT);
            for (Reach reach : values()) {
                if (reach.name().equals(wanted)) {
                    return reach;
                }
            }
            log.warn("event=case_variant_sanctions_unknown value={} using=newer expected=newer|all", raw);
            return NEWER;
        }
    }

    private final ModerationRepository repository;
    private final NameHolders holders;
    private final Reach reach;

    public CaseVariantSanctions(ModerationRepository repository, NameHolders holders, Reach reach) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.reach = Objects.requireNonNull(reach, "reach");
    }

    /** Nothing follows anybody: the answer for a server whose logins are authenticated. */
    public static CaseVariantSanctions none(ModerationRepository repository) {
        return new CaseVariantSanctions(repository, NameHolders.NONE, Reach.NEWER);
    }

    /**
     * What the login of {@code who} inherits from the other spellings of its name: the first active ban, mute and jail
     * among them that reaches it. Reads storage, a fixed number of queries however many spellings there are, so call
     * it off the tick.
     */
    public Inherited atLogin(PlayerRef who, Instant now) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(now, "now");
        List<PlayerRef> underName = holders.underName(who.name());
        boolean known = underName.stream().anyMatch(holder -> holder.uuid().equals(who.uuid()));
        List<PlayerRef> variants = otherSpellings(who, underName);
        if (variants.isEmpty()) {
            return new Inherited(variants, known, TempbanState.none(), MuteState.none(), JailState.none());
        }
        Optional<Instant> firstSeen = firstSeen(who);
        Map<java.util.UUID, TempbanState> bans = repository.loadTempbans(variants);
        Map<java.util.UUID, MuteState> mutes = repository.loadMutes(variants);
        Map<java.util.UUID, JailState> jails = repository.loadJails(variants);
        TempbanState ban = TempbanState.none();
        MuteState mute = MuteState.none();
        JailState jail = JailState.none();
        for (PlayerRef variant : variants) {
            if (!ban.isActiveAt(now)
                    && bans.get(variant.uuid()) instanceof TempbanState.Active active
                    && active.isActiveAt(now)
                    && reaches(firstSeen, active.issuedAt())) {
                ban = active;
            }
            MuteState variantMute = mutes.getOrDefault(variant.uuid(), MuteState.none());
            if (!mute.isActiveAt(now) && variantMute.isActiveAt(now) && reaches(firstSeen, issuedAt(variantMute))) {
                mute = variantMute;
            }
            if (!jail.isActiveAt(now)
                    && jails.get(variant.uuid()) instanceof JailState.Active active
                    && active.isActiveAt(now)
                    && reaches(firstSeen, active.issuedAt())) {
                jail = active;
            }
        }
        return new Inherited(variants, known, ban, mute, jail);
    }

    /**
     * The accounts under another spelling of {@code banned}'s name that a ban issued at {@code issuedAt} reaches, so
     * the ones connected can be removed as it lands. Reads storage, so call it off the tick.
     */
    public List<PlayerRef> reachedBy(PlayerRef banned, Instant issuedAt) {
        Objects.requireNonNull(banned, "banned");
        Objects.requireNonNull(issuedAt, "issuedAt");
        List<PlayerRef> variants = otherSpellings(banned, holders.underName(banned.name()));
        if (reach == Reach.ALL || variants.isEmpty()) {
            return variants;
        }
        // The holders are capped and a ban is rare, so one first-seen read per spelling is a bounded cost.
        List<PlayerRef> reached = new ArrayList<>();
        for (PlayerRef variant : variants) {
            if (reaches(firstSeen(variant), issuedAt)) {
                reached.add(variant);
            }
        }
        return reached;
    }

    private Optional<Instant> firstSeen(PlayerRef who) {
        return reach == Reach.ALL ? Optional.empty() : repository.seen(who).map(SeenRecord::firstSeen);
    }

    /** An account never seen is new; one seen before the sanction was issued was already somebody else. */
    private boolean reaches(Optional<Instant> firstSeen, Instant issuedAt) {
        return reach == Reach.ALL
                || firstSeen.map(first -> first.isAfter(issuedAt)).orElse(true);
    }

    private static List<PlayerRef> otherSpellings(PlayerRef who, List<PlayerRef> underName) {
        return underName.stream()
                .filter(holder ->
                        !holder.uuid().equals(who.uuid()) && !holder.name().equals(who.name()))
                .toList();
    }

    private static Instant issuedAt(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuedAt();
            case MuteState.Timed timed -> timed.issuedAt();
            case MuteState.None none -> Instant.MAX;
        };
    }

    /**
     * What one login inherits: the accounts under other spellings of its name, whether this account is among the
     * holders already, and the ban, mute and jail that follow it from them ({@code none} where nothing does).
     */
    public record Inherited(List<PlayerRef> variants, boolean known, TempbanState ban, MuteState mute, JailState jail) {

        public Inherited {
            variants = List.copyOf(variants);
            Objects.requireNonNull(ban, "ban");
            Objects.requireNonNull(mute, "mute");
            Objects.requireNonNull(jail, "jail");
        }

        /** Nothing inherited and no other spelling: the answer when names are not case-sensitive. */
        public static Inherited nothing() {
            return new Inherited(List.of(), false, TempbanState.none(), MuteState.none(), JailState.none());
        }
    }
}

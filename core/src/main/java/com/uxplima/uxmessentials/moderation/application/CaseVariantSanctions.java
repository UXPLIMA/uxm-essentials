package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
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
 * <p>Only an original follows a player. A mute or a jail an account holds as a copy ({@link CaseCopies}) is never a
 * source, so it is not copied on and never comes back to the account it came from, and a sanction already copied onto
 * the joining account once is not copied again, so a copy staff lifted on its own stays lifted.
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
    private final CaseCopies copies;

    public CaseVariantSanctions(ModerationRepository repository, NameHolders holders, Reach reach) {
        this(repository, holders, reach, CaseCopies.NONE);
    }

    public CaseVariantSanctions(ModerationRepository repository, NameHolders holders, Reach reach, CaseCopies copies) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.reach = Objects.requireNonNull(reach, "reach");
        this.copies = Objects.requireNonNull(copies, "copies");
    }

    /** Nothing follows anybody: the answer for a server whose logins are authenticated. */
    public static CaseVariantSanctions none(ModerationRepository repository) {
        return new CaseVariantSanctions(repository, NameHolders.NONE, Reach.NEWER);
    }

    /**
     * What the login of {@code who} inherits from the other spellings of its name: the first active ban, mute and jail
     * among them that reaches it, each with the account it came from. Reads storage, a fixed number of queries however
     * many spellings there are, so call it off the tick.
     */
    public Inherited atLogin(PlayerRef who, Instant now) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(now, "now");
        List<PlayerRef> underName = holders.underName(who.name());
        boolean known = underName.stream().anyMatch(holder -> holder.uuid().equals(who.uuid()));
        List<PlayerRef> variants = otherSpellings(who, underName);
        if (variants.isEmpty()) {
            return Inherited.nothing(known);
        }
        Optional<Instant> firstSeen = firstSeen(who);
        List<UUID> asked = new ArrayList<>();
        variants.forEach(variant -> asked.add(variant.uuid()));
        asked.add(who.uuid());
        Set<Sanction> held = new HashSet<>();
        Set<Sanction> givenBefore = new HashSet<>();
        for (CaseCopies.Copy copy : copies.heldBy(asked)) {
            if (copy.target().equals(who.uuid())) {
                givenBefore.add(new Sanction(copy.origin(), copy.kind(), copy.issuedAt()));
            } else {
                held.add(new Sanction(copy.target(), copy.kind(), copy.issuedAt()));
            }
        }
        Map<UUID, TempbanState> bans = repository.loadTempbans(variants);
        Map<UUID, MuteState> mutes = repository.loadMutes(variants);
        Map<UUID, JailState> jails = repository.loadJails(variants);
        TempbanState ban = TempbanState.none();
        MuteState mute = MuteState.none();
        Optional<PlayerRef> muteFrom = Optional.empty();
        JailState jail = JailState.none();
        Optional<PlayerRef> jailFrom = Optional.empty();
        for (PlayerRef variant : variants) {
            if (!ban.isActiveAt(now)
                    && bans.get(variant.uuid()) instanceof TempbanState.Active active
                    && active.isActiveAt(now)
                    && reaches(firstSeen, active.issuedAt())) {
                ban = active;
            }
            MuteState variantMute = mutes.getOrDefault(variant.uuid(), MuteState.none());
            if (!mute.isActiveAt(now)
                    && variantMute.isActiveAt(now)
                    && reaches(firstSeen, issuedAt(variantMute))
                    && original(variant, CaseCopies.Kind.MUTE, issuedAt(variantMute), held, givenBefore)) {
                mute = variantMute;
                muteFrom = Optional.of(variant);
            }
            if (!jail.isActiveAt(now)
                    && jails.get(variant.uuid()) instanceof JailState.Active active
                    && active.isActiveAt(now)
                    && reaches(firstSeen, active.issuedAt())
                    && original(variant, CaseCopies.Kind.JAIL, active.issuedAt(), held, givenBefore)) {
                jail = active;
                jailFrom = Optional.of(variant);
            }
        }
        return new Inherited(variants, known, ban, mute, muteFrom, jail, jailFrom);
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

    /**
     * Whether {@code variant}'s sanction issued at {@code issuedAt} may be copied: it is the variant's own, not a copy
     * the variant holds, and it was never copied onto the joining account before.
     */
    private static boolean original(
            PlayerRef variant, CaseCopies.Kind kind, Instant issuedAt, Set<Sanction> held, Set<Sanction> givenBefore) {
        Sanction sanction = new Sanction(variant.uuid(), kind, issuedAt);
        return !held.contains(sanction) && !givenBefore.contains(sanction);
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

    /** A sanction named the way a copy names it: whose it is, its kind, and when it was issued. */
    private record Sanction(UUID account, CaseCopies.Kind kind, Instant issuedAt) {}

    static Instant issuedAt(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuedAt();
            case MuteState.Timed timed -> timed.issuedAt();
            case MuteState.None none -> Instant.MAX;
        };
    }

    /**
     * What one login inherits: the accounts under other spellings of its name, whether this account is among the
     * holders already, and the ban, mute and jail that follow it from them ({@code none} where nothing does), the
     * mute and the jail with the account each came from.
     */
    public record Inherited(
            List<PlayerRef> variants,
            boolean known,
            TempbanState ban,
            MuteState mute,
            Optional<PlayerRef> muteFrom,
            JailState jail,
            Optional<PlayerRef> jailFrom) {

        public Inherited {
            variants = List.copyOf(variants);
            Objects.requireNonNull(ban, "ban");
            Objects.requireNonNull(mute, "mute");
            Objects.requireNonNull(muteFrom, "muteFrom");
            Objects.requireNonNull(jail, "jail");
            Objects.requireNonNull(jailFrom, "jailFrom");
        }

        /** Nothing inherited and no other spelling: the answer when names are not case-sensitive. */
        public static Inherited nothing() {
            return nothing(false);
        }

        static Inherited nothing(boolean known) {
            return new Inherited(
                    List.of(),
                    known,
                    TempbanState.none(),
                    MuteState.none(),
                    Optional.empty(),
                    JailState.none(),
                    Optional.empty());
        }
    }
}

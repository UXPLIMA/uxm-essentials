package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The placeholder resolution logic, behind a thin seam so it is testable without a live PlaceholderAPI.
 * The {@code UxmEssentialsExpansion} shell strips the {@code uxmessentials_} prefix, builds a {@link
 * PlayerRef} from the requesting {@code OfflinePlayer}, and asks this resolver for the value; the resolver
 * never touches a PlaceholderAPI type.
 *
 * <p>Each key is dispatched to the owning context's read seam ({@link PlaceholderContexts}). When that
 * context is disabled — its seam absent — or the player is offline for a session-only placeholder, the
 * value degrades to a sensible empty/"-" default ({@link #EMPTY}) rather than failing. An entirely unknown
 * key returns {@link Optional#empty()}, which the shell maps to {@code null} so PlaceholderAPI shows the
 * raw token unchanged.
 */
@NullMarked
public final class PlaceholderResolver {

    /** The value a placeholder degrades to when its owning module is disabled or the data is absent. */
    public static final String EMPTY = "-";

    private static final String YES = "yes";
    private static final String NO = "no";
    private static final String KIT_COOLDOWN_PREFIX = "kit_cooldown_";
    private static final String VOTES_PREFIX = "votes_";
    private static final String VOTES_TOP_PREFIX = "top_";
    private static final String VOTES_POSITION_PREFIX = "position_";
    private static final String VOTEPARTY_PREFIX = "voteparty_";

    private final PlaceholderContexts contexts;

    public PlaceholderResolver(PlaceholderContexts contexts) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    /**
     * Resolve the {@code uxmessentials_}-stripped {@code key} for {@code who}. {@code online} reflects
     * whether the requesting player is currently connected — session-only placeholders (presence) read
     * empty for an offline player. An unknown key returns {@link Optional#empty()}.
     */
    public Optional<String> resolve(PlayerRef who, boolean online, String key) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(key, "key");
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(KIT_COOLDOWN_PREFIX)) {
            return Optional.of(kitCooldown(who, normalized.substring(KIT_COOLDOWN_PREFIX.length())));
        }
        if (normalized.startsWith(VOTES_PREFIX)) {
            return Optional.of(votes(who, normalized.substring(VOTES_PREFIX.length())));
        }
        if (normalized.startsWith(VOTEPARTY_PREFIX)) {
            return Optional.of(voteparty(normalized.substring(VOTEPARTY_PREFIX.length())));
        }
        return switch (normalized) {
            case "homes_count", "homes_limit", "homes_left" -> Optional.of(homes(who, normalized));
            case "balance", "balance_formatted", "baltop_position" -> Optional.of(economy(who, normalized));
            case "afk", "afk_duration", "vanished" -> Optional.of(presence(who, online, normalized));
            case "vaults_count" -> Optional.of(vaults(who));
            case "muted", "jailed" -> Optional.of(moderation(who, normalized));
            default -> Optional.empty();
        };
    }

    private String homes(PlayerRef who, String key) {
        Optional<HomesPlaceholders> seam = contexts.homes();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        HomesPlaceholders homes = seam.get();
        int count = homes.count(who);
        int limit = homes.limit(who);
        return switch (key) {
            case "homes_count" -> Integer.toString(count);
            case "homes_limit" -> limit < 0 ? unlimited() : Integer.toString(limit);
            default -> limit < 0 ? unlimited() : Integer.toString(Math.max(0, limit - count));
        };
    }

    private String economy(PlayerRef who, String key) {
        Optional<EconomyPlaceholders> seam = contexts.economy();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        EconomyPlaceholders economy = seam.get();
        return switch (key) {
            case "balance" -> plainAmount(economy.balance(who));
            case "balance_formatted" -> economy.formatted(who);
            default -> baltopPosition(economy.baltopPosition(who));
        };
    }

    private String presence(PlayerRef who, boolean online, String key) {
        Optional<PresencePlaceholders> seam = contexts.presence();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        Optional<PresencePlaceholders.Snapshot> snapshot = seam.get().snapshot(who);
        if (snapshot.isEmpty()) {
            return EMPTY;
        }
        PresencePlaceholders.Snapshot state = snapshot.get();
        return switch (key) {
            case "afk" -> bool(state.afk());
            case "afk_duration" -> state.afk() ? PlaceholderDurations.compact(state.afkFor()) : EMPTY;
            default -> bool(state.vanished());
        };
    }

    private String kitCooldown(PlayerRef who, String kitId) {
        Optional<KitsPlaceholders> seam = contexts.kits();
        if (seam.isEmpty() || kitId.isBlank()) {
            return EMPTY;
        }
        Optional<Duration> remaining = seam.get().cooldownRemaining(who, kitId);
        return remaining.map(PlaceholderDurations::compact).orElse(EMPTY);
    }

    private String vaults(PlayerRef who) {
        return contexts.vaults().map(seam -> Integer.toString(seam.count(who))).orElse(EMPTY);
    }

    /**
     * Resolve a {@code votes_*} tail. Three sub-patterns:
     * <ul>
     *   <li>{@code <period>} — the requesting player's vote count for that period.</li>
     *   <li>{@code top_<period>_<n>_name} or {@code top_<period>_<n>_votes} — the name or vote
     *       count of the player ranked {@code <n>} (1-based) on the leaderboard.</li>
     *   <li>{@code position_<period>} — the requesting player's 1-based leaderboard rank.</li>
     * </ul>
     */
    private String votes(PlayerRef who, String tail) {
        Optional<VotePlaceholders> seam = contexts.vote();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        VotePlaceholders vote = seam.get();

        if (tail.startsWith(VOTES_TOP_PREFIX)) {
            // top_<period>_<n>_name  or  top_<period>_<n>_votes
            // e.g. tail = "top_monthly_1_name" → strip "top_" → "monthly_1_name"
            String rest = tail.substring(VOTES_TOP_PREFIX.length());
            // rest = "<period>_<n>_<field>"
            List<String> parts = List.of(rest.split("_", 3));
            if (parts.size() != 3) {
                return EMPTY;
            }
            VotePeriod period = parsePeriod(parts.get(0));
            if (period == null) {
                return EMPTY;
            }
            int rank;
            try {
                rank = Integer.parseInt(parts.get(1));
            } catch (NumberFormatException ignored) {
                return EMPTY;
            }
            String field = parts.get(2);
            Optional<VoteRanking> row = vote.topAt(period, rank);
            if (row.isEmpty()) {
                return EMPTY;
            }
            VoteRanking ranking = row.get();
            return switch (field) {
                case "name" -> ranking.player().name();
                case "votes" -> Long.toString(ranking.votes());
                default -> EMPTY;
            };
        }

        if (tail.startsWith(VOTES_POSITION_PREFIX)) {
            // position_<period>
            String periodName = tail.substring(VOTES_POSITION_PREFIX.length());
            VotePeriod period = parsePeriod(periodName);
            if (period == null) {
                return EMPTY;
            }
            OptionalInt pos = vote.positionOf(who, period);
            return pos.isPresent() ? Integer.toString(pos.getAsInt()) : EMPTY;
        }

        // Plain period count: votes_<period>
        VotePeriod period = parsePeriod(tail);
        if (period == null) {
            return EMPTY;
        }
        return Long.toString(vote.countFor(who, period));
    }

    private static @Nullable VotePeriod parsePeriod(String periodName) {
        return switch (periodName) {
            case "daily" -> VotePeriod.DAILY;
            case "weekly" -> VotePeriod.WEEKLY;
            case "monthly" -> VotePeriod.MONTHLY;
            case "alltime" -> VotePeriod.ALLTIME;
            default -> null;
        };
    }

    private String voteparty(String subKey) {
        Optional<VotePlaceholders> seam = contexts.vote();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        VotePlaceholders vote = seam.get();
        int count = vote.partyCount();
        int threshold = vote.partyThreshold();
        return switch (subKey) {
            case "current" -> Integer.toString(count);
            case "required" -> Integer.toString(threshold);
            case "remaining" -> Integer.toString(Math.max(0, threshold - count));
            default -> EMPTY;
        };
    }

    private String moderation(PlayerRef who, String key) {
        Optional<ModerationPlaceholders> seam = contexts.moderation();
        if (seam.isEmpty()) {
            return NO;
        }
        ModerationPlaceholders moderation = seam.get();
        return bool(key.equals("muted") ? moderation.isMuted(who) : moderation.isJailed(who));
    }

    private static String baltopPosition(OptionalInt position) {
        return position.isPresent() ? Integer.toString(position.getAsInt()) : EMPTY;
    }

    private static String plainAmount(Money money) {
        return money.amount().toPlainString();
    }

    private static String bool(boolean value) {
        return value ? YES : NO;
    }

    private static String unlimited() {
        return "∞";
    }
}

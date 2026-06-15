package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
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
    private static final String ECONOMY_PREFIX = "economy_";
    private static final String VOTES_PREFIX = "votes_";
    private static final String VOTES_TOP_PREFIX = "top_";
    private static final String VOTES_POSITION_PREFIX = "position_";
    private static final String VOTES_STREAK_PREFIX = "streak_";
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
        if (normalized.startsWith(ECONOMY_PREFIX)) {
            return Optional.of(economyFamily(who, normalized.substring(ECONOMY_PREFIX.length())));
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

    /** The bare economy keys ({@code balance}, {@code balance_formatted}, {@code baltop_position}). */
    private String economy(PlayerRef who, String key) {
        return economyFamily(who, key);
    }

    /**
     * Resolve an {@code economy_*} tail (and the bare {@code balance}/{@code baltop_position} aliases) against
     * the economy seam. The default-currency scalars read straight through; the per-currency forms
     * ({@code balance_<currency>}, {@code balance_formatted_<currency>}) resolve the currency by id; the
     * indexed forms ({@code baltop_<n>_*}, {@code baltop_<currency>_<n>_*}) parse a 1-based rank and read the
     * bounded ranked snapshot.
     */
    private String economyFamily(PlayerRef who, String tail) {
        Optional<EconomyPlaceholders> seam = contexts.economy();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        EconomyPlaceholders economy = seam.get();
        switch (tail) {
            case "balance" -> {
                return plainAmount(economy.balance(who));
            }
            case "balance_formatted" -> {
                return economy.formatted(who);
            }
            case "balance_compact", "balance_short" -> {
                return economy.compact(who);
            }
            case "baltop_position" -> {
                return baltopPosition(economy.baltopPosition(who));
            }
            case "currency_name" -> {
                return economy.defaultCurrency().plural();
            }
            case "currency_symbol" -> {
                return economy.defaultCurrency().symbol();
            }
            default -> {
                if (tail.startsWith("balance_formatted_")) {
                    return currencyBalanceFormatted(who, economy, tail.substring("balance_formatted_".length()));
                }
                if (tail.startsWith("balance_")) {
                    return currencyBalance(who, economy, tail.substring("balance_".length()));
                }
                if (tail.startsWith("baltop_")) {
                    return baltop(economy, tail.substring("baltop_".length()));
                }
                return EMPTY;
            }
        }
    }

    private static String currencyBalance(PlayerRef who, EconomyPlaceholders economy, String currencyId) {
        return economy.currency(currencyId)
                .map(currency -> plainAmount(economy.balance(who, currency)))
                .orElse(EMPTY);
    }

    private static String currencyBalanceFormatted(PlayerRef who, EconomyPlaceholders economy, String currencyId) {
        return economy.currency(currencyId)
                .map(currency -> MoneyFormat.withSymbol(economy.balance(who, currency)))
                .orElse(EMPTY);
    }

    /**
     * Resolve a {@code baltop_*} tail: either {@code <n>_<field>} on the default currency or
     * {@code <currency>_<n>_<field>} on a named currency. The rank is parsed 1-based; an unparseable or
     * out-of-range rank, an unknown currency, or an unknown field all degrade to the dash.
     */
    private String baltop(EconomyPlaceholders economy, String tail) {
        List<String> parts = List.of(tail.split("_", 3));
        // Try <currency>_<n>_<field> first when the leading token is not itself a number.
        if (parts.size() == 3 && !isInteger(parts.get(0))) {
            Optional<Currency> currency = economy.currency(parts.get(0));
            if (currency.isEmpty()) {
                return EMPTY;
            }
            return baltopRow(economy, currency.get(), parts.get(1), parts.get(2));
        }
        // Otherwise <n>_<field> on the default currency.
        if (parts.size() >= 2) {
            return baltopRow(economy, economy.defaultCurrency(), parts.get(0), parts.get(1));
        }
        return EMPTY;
    }

    private String baltopRow(EconomyPlaceholders economy, Currency currency, String rankToken, String field) {
        int rank;
        try {
            rank = Integer.parseInt(rankToken);
        } catch (NumberFormatException ignored) {
            return EMPTY;
        }
        Optional<BaltopRow> row = economy.baltopRow(currency, rank);
        if (row.isEmpty()) {
            return EMPTY;
        }
        BaltopRow entry = row.get();
        return switch (field) {
            case "name" -> entry.owner().name();
            case "uuid" -> entry.owner().uuid().toString();
            case "amount" -> plainAmount(entry.balance());
            case "formatted" -> MoneyFormat.withSymbol(entry.balance());
            default -> EMPTY;
        };
    }

    private static boolean isInteger(String token) {
        try {
            Integer.parseInt(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
     *   <li>{@code streak_current} or {@code streak_best} — the requesting player's current or
     *       best consecutive-day voting streak.</li>
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

        if (tail.startsWith(VOTES_STREAK_PREFIX)) {
            // streak_current  or  streak_best
            String field = tail.substring(VOTES_STREAK_PREFIX.length());
            return switch (field) {
                case "current" -> Long.toString(vote.currentStreak(who));
                case "best" -> Long.toString(vote.bestStreak(who));
                default -> EMPTY;
            };
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

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
    private static final String HOMES_PREFIX = "homes_";
    private static final String PRESENCE_PREFIX = "presence_";
    private static final String PLAYERSTATE_PREFIX = "playerstate_";
    private static final String TELEPORT_PREFIX = "teleport_";
    private static final String MODERATION_PREFIX = "moderation_";
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
        if (normalized.startsWith(HOMES_PREFIX)) {
            return Optional.of(homesFamily(who, normalized.substring(HOMES_PREFIX.length())));
        }
        if (normalized.startsWith(VOTES_PREFIX)) {
            return Optional.of(votes(who, normalized.substring(VOTES_PREFIX.length())));
        }
        if (normalized.startsWith(VOTEPARTY_PREFIX)) {
            return Optional.of(voteparty(normalized.substring(VOTEPARTY_PREFIX.length())));
        }
        if (normalized.startsWith(PRESENCE_PREFIX)) {
            return Optional.of(presence(who, online, normalized.substring(PRESENCE_PREFIX.length())));
        }
        if (normalized.startsWith(PLAYERSTATE_PREFIX)) {
            return Optional.of(playerstate(who, online, normalized.substring(PLAYERSTATE_PREFIX.length())));
        }
        if (normalized.startsWith(TELEPORT_PREFIX)) {
            return Optional.of(teleport(who, online, normalized.substring(TELEPORT_PREFIX.length())));
        }
        if (normalized.startsWith(MODERATION_PREFIX)) {
            return Optional.of(moderationFamily(who, normalized.substring(MODERATION_PREFIX.length())));
        }
        return switch (normalized) {
            case "balance", "balance_formatted", "baltop_position" -> Optional.of(economy(who, normalized));
            case "afk", "afk_duration", "vanished" -> Optional.of(presence(who, online, normalized));
            case "vaults_count" -> Optional.of(vaults(who));
            case "muted", "jailed" -> Optional.of(moderation(who, normalized));
            default -> Optional.empty();
        };
    }

    /**
     * Resolve a {@code homes_*} tail against the homes seam. The count/limit/left scalars and the home-list
     * placeholders read from the seam; the indexed forms ({@code <index>}, {@code <index>_world},
     * {@code <index>_x|y|z}) parse the 1-based index from the tail and degrade to the dash when it is out of
     * range or unparseable, and {@code exists_<label>} reports whether a home carries that label.
     */
    private String homesFamily(PlayerRef who, String tail) {
        Optional<HomesPlaceholders> seam = contexts.homes();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        HomesPlaceholders homes = seam.get();
        switch (tail) {
            case "count" -> {
                return Integer.toString(homes.count(who));
            }
            case "limit" -> {
                int limit = homes.limit(who);
                return limit < 0 ? unlimited() : Integer.toString(limit);
            }
            case "left" -> {
                int limit = homes.limit(who);
                return limit < 0 ? unlimited() : Integer.toString(Math.max(0, limit - homes.count(who)));
            }
            case "list" -> {
                List<HomesPlaceholders.HomeView> all = homes.list(who);
                return all.isEmpty() ? EMPTY : joinNames(all);
            }
            default -> {
                if (tail.startsWith("exists_")) {
                    return homeExists(homes.list(who), tail.substring("exists_".length()));
                }
                return indexedHome(homes.list(who), tail);
            }
        }
    }

    private static String joinNames(List<HomesPlaceholders.HomeView> homes) {
        StringBuilder names = new StringBuilder();
        for (HomesPlaceholders.HomeView home : homes) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(home.name());
        }
        return names.toString();
    }

    private static String homeExists(List<HomesPlaceholders.HomeView> homes, String label) {
        if (label.isBlank()) {
            return NO;
        }
        boolean present = homes.stream().anyMatch(home -> home.name().equalsIgnoreCase(label));
        return bool(present);
    }

    /**
     * Resolve an indexed-home tail: {@code <index>}, {@code <index>_world}, or {@code <index>_x|y|z}. The
     * leading token is the 1-based home index; an unparseable or out-of-range index degrades to the dash.
     */
    private static String indexedHome(List<HomesPlaceholders.HomeView> homes, String tail) {
        List<String> parts = List.of(tail.split("_", 2));
        int index;
        try {
            index = Integer.parseInt(parts.get(0));
        } catch (NumberFormatException ignored) {
            return EMPTY;
        }
        if (index < 1 || index > homes.size()) {
            return EMPTY;
        }
        HomesPlaceholders.HomeView home = homes.get(index - 1);
        if (parts.size() == 1) {
            return home.name();
        }
        return switch (parts.get(1)) {
            case "world" -> home.world();
            case "x" -> Integer.toString(home.blockX());
            case "y" -> Integer.toString(home.blockY());
            case "z" -> Integer.toString(home.blockZ());
            default -> EMPTY;
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

    /**
     * Resolve a presence key against the presence seam. Accepts both the bare keys ({@code afk},
     * {@code afk_duration}, {@code vanished}) and the {@code presence_}-stripped family ({@code nickname},
     * {@code realname}, {@code afk_since} as an alias of {@code afk_duration}, {@code afk_reason}). Presence is
     * session-only state, so an offline player or a disabled module degrades every key to the dash.
     */
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
            case "afk_duration", "afk_since" -> state.afk() ? PlaceholderDurations.compact(state.afkFor()) : EMPTY;
            case "afk_reason" -> state.afk() ? state.afkReason().orElse(EMPTY) : EMPTY;
            case "nickname" -> state.nickname();
            case "realname" -> who.name();
            default -> bool(state.vanished());
        };
    }

    /**
     * Resolve a {@code playerstate_}-stripped key against the playerstate seam. Every key is live session
     * state, so a disabled module or an offline player degrades each to the dash. Coordinates are block-
     * truncated, the float scalars (speed, experience) are rendered with up to two decimal places trimmed,
     * and {@code playtime}/{@code playtime_formatted} read the total time played.
     */
    private String playerstate(PlayerRef who, boolean online, String key) {
        Optional<PlayerstatePlaceholders> seam = contexts.playerstate();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        Optional<PlayerstatePlaceholders.Snapshot> snapshot = seam.get().snapshot(who);
        return snapshot.map(state -> playerstateField(state, key)).orElse(EMPTY);
    }

    private static String playerstateField(PlayerstatePlaceholders.Snapshot state, String key) {
        return switch (key) {
            case "gamemode" -> state.gamemode();
            case "fly" -> bool(state.flightAllowed());
            case "flying" -> bool(state.flying());
            case "god" -> bool(state.god());
            case "speed" -> decimal(state.flying() ? state.flySpeed() : state.walkSpeed());
            case "walk_speed" -> decimal(state.walkSpeed());
            case "fly_speed" -> decimal(state.flySpeed());
            case "health" -> decimal(state.health());
            case "max_health" -> decimal(state.maxHealth());
            case "food" -> Integer.toString(state.food());
            case "level" -> Integer.toString(state.level());
            case "xp" -> decimal(state.experienceProgress());
            case "world" -> state.world();
            case "x" -> Integer.toString(state.blockX());
            case "y" -> Integer.toString(state.blockY());
            case "z" -> Integer.toString(state.blockZ());
            case "biome" -> state.biome();
            case "playtime" -> Long.toString(state.playtime().toHours());
            case "playtime_formatted" -> PlaceholderDurations.compact(state.playtime());
            default -> EMPTY;
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

    /**
     * Resolve a {@code teleport_}-stripped key against the teleport seam. The cooldown/warmup remaining keys
     * carry a raw whole-second scalar and a {@code _formatted} {@code 1m30s} variant, reading {@code 0} (or
     * {@code 0s}) when nothing is in flight; the back-location keys read the captured {@code /back} point
     * (dash when none); the request scalars and the accept flag read the {@code tpa} registry and the
     * {@code /tptoggle} state. A disabled module degrades every key to the dash; offline reads degrade the
     * session-only request/accept and warmup keys to the dash since they cannot be queried.
     */
    private String teleport(PlayerRef who, boolean online, String key) {
        Optional<TeleportPlaceholders> seam = contexts.teleport();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        TeleportPlaceholders teleport = seam.get();
        return switch (key) {
            case "cooldown_remaining" -> remainingSeconds(teleport.cooldownRemaining(who));
            case "cooldown_remaining_formatted" -> remainingFormatted(teleport.cooldownRemaining(who));
            case "warmup_remaining" -> online ? remainingSeconds(teleport.warmupRemaining(who)) : EMPTY;
            case "warmup_remaining_formatted" -> online ? remainingFormatted(teleport.warmupRemaining(who)) : EMPTY;
            case "back_available" -> bool(teleport.backLocation(who).isPresent());
            case "back_world" -> teleport.backLocation(who)
                    .map(TeleportPlaceholders.BackView::world)
                    .orElse(EMPTY);
            case "back_x" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockX);
            case "back_y" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockY);
            case "back_z" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockZ);
            case "tpa_incoming" -> online ? Integer.toString(teleport.incomingRequests(who)) : EMPTY;
            case "tpa_pending" -> online ? bool(teleport.hasOutgoingRequest(who)) : EMPTY;
            case "accepting" -> online ? bool(teleport.acceptingRequests(who)) : EMPTY;
            default -> EMPTY;
        };
    }

    private static String backCoordinate(
            Optional<TeleportPlaceholders.BackView> back,
            java.util.function.ToIntFunction<TeleportPlaceholders.BackView> field) {
        return back.map(view -> Integer.toString(field.applyAsInt(view))).orElse(EMPTY);
    }

    private static String remainingSeconds(Optional<Duration> remaining) {
        return Long.toString(remaining.map(d -> Math.max(0, d.toSeconds())).orElse(0L));
    }

    private static String remainingFormatted(Optional<Duration> remaining) {
        return remaining.map(PlaceholderDurations::compact).orElse(PlaceholderDurations.compact(Duration.ZERO));
    }

    private String moderation(PlayerRef who, String key) {
        Optional<ModerationPlaceholders> seam = contexts.moderation();
        if (seam.isEmpty()) {
            return NO;
        }
        ModerationPlaceholders moderation = seam.get();
        return bool(key.equals("muted") ? moderation.isMuted(who) : moderation.isJailed(who));
    }

    /**
     * Resolve a {@code moderation_}-stripped key against the moderation seam. The state-boolean keys
     * ({@code banned}, {@code muted}, {@code jailed}, {@code frozen}) and {@code warns} read straight through;
     * the ban/mute detail keys read the active, clock-gated sanction and render its remaining wait (raw whole
     * seconds and a {@code _formatted} variant; {@code permanent} for a permanent sanction), reason and issuer.
     * A disabled module degrades the booleans to "no" and the detail/count keys to the dash.
     */
    private String moderationFamily(PlayerRef who, String tail) {
        Optional<ModerationPlaceholders> seam = contexts.moderation();
        if (seam.isEmpty()) {
            return isBooleanModerationKey(tail) ? NO : EMPTY;
        }
        ModerationPlaceholders moderation = seam.get();
        return switch (tail) {
            case "banned" -> bool(moderation.activeBan(who).isPresent());
            case "muted" -> bool(moderation.isMuted(who));
            case "jailed" -> bool(moderation.isJailed(who));
            case "frozen" -> bool(moderation.isFrozen(who));
            case "warns" -> Integer.toString(moderation.warnCount(who));
            case "ban_reason" -> sanctionField(moderation.activeBan(who), Sanction.REASON, false);
            case "ban_issuer" -> sanctionField(moderation.activeBan(who), Sanction.ISSUER, false);
            case "ban_remaining" -> sanctionField(moderation.activeBan(who), Sanction.REMAINING, false);
            case "ban_remaining_formatted" -> sanctionField(moderation.activeBan(who), Sanction.REMAINING, true);
            case "mute_reason" -> sanctionField(moderation.activeMute(who), Sanction.REASON, false);
            case "mute_issuer" -> sanctionField(moderation.activeMute(who), Sanction.ISSUER, false);
            case "mute_remaining" -> sanctionField(moderation.activeMute(who), Sanction.REMAINING, false);
            case "mute_remaining_formatted" -> sanctionField(moderation.activeMute(who), Sanction.REMAINING, true);
            default -> EMPTY;
        };
    }

    private static boolean isBooleanModerationKey(String tail) {
        return switch (tail) {
            case "banned", "muted", "jailed", "frozen" -> true;
            default -> false;
        };
    }

    private enum Sanction {
        REASON,
        ISSUER,
        REMAINING
    }

    /** Render one field of an active ban/mute view, or the dash when no sanction is active. */
    private static String sanctionField(
            Optional<ModerationPlaceholders.SanctionView> view, Sanction field, boolean formatted) {
        if (view.isEmpty()) {
            return EMPTY;
        }
        ModerationPlaceholders.SanctionView active = view.get();
        return switch (field) {
            case REASON -> active.reason();
            case ISSUER -> active.issuer();
            case REMAINING -> sanctionRemaining(active.remaining(), formatted);
        };
    }

    /** A sanction's remaining wait, or {@code permanent} when it never lifts. */
    private static String sanctionRemaining(Optional<Duration> remaining, boolean formatted) {
        if (remaining.isEmpty()) {
            return "permanent";
        }
        Duration left = remaining.get();
        return formatted ? PlaceholderDurations.compact(left) : Long.toString(Math.max(0, left.toSeconds()));
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

    /**
     * A live scalar (health, speed, experience progress) rounded to two decimal places with trailing zeros
     * stripped — so {@code 20.0} reads {@code 20} and {@code 0.25} reads {@code 0.25}.
     */
    private static String decimal(double value) {
        return new java.math.BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String unlimited() {
        return "∞";
    }
}

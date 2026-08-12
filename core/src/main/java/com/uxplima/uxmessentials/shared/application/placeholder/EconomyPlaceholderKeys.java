package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/** Money and standing: balances, the leaderboard, and where a player sits on the rank ladder. */
final class EconomyPlaceholderKeys {

    private static final ModuleId ECONOMY = ModuleId.of("economy");
    private static final ModuleId RANKS = ModuleId.of("ranks");

    private EconomyPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(balances(), baltop(), ranks()).flatMap(List::stream).toList();
    }

    private static List<PlaceholderSpec> balances() {
        return List.of(
                PlaceholderSpec.of(
                        "balance",
                        "The player's balance in the default currency, as a plain number.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "balance_formatted",
                        "The player's balance with the currency symbol and grouping applied.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_balance",
                        "The player's balance in the default currency, as a plain number.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_balance_formatted",
                        "The player's balance with the currency symbol and grouping applied.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_balance_compact",
                        "The player's balance shortened to 1.2k / 3.4M.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_balance_short",
                        "The same shortened balance, under the spelling a config may prefer.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_currency_name",
                        "The plural name of the default currency.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_currency_symbol",
                        "The symbol of the default currency.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_balance_<currency>",
                        "The player's balance in one named currency, as a plain number.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_balance_formatted_<currency>",
                        "The player's balance in one named currency, with its symbol applied.",
                        PlaceholderScope.PLAYER,
                        ECONOMY));
    }

    private static List<PlaceholderSpec> baltop() {
        return List.of(
                PlaceholderSpec.of(
                        "baltop_position",
                        "The player's place on the default-currency leaderboard.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.of(
                        "economy_baltop_position",
                        "The player's place on the default-currency leaderboard.",
                        PlaceholderScope.PLAYER,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<n>_name",
                        "The name of the player ranked nth on the default-currency leaderboard.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<n>_uuid",
                        "The uuid of the player ranked nth on the default-currency leaderboard.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<n>_amount",
                        "The balance of the player ranked nth, as a plain number.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<n>_formatted",
                        "The balance of the player ranked nth, with the currency symbol applied.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<currency>_<n>_name",
                        "The name of the player ranked nth on one named currency's leaderboard.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<currency>_<n>_uuid",
                        "The uuid of the player ranked nth on one named currency's leaderboard.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<currency>_<n>_amount",
                        "The balance of the player ranked nth in one named currency, as a plain number.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY),
                PlaceholderSpec.family(
                        "economy_baltop_<currency>_<n>_formatted",
                        "The balance of the player ranked nth in one named currency, with its symbol applied.",
                        PlaceholderScope.GLOBAL,
                        ECONOMY));
    }

    private static List<PlaceholderSpec> ranks() {
        return List.of(
                PlaceholderSpec.of("rank", "The player's current rank.", PlaceholderScope.PLAYER, RANKS),
                PlaceholderSpec.of(
                        "rank_next",
                        "The rank above the player's, or max when they are at the top of the ladder.",
                        PlaceholderScope.PLAYER,
                        RANKS),
                PlaceholderSpec.of(
                        "rank_next_cost",
                        "What the next rankup charges, or the dash at the top of the ladder.",
                        PlaceholderScope.PLAYER,
                        RANKS),
                PlaceholderSpec.of(
                        "rank_position",
                        "Which rung of the ladder the player stands on, counting from one.",
                        PlaceholderScope.PLAYER,
                        RANKS),
                PlaceholderSpec.of("rank_total", "How many rungs the ladder holds.", PlaceholderScope.PLAYER, RANKS),
                PlaceholderSpec.of(
                        "rank_progress",
                        "How far up the ladder the player stands, as a whole percentage.",
                        PlaceholderScope.PLAYER,
                        RANKS),
                PlaceholderSpec.of(
                        "prestige", "How many times the player has prestiged.", PlaceholderScope.PLAYER, RANKS));
    }
}

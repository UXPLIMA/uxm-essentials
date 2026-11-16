package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/** What a player collects: kits, vault space, and votes. */
final class ContentPlaceholderKeys {

    private static final ModuleId KITS = ModuleId.of("kits");
    private static final ModuleId VAULTS = ModuleId.of("vaults");
    private static final ModuleId VOTE = ModuleId.of("vote");
    private static final ModuleId SKIN = ModuleId.of("skin");

    private ContentPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(kits(), vaults(), votes(), skin())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PlaceholderSpec> kits() {
        return List.of(
                PlaceholderSpec.of(
                        "kits_list",
                        "The ids of the kits the player may claim, comma separated.",
                        "starter, daily, vip",
                        PlaceholderScope.PLAYER,
                        KITS),
                PlaceholderSpec.family(
                        "kit_cooldown_<kit>",
                        "The wait left before the player may claim one kit again.",
                        "23h59m",
                        PlaceholderScope.PLAYER,
                        KITS),
                PlaceholderSpec.family(
                        "kit_cooldown_<kit>_formatted",
                        "The same remaining kit wait, under the spelling a config may prefer.",
                        "23h59m",
                        PlaceholderScope.PLAYER,
                        KITS),
                PlaceholderSpec.family(
                        "kit_available_<kit>",
                        "Whether the player may claim one kit right now (yes/no).",
                        "yes",
                        PlaceholderScope.PLAYER,
                        KITS),
                PlaceholderSpec.family(
                        "kit_has_<kit>",
                        "Whether the player holds one kit's permission (yes/no).",
                        "yes",
                        PlaceholderScope.PLAYER,
                        KITS),
                PlaceholderSpec.family(
                        "kit_cost_<kit>",
                        "What one kit charges to claim, or free when it charges nothing.",
                        "$250.00",
                        PlaceholderScope.GLOBAL,
                        KITS),
                PlaceholderSpec.family(
                        "kit_claims_left_<kit>",
                        "How many claims of one kit the player has left; the infinity marker when it repeats.",
                        "2",
                        PlaceholderScope.PLAYER,
                        KITS));
    }

    private static List<PlaceholderSpec> vaults() {
        return List.of(
                PlaceholderSpec.of(
                        "vaults_count", "How many vaults the player holds.", "2", PlaceholderScope.PLAYER, VAULTS),
                PlaceholderSpec.of(
                        "vaults_max",
                        "How many vaults the player may open; the infinity marker when unlimited.",
                        "5",
                        PlaceholderScope.PLAYER,
                        VAULTS),
                PlaceholderSpec.of(
                        "vaults_left",
                        "How many more vaults the player may open.",
                        "3",
                        PlaceholderScope.PLAYER,
                        VAULTS),
                PlaceholderSpec.of(
                        "vaults_size",
                        "How many rows each of the player's vaults holds.",
                        "6",
                        PlaceholderScope.PLAYER,
                        VAULTS));
    }

    private static List<PlaceholderSpec> votes() {
        return List.of(
                PlaceholderSpec.family(
                        "votes_<period>",
                        "The player's vote count for one period: daily, weekly, monthly or alltime.",
                        "14",
                        PlaceholderScope.PLAYER,
                        VOTE),
                PlaceholderSpec.family(
                        "votes_position_<period>",
                        "Where the player sits on one period's vote leaderboard.",
                        "3",
                        PlaceholderScope.PLAYER,
                        VOTE),
                PlaceholderSpec.family(
                        "votes_top_<period>_<n>_name",
                        "The name of the player ranked nth on one period's vote leaderboard.",
                        "Steve",
                        PlaceholderScope.GLOBAL,
                        VOTE),
                PlaceholderSpec.family(
                        "votes_top_<period>_<n>_votes",
                        "The vote count of the player ranked nth on one period's leaderboard.",
                        "42",
                        PlaceholderScope.GLOBAL,
                        VOTE),
                PlaceholderSpec.of(
                        "votes_streak_current",
                        "How many days in a row the player has voted.",
                        "5",
                        PlaceholderScope.PLAYER,
                        VOTE),
                PlaceholderSpec.of(
                        "votes_streak_best",
                        "The player's longest voting streak.",
                        "12",
                        PlaceholderScope.PLAYER,
                        VOTE),
                PlaceholderSpec.of(
                        "voteparty_current",
                        "How many votes the party has collected.",
                        "37",
                        PlaceholderScope.GLOBAL,
                        VOTE),
                PlaceholderSpec.of(
                        "voteparty_required",
                        "How many votes the party needs to fire.",
                        "50",
                        PlaceholderScope.GLOBAL,
                        VOTE),
                PlaceholderSpec.of(
                        "voteparty_remaining",
                        "How many votes the party still needs.",
                        "13",
                        PlaceholderScope.GLOBAL,
                        VOTE));
    }

    /**
     * What the player chose, not what they happen to be wearing: a player who chose nothing reads the dash, since
     * the join order's answer (their premium skin, their Bedrock skin, one of the pool) is not a choice of theirs.
     */
    private static List<PlaceholderSpec> skin() {
        return List.of(
                PlaceholderSpec.of(
                        "skin_source",
                        "Where the player's chosen skin came from: by-name, by-url, by-file, bedrock or fallback.",
                        "by-name",
                        PlaceholderScope.PLAYER,
                        SKIN),
                PlaceholderSpec.of(
                        "skin_value",
                        "What that source names: the account, the link, the file or the Bedrock id.",
                        "Notch",
                        PlaceholderScope.PLAYER,
                        SKIN),
                PlaceholderSpec.of(
                        "skin_model",
                        "The player model the skin was cut for: classic or slim.",
                        "classic",
                        PlaceholderScope.PLAYER,
                        SKIN),
                PlaceholderSpec.of(
                        "skin_chosen",
                        "Whether the player chose a skin of their own (yes/no).",
                        "yes",
                        PlaceholderScope.PLAYER,
                        SKIN));
    }
}

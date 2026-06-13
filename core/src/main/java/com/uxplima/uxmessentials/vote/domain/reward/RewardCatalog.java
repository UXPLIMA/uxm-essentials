package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The operator-configured reward sets the engine resolves against, grouped by what triggers them: the
 * per-vote specs (every vote), the per-site specs (keyed by the vote-list service name), the first-vote
 * specs (a player's very first vote), and the milestone rewards (keyed off the all-time count). The
 * adapter parses {@code modules/vote/config.conf} into this catalog once on load and swaps it atomically
 * on reload.
 *
 * <p>Per-site lookup is case-insensitive: the keys are stored lower-cased and {@link #forSite(String)}
 * lower-cases its argument, so a vote from {@code "PlanetMinecraft"} matches a {@code planetminecraft}
 * config key. A service with no configured site rewards resolves to an empty list, never null.
 *
 * @param perVote the specs paid on every vote
 * @param perSite the specs paid per vote-list service, keyed by lower-cased service name
 * @param firstVote the specs paid on a player's first ever vote
 * @param milestones the all-time-count milestone rewards
 */
public record RewardCatalog(
        List<RewardSpec> perVote,
        Map<String, List<RewardSpec>> perSite,
        List<RewardSpec> firstVote,
        List<MilestoneReward> milestones) {

    public RewardCatalog {
        Objects.requireNonNull(perVote, "perVote");
        Objects.requireNonNull(perSite, "perSite");
        Objects.requireNonNull(firstVote, "firstVote");
        Objects.requireNonNull(milestones, "milestones");
        perVote = List.copyOf(perVote);
        perSite = lowerCasedCopy(perSite);
        firstVote = List.copyOf(firstVote);
        milestones = List.copyOf(milestones);
    }

    /** An empty catalog: no per-vote, per-site, first-vote, or milestone rewards. */
    public static RewardCatalog empty() {
        return new RewardCatalog(List.of(), Map.of(), List.of(), List.of());
    }

    /** The per-site specs for {@code service}, matched case-insensitively; an empty list when none. */
    public List<RewardSpec> forSite(String service) {
        Objects.requireNonNull(service, "service");
        return perSite.getOrDefault(service.toLowerCase(Locale.ROOT), List.of());
    }

    private static Map<String, List<RewardSpec>> lowerCasedCopy(Map<String, List<RewardSpec>> source) {
        Map<String, List<RewardSpec>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<RewardSpec>> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "perSite key").toLowerCase(Locale.ROOT);
            copy.put(key, List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}

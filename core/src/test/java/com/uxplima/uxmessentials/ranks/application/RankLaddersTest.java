package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RankLadders}' parse of the {@code ranks.conf} tree: each {@code ranks.<id>} section becomes a
 * {@link Rank} with its order, display name, cost and raw requirement/action lists, the ladder is ordered by the
 * parsed order, and a section with only some keys falls back to the shipped defaults (id as the display name, a
 * free rank, no requirements or actions). An empty tree yields an empty ladder.
 */
class RankLaddersTest {

    @Test
    void parsesEachSectionIntoAnOrderedRank() {
        ConfigStore config = new FixedConfig(Map.ofEntries(
                Map.entry("ranks", List.of("citizen", "first")),
                Map.entry("ranks.first.order", 10),
                Map.entry("ranks.first.display-name", "&aFirst"),
                Map.entry("ranks.first.cost", 0),
                Map.entry("ranks.citizen.order", 20),
                Map.entry("ranks.citizen.display-name", "&bCitizen"),
                Map.entry("ranks.citizen.cost", 500),
                Map.entry("ranks.citizen.requirements", List.of("money 500")),
                Map.entry("ranks.citizen.actions", List.of("console lp user %player% parent set citizen"))));

        RankLadder ladder = RankLadders.from(config);

        assertThat(ladder.ranks()).extracting(Rank::id).containsExactly(RankId.of("first"), RankId.of("citizen"));
        Rank citizen = ladder.rank(RankId.of("citizen")).orElseThrow();
        assertThat(citizen.order()).isEqualTo(20);
        assertThat(citizen.displayName()).isEqualTo("&bCitizen");
        assertThat(citizen.cost()).isEqualTo(500L);
        assertThat(citizen.requirements()).containsExactly("money 500");
        assertThat(citizen.actions()).containsExactly("console lp user %player% parent set citizen");
    }

    @Test
    void fallsBackToDefaultsForMissingKeys() {
        ConfigStore config = new FixedConfig(Map.of("ranks", List.of("rookie")));

        Rank rookie = RankLadders.from(config).rank(RankId.of("rookie")).orElseThrow();

        assertThat(rookie.order()).isZero();
        assertThat(rookie.displayName()).isEqualTo("rookie");
        assertThat(rookie.cost()).isZero();
        assertThat(rookie.requirements()).isEmpty();
        assertThat(rookie.actions()).isEmpty();
    }

    @Test
    void anEmptyTreeYieldsAnEmptyLadder() {
        assertThat(RankLadders.from(new FixedConfig(Map.of())).isEmpty()).isTrue();
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }

        @Override
        public long getLong(String path, long fallback) {
            return values.get(path) instanceof Number n ? n.longValue() : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // the fake models a config list as a List<String> at the key's path
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // the fake models a map's child keys as a List<String> at the map's path
        public List<String> getKeys(String path) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : List.of();
        }
    }
}

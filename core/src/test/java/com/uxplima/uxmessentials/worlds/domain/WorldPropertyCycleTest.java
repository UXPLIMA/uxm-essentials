package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorldPropertyCycleTest {

    private static final List<String> NO_WORLDS = List.of();
    private static final List<String> WORLDS = List.of("a", "b");

    @Test
    void booleanForwardTogglesAndWraps() {
        assertThat(next(WorldProperties.PVP, "true", CycleAction.FORWARD)).isEqualTo("false");
        assertThat(next(WorldProperties.PVP, "false", CycleAction.FORWARD)).isEqualTo("true");
    }

    @Test
    void booleanBackwardTogglesAndWraps() {
        assertThat(next(WorldProperties.PVP, "true", CycleAction.BACKWARD)).isEqualTo("false");
        assertThat(next(WorldProperties.PVP, "false", CycleAction.BACKWARD)).isEqualTo("true");
    }

    @Test
    void enumForwardCyclesSuggestionsInOrderAndWraps() {
        assertThat(next(WorldProperties.DIFFICULTY, "PEACEFUL", CycleAction.FORWARD))
                .isEqualTo("EASY");
        assertThat(next(WorldProperties.DIFFICULTY, "EASY", CycleAction.FORWARD))
                .isEqualTo("NORMAL");
        assertThat(next(WorldProperties.DIFFICULTY, "NORMAL", CycleAction.FORWARD))
                .isEqualTo("HARD");
        assertThat(next(WorldProperties.DIFFICULTY, "HARD", CycleAction.FORWARD))
                .isEqualTo("PEACEFUL");
    }

    @Test
    void enumBackwardReversesAndWraps() {
        assertThat(next(WorldProperties.DIFFICULTY, "PEACEFUL", CycleAction.BACKWARD))
                .isEqualTo("HARD");
        assertThat(next(WorldProperties.DIFFICULTY, "HARD", CycleAction.BACKWARD))
                .isEqualTo("NORMAL");
    }

    @Test
    void enumBlankCurrentStartsFromFirstSuggestion() {
        assertThat(next(WorldProperties.DIFFICULTY, "", CycleAction.FORWARD)).isEqualTo("EASY");
    }

    @Test
    void ticksCycleTheirPresets() {
        assertThat(next(WorldProperties.TIME, "0", CycleAction.FORWARD)).isEqualTo("6000");
        assertThat(next(WorldProperties.TIME, "6000", CycleAction.FORWARD)).isEqualTo("12000");
        assertThat(next(WorldProperties.TIME, "18000", CycleAction.FORWARD)).isEqualTo("0");
        assertThat(next(WorldProperties.TIME, "0", CycleAction.BACKWARD)).isEqualTo("18000");
    }

    @Test
    void integerPropertyCyclesItsPresetsBecauseSuggestionsAreNonEmpty() {
        assertThat(WorldProperties.PLAYER_LIMIT.suggestions()).containsExactly("0", "1", "10", "50");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "0", CycleAction.FORWARD)).isEqualTo("1");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "1", CycleAction.FORWARD)).isEqualTo("10");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "50", CycleAction.FORWARD))
                .isEqualTo("0");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "0", CycleAction.BACKWARD))
                .isEqualTo("50");
    }

    @Test
    void decimalPropertyCyclesItsPresetsBecauseSuggestionsAreNonEmpty() {
        assertThat(WorldProperties.ENTRY_FEE.suggestions()).containsExactly("0", "100", "500", "1000");
        assertThat(next(WorldProperties.ENTRY_FEE, "0", CycleAction.FORWARD)).isEqualTo("100");
        assertThat(next(WorldProperties.ENTRY_FEE, "100", CycleAction.FORWARD)).isEqualTo("500");
        assertThat(next(WorldProperties.ENTRY_FEE, "1000", CycleAction.FORWARD)).isEqualTo("0");
    }

    @Test
    void bigStepBehavesLikeSingleStepWithinSuggestions() {
        assertThat(next(WorldProperties.PLAYER_LIMIT, "0", CycleAction.FORWARD_BIG))
                .isEqualTo("1");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "1", CycleAction.BACKWARD_BIG))
                .isEqualTo("0");
    }

    @Test
    void stringPropertyCyclesProvidedWorldNames() {
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "", CycleAction.FORWARD, WORLDS))
                .isEqualTo("a");
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "a", CycleAction.FORWARD, WORLDS))
                .isEqualTo("b");
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "b", CycleAction.FORWARD, WORLDS))
                .isEqualTo("a");
    }

    @Test
    void stringPropertyBackwardReversesWorldNames() {
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "a", CycleAction.BACKWARD, WORLDS))
                .isEqualTo("b");
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "b", CycleAction.BACKWARD, WORLDS))
                .isEqualTo("a");
    }

    @Test
    void stringPropertyWithEmptyWorldNamesReturnsCurrent() {
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "ghost", CycleAction.FORWARD, NO_WORLDS))
                .isEqualTo("ghost");
    }

    @Test
    void clearOnStringPropertyReturnsEmpty() {
        assertThat(next(WorldProperties.PORTAL_NETHER_LINK, "a", CycleAction.CLEAR, WORLDS))
                .isEqualTo("");
    }

    @Test
    void clearOnSuggestionPropertyReturnsEncodedDefault() {
        assertThat(next(WorldProperties.DIFFICULTY, "HARD", CycleAction.CLEAR)).isEqualTo("NORMAL");
        assertThat(next(WorldProperties.PVP, "false", CycleAction.CLEAR)).isEqualTo("true");
        assertThat(next(WorldProperties.PLAYER_LIMIT, "50", CycleAction.CLEAR)).isEqualTo("0");
    }

    @Test
    void blankCurrentRawIsTreatedAsDefaultForSuggestionCycle() {
        assertThat(next(WorldProperties.PVP, "  ", CycleAction.FORWARD)).isEqualTo("false");
    }

    @Test
    void everyReturnedValueDecodesOrIsEmpty() {
        List<WorldProperty<?>> sample = List.of(
                WorldProperties.PVP,
                WorldProperties.DIFFICULTY,
                WorldProperties.TIME,
                WorldProperties.PLAYER_LIMIT,
                WorldProperties.ENTRY_FEE,
                WorldProperties.PORTAL_NETHER_LINK);
        for (WorldProperty<?> property : sample) {
            for (CycleAction action : CycleAction.values()) {
                String result = WorldPropertyCycle.next(property, "", action, WORLDS);
                assertThat(result.isEmpty() || property.decode(result).isPresent())
                        .as("property %s action %s returned %s", property.key(), action, result)
                        .isTrue();
            }
        }
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the method rejects it at runtime
    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> WorldPropertyCycle.next(null, "true", CycleAction.FORWARD, NO_WORLDS));
        assertThatNullPointerException()
                .isThrownBy(() -> WorldPropertyCycle.next(WorldProperties.PVP, "true", null, NO_WORLDS));
        assertThatNullPointerException()
                .isThrownBy(() -> WorldPropertyCycle.next(WorldProperties.PVP, "true", CycleAction.FORWARD, null));
    }

    private static String next(WorldProperty<?> property, String currentRaw, CycleAction action) {
        return WorldPropertyCycle.next(property, currentRaw, action, NO_WORLDS);
    }

    private static String next(
            WorldProperty<?> property, String currentRaw, CycleAction action, List<String> worldNames) {
        return WorldPropertyCycle.next(property, currentRaw, action, worldNames);
    }
}

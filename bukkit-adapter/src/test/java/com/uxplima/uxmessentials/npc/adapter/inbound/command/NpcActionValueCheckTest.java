package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the add-time value validation for the richer N12 action types: a numeric {@code DELAY}/{@code CHANCE}/
 * {@code COST} and a resolvable {@code GIVE} material are required, while the free-text gates and operator-content
 * effects accept anything. {@code parseType} maps every type word, including the six new ones, case-insensitively.
 */
class NpcActionValueCheckTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock(); // Material.matchMaterial needs a server
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void parseTypeMapsTheNewTypesCaseInsensitively() {
        assertThat(NpcActionValueCheck.parseType("DELAY")).contains(NpcActionType.DELAY);
        assertThat(NpcActionValueCheck.parseType("Random")).contains(NpcActionType.RANDOM);
        assertThat(NpcActionValueCheck.parseType("chance")).contains(NpcActionType.CHANCE);
        assertThat(NpcActionValueCheck.parseType("Permission")).contains(NpcActionType.PERMISSION);
        assertThat(NpcActionValueCheck.parseType("condition")).contains(NpcActionType.CONDITION);
        assertThat(NpcActionValueCheck.parseType("cost")).contains(NpcActionType.COST);
        assertThat(NpcActionValueCheck.parseType("give")).contains(NpcActionType.GIVE);
        assertThat(NpcActionValueCheck.parseType("nope")).isEmpty();
    }

    @Test
    void randomMustBeAPositiveCount() {
        assertThat(NpcActionValueCheck.check(NpcActionType.RANDOM, "3").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.RANDOM, "0").isValid())
                .isFalse();
        assertThat(NpcActionValueCheck.check(NpcActionType.RANDOM, "-2").isValid())
                .isFalse();
        assertThat(NpcActionValueCheck.check(NpcActionType.RANDOM, "many").isValid())
                .isFalse();
    }

    @Test
    void giveAcceptsASerializedItemToken() {
        // A b64: token (what 'give hand' stores) is accepted as-is — its shape is the codec's concern, not the check.
        assertThat(NpcActionValueCheck.check(NpcActionType.GIVE, "b64:whatever").isValid())
                .isTrue();
    }

    @Test
    void delayMustBeAWholeNumber() {
        assertThat(NpcActionValueCheck.check(NpcActionType.DELAY, "40").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.DELAY, "-1").isValid())
                .isFalse();
        assertThat(NpcActionValueCheck.check(NpcActionType.DELAY, "abc").isValid())
                .isFalse();
    }

    @Test
    void chanceMustBeAPercent() {
        assertThat(NpcActionValueCheck.check(NpcActionType.CHANCE, "25").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.CHANCE, "25.0").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.CHANCE, "150").isValid())
                .isFalse();
        assertThat(NpcActionValueCheck.check(NpcActionType.CHANCE, "nope").isValid())
                .isFalse();
    }

    @Test
    void costMustBeANonNegativeNumber() {
        assertThat(NpcActionValueCheck.check(NpcActionType.COST, "50").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.COST, "-5").isValid())
                .isFalse();
        assertThat(NpcActionValueCheck.check(NpcActionType.COST, "free").isValid())
                .isFalse();
    }

    @Test
    void giveMustNameAKnownMaterial() {
        assertThat(NpcActionValueCheck.check(NpcActionType.GIVE, "DIAMOND").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.GIVE, "diamond:3").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.GIVE, "NOT_A_REAL_MATERIAL")
                        .isValid())
                .isFalse();
    }

    @Test
    void freeTextGatesAndEffectsAcceptAnything() {
        assertThat(NpcActionValueCheck.check(NpcActionType.PERMISSION, "anything at all")
                        .isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.CONDITION, "garbage").isValid())
                .isTrue();
        assertThat(NpcActionValueCheck.check(NpcActionType.MESSAGE, "<red>hi").isValid())
                .isTrue();
    }
}

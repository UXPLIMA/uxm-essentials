package com.uxplima.uxmessentials.shared.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ConditionParserTest {

    private static ConditionContext ctx(String world, String gamemode, Map<String, String> placeholders) {
        Function<String, String> resolve = s -> placeholders.getOrDefault(s, s);
        return new ConditionContext("uxmessentials.vip"::equals, world, gamemode, resolve);
    }

    @Test
    void blankAndNullParseToAlways() {
        assertThat(ConditionParser.parse(null)).isInstanceOf(DisplayCondition.Always.class);
        assertThat(ConditionParser.parse("")).isInstanceOf(DisplayCondition.Always.class);
        assertThat(ConditionParser.parse("   ")).isInstanceOf(DisplayCondition.Always.class);
    }

    @Test
    void permissionForm() {
        DisplayCondition c = ConditionParser.parse("permission:uxmessentials.vip");
        assertThat(c).isInstanceOf(DisplayCondition.Permission.class);
        assertThat(((DisplayCondition.Permission) c).node()).isEqualTo("uxmessentials.vip");
    }

    @Test
    void worldForm() {
        DisplayCondition c = ConditionParser.parse("world:nether");
        assertThat(c).isInstanceOf(DisplayCondition.World.class);
        assertThat(((DisplayCondition.World) c).name()).isEqualTo("nether");
    }

    @Test
    void gamemodeForm() {
        DisplayCondition c = ConditionParser.parse("gamemode:creative");
        assertThat(c).isInstanceOf(DisplayCondition.Gamemode.class);
        assertThat(((DisplayCondition.Gamemode) c).mode()).isEqualTo("creative");
    }

    @Test
    void comparisonFormsParseToTheRightOperator() {
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% >= 5")).op())
                .isEqualTo(DisplayCondition.Operator.GREATER_OR_EQUAL);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% <= 5")).op())
                .isEqualTo(DisplayCondition.Operator.LESS_OR_EQUAL);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% != 5")).op())
                .isEqualTo(DisplayCondition.Operator.NOT_EQUALS);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% > 5")).op())
                .isEqualTo(DisplayCondition.Operator.GREATER);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% < 5")).op())
                .isEqualTo(DisplayCondition.Operator.LESS);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%n% = 5")).op())
                .isEqualTo(DisplayCondition.Operator.EQUALS);
        assertThat(((DisplayCondition.Compare) ConditionParser.parse("%rank% contains MVP")).op())
                .isEqualTo(DisplayCondition.Operator.CONTAINS);
    }

    @Test
    void comparisonCapturesTrimmedOperands() {
        DisplayCondition.Compare c = (DisplayCondition.Compare) ConditionParser.parse("%balance% >= 100");
        assertThat(c.left()).isEqualTo("%balance%");
        assertThat(c.right()).isEqualTo("100");
    }

    @Test
    void leadingBangWrapsInNegate() {
        DisplayCondition c = ConditionParser.parse("!world:nether");
        assertThat(c).isInstanceOf(DisplayCondition.Negate.class);
        DisplayCondition inner = ((DisplayCondition.Negate) c).inner();
        assertThat(inner).isInstanceOf(DisplayCondition.World.class);
        assertThat(((DisplayCondition.World) inner).name()).isEqualTo("nether");
    }

    @Test
    void negatedWorldMatchesCorrectly() {
        DisplayCondition c = ConditionParser.parse("!world:nether");
        assertThat(c.matches(ctx("world", "SURVIVAL", Map.of()))).isTrue();
        assertThat(c.matches(ctx("nether", "SURVIVAL", Map.of()))).isFalse();
    }

    @Test
    void andListParsesToAll() {
        DisplayCondition c = ConditionParser.parse("permission:uxmessentials.vip && world:world");
        assertThat(c).isInstanceOf(DisplayCondition.All.class);
        assertThat(((DisplayCondition.All) c).conditions()).hasSize(2);
        assertThat(c.matches(ctx("world", "SURVIVAL", Map.of()))).isTrue();
        assertThat(c.matches(ctx("nether", "SURVIVAL", Map.of()))).isFalse();
    }

    @Test
    void commaListParsesToAll() {
        DisplayCondition c = ConditionParser.parse("world:world, gamemode:survival");
        assertThat(c).isInstanceOf(DisplayCondition.All.class);
        assertThat(((DisplayCondition.All) c).conditions()).hasSize(2);
    }

    @Test
    void orListParsesToAny() {
        DisplayCondition c = ConditionParser.parse("world:nether || world:world");
        assertThat(c).isInstanceOf(DisplayCondition.Any.class);
        assertThat(((DisplayCondition.Any) c).conditions()).hasSize(2);
        assertThat(c.matches(ctx("world", "SURVIVAL", Map.of()))).isTrue();
        assertThat(c.matches(ctx("the_end", "SURVIVAL", Map.of()))).isFalse();
    }

    @Test
    void unparseableGarbageBecomesNever() {
        DisplayCondition c = ConditionParser.parse("@@@ not a real condition @@@");
        assertThat(c).isInstanceOf(DisplayCondition.Never.class);
        assertThat(c.matches(ctx("world", "SURVIVAL", Map.of()))).isFalse();
    }

    @Test
    void unknownPrefixBecomesNever() {
        DisplayCondition c = ConditionParser.parse("biome:plains");
        assertThat(c).isInstanceOf(DisplayCondition.Never.class);
    }

    @Test
    void emptyPrefixValueBecomesNever() {
        assertThat(ConditionParser.parse("world:")).isInstanceOf(DisplayCondition.Never.class);
    }
}

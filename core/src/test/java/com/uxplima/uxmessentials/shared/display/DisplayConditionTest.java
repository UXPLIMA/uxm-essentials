package com.uxplima.uxmessentials.shared.display;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class DisplayConditionTest {

    private static ConditionContext ctx(
            Predicate<String> hasPermission, String world, String gamemode, Map<String, String> placeholders) {
        Function<String, String> resolve = s -> placeholders.getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, gamemode, resolve);
    }

    private static ConditionContext plainCtx() {
        return ctx(node -> false, "world", "SURVIVAL", Map.of());
    }

    @Test
    void alwaysIsTrue() {
        assertThat(DisplayCondition.always().matches(plainCtx())).isTrue();
    }

    @Test
    void neverIsFalse() {
        assertThat(DisplayCondition.never().matches(plainCtx())).isFalse();
    }

    @Test
    void permissionUsesTheContextPredicate() {
        Set<String> held = Set.of("uxmessentials.vip");
        ConditionContext c = ctx(held::contains, "world", "SURVIVAL", Map.of());
        assertThat(new DisplayCondition.Permission("uxmessentials.vip").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Permission("uxmessentials.admin").matches(c))
                .isFalse();
    }

    @Test
    void worldMatchesCaseInsensitively() {
        ConditionContext c = ctx(n -> false, "World_Nether", "SURVIVAL", Map.of());
        assertThat(new DisplayCondition.World("world_nether").matches(c)).isTrue();
        assertThat(new DisplayCondition.World("WORLD_NETHER").matches(c)).isTrue();
        assertThat(new DisplayCondition.World("world_the_end").matches(c)).isFalse();
    }

    @Test
    void gamemodeMatchesCaseInsensitively() {
        ConditionContext c = ctx(n -> false, "world", "Creative", Map.of());
        assertThat(new DisplayCondition.Gamemode("creative").matches(c)).isTrue();
        assertThat(new DisplayCondition.Gamemode("CREATIVE").matches(c)).isTrue();
        assertThat(new DisplayCondition.Gamemode("survival").matches(c)).isFalse();
    }

    @Test
    void compareNumericOrdering() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of("%balance%", "150"));
        assertThat(new DisplayCondition.Compare("%balance%", DisplayCondition.Operator.GREATER_OR_EQUAL, "100")
                        .matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%balance%", DisplayCondition.Operator.GREATER_OR_EQUAL, "150")
                        .matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%balance%", DisplayCondition.Operator.LESS, "150").matches(c))
                .isFalse();
        assertThat(new DisplayCondition.Compare("%balance%", DisplayCondition.Operator.LESS, "200").matches(c))
                .isTrue();
    }

    @Test
    void compareNumericEquality() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of("%n%", "5"));
        assertThat(new DisplayCondition.Compare("%n%", DisplayCondition.Operator.EQUALS, "5.0").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%n%", DisplayCondition.Operator.NOT_EQUALS, "6").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%n%", DisplayCondition.Operator.NOT_EQUALS, "5").matches(c))
                .isFalse();
    }

    @Test
    void compareStringEqualityAndContains() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of("%rank%", "MVP+"));
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.EQUALS, "MVP+").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.EQUALS, "mvp+").matches(c))
                .isFalse();
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.CONTAINS, "MVP").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.CONTAINS, "VIP").matches(c))
                .isFalse();
    }

    @Test
    void compareOrderingOnNonNumberIsFalse() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of("%rank%", "gold"));
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.GREATER, "silver").matches(c))
                .isFalse();
        assertThat(new DisplayCondition.Compare("%rank%", DisplayCondition.Operator.LESS_OR_EQUAL, "silver").matches(c))
                .isFalse();
    }

    @Test
    void compareLeavesLiteralsWhenNoPlaceholderMapping() {
        // The fake resolver returns the input unchanged for unmapped tokens, so literals compare directly.
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of());
        assertThat(new DisplayCondition.Compare("42", DisplayCondition.Operator.GREATER, "7").matches(c))
                .isTrue();
        assertThat(new DisplayCondition.Compare("foo", DisplayCondition.Operator.EQUALS, "foo").matches(c))
                .isTrue();
    }

    @Test
    void negateFlipsInner() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of());
        assertThat(new DisplayCondition.Negate(DisplayCondition.always()).matches(c))
                .isFalse();
        assertThat(new DisplayCondition.Negate(DisplayCondition.never()).matches(c))
                .isTrue();
    }

    @Test
    void allRequiresEveryMember() {
        ConditionContext c = ctx("uxmessentials.vip"::equals, "world", "SURVIVAL", Map.of());
        DisplayCondition both = new DisplayCondition.All(
                List.of(new DisplayCondition.Permission("uxmessentials.vip"), new DisplayCondition.World("world")));
        DisplayCondition oneFails = new DisplayCondition.All(
                List.of(new DisplayCondition.Permission("uxmessentials.vip"), new DisplayCondition.World("nether")));
        assertThat(both.matches(c)).isTrue();
        assertThat(oneFails.matches(c)).isFalse();
        assertThat(new DisplayCondition.All(List.of()).matches(c)).isTrue();
    }

    @Test
    void anyRequiresOneMember() {
        ConditionContext c = ctx(n -> false, "world", "SURVIVAL", Map.of());
        DisplayCondition oneTrue = new DisplayCondition.Any(
                List.of(new DisplayCondition.World("nether"), new DisplayCondition.World("world")));
        DisplayCondition noneTrue = new DisplayCondition.Any(
                List.of(new DisplayCondition.World("nether"), new DisplayCondition.World("the_end")));
        assertThat(oneTrue.matches(c)).isTrue();
        assertThat(noneTrue.matches(c)).isFalse();
        assertThat(new DisplayCondition.Any(List.of()).matches(c)).isFalse();
    }
}

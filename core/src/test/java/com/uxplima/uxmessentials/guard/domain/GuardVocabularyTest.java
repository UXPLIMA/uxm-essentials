package com.uxplima.uxmessentials.guard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The words an operator types: actions, targets, behaviour values and shapes. */
class GuardVocabularyTest {

    @Test
    void aPluginsOwnActionsJoinTheTreeWithTheirParents() {
        ActionTree tree = ActionTree.builtIn().with(List.of(ActionId.of("shop.buy.vip")));

        assertThat(tree.contains(ActionId.of("shop"))).isTrue();
        assertThat(tree.chain(ActionId.of("shop.buy.vip")))
                .containsExactly(ActionId.of("shop.buy.vip"), ActionId.of("shop.buy"), ActionId.of("shop"));
        assertThat(tree.children(ActionId.of("shop"))).containsExactly(ActionId.of("shop.buy"));
    }

    @Test
    void targetsReadInEnglishAndTurkish() {
        assertThat(Target.parse("herkes")).isEqualTo(Target.parse("everyone"));
        assertThat(Target.parse("yabanci")).isEqualTo(Target.parse("nonmember"));
        assertThat(Target.parse("rol:member")).isEqualTo(Target.parse("role:member"));
        assertThat(Target.parse("member")).isEqualTo(Target.parse("role:member"));
        assertThat(Target.parse("???")).isEmpty();
    }

    @Test
    void behaviourValuesAreNormalisedOrRefused() {
        assertThat(BehaviourKey.FLY.normalise("yasak")).contains("deny");
        assertThat(BehaviourKey.GOD.normalise("izin")).contains("allow");
        assertThat(BehaviourKey.INVINCIBLE.normalise("evet")).contains("true");
        assertThat(BehaviourKey.TIME.normalise("night")).contains("13000");
        assertThat(BehaviourKey.HEAL.normalise("2 5")).contains("2.0 5");
        assertThat(BehaviourKey.WALK_SPEED.normalise("3")).isEmpty();
        assertThat(BehaviourKey.GAME_MODE.normalise("hardcore")).isEmpty();
        assertThat(BehaviourKey.COMMANDS_DENY.normalise("home, tpa  home")).contains("home,tpa");
    }

    @Test
    void shapesContainWhatTheyCover() {
        Shape triangle = Shape.Polygon.of(new int[] {0, 10, 0}, new int[] {0, 0, 10}, 0, 10);
        Shape column = new Shape.Cylinder(0, 0, 5, 5, 0, 10);
        Shape ball = new Shape.Sphere(0, 50, 0, 4);
        Shape chunks = Shape.ChunkSet.of(Set.of(Shape.chunkKey(0, 0)), 0, 100);

        assertThat(triangle.contains(2, 5, 2)).isTrue();
        assertThat(triangle.contains(9, 5, 9)).isFalse();
        assertThat(triangle.contains(2, 11, 2)).isFalse();
        assertThat(column.contains(3, 5, 3)).isTrue();
        assertThat(column.contains(5, 5, 5)).isFalse();
        assertThat(ball.contains(0, 54, 0)).isTrue();
        assertThat(ball.contains(3, 53, 3)).isFalse();
        assertThat(chunks.contains(15, 50, 15)).isTrue();
        assertThat(chunks.contains(16, 50, 15)).isFalse();
        assertThat(new Shape.Global().contains(1_000_000, -64, -1_000_000)).isTrue();
    }

    @Test
    void anInnerLayerOfBehavioursWinsKeyByKey() {
        Behaviours outer = Behaviours.empty().with(BehaviourKey.FLY, "deny").with(BehaviourKey.TIME, "day");
        Behaviours inner = Behaviours.empty().with(BehaviourKey.FLY, "allow");

        Behaviours merged = outer.overlay(inner);

        assertThat(merged.get(BehaviourKey.FLY)).contains("allow");
        assertThat(merged.get(BehaviourKey.TIME)).contains("1000");
    }
}

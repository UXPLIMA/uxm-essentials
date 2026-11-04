package com.uxplima.uxmessentials.guard.domain.engine;

import static com.uxplima.uxmessentials.guard.GuardWorlds.action;
import static com.uxplima.uxmessentials.guard.GuardWorlds.compile;
import static com.uxplima.uxmessentials.guard.GuardWorlds.cuboid;
import static com.uxplima.uxmessentials.guard.GuardWorlds.player;
import static com.uxplima.uxmessentials.guard.GuardWorlds.rule;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.guard.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import org.junit.jupiter.api.Test;

class GuardEngineTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static Verdict check(WorldIndex index, int x, int y, int z, String act, GuardSubject who) {
        return index.check(x, y, z, action(act), who);
    }

    @Test
    void aNewRegionDecidesNothing() {
        WorldIndex index = compile(List.of(cuboid("spawn", 0, 0, 0, 10, 10, 10)));

        assertThat(check(index, 5, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.UNSET);
    }

    @Test
    void aNonmemberRuleStopsStrangersButNotMembers() {
        Region home = cuboid("home", 0, 0, 0, 10, 10, 10)
                .withRule(rule("break", "nonmember", Effect.DENY))
                .withRole(MEMBER, RoleName.MEMBER);
        WorldIndex index = compile(List.of(home));

        assertThat(check(index, 5, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 5, 5, 5, "break", player(MEMBER))).isEqualTo(Verdict.UNSET);
        assertThat(check(index, 50, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.UNSET);
    }

    @Test
    void aRoleRuleIsMoreSpecificThanAnEveryoneRule() {
        Region arena = cuboid("arena", 0, 0, 0, 10, 10, 10)
                .withRule(rule("place", "everyone", Effect.DENY))
                .withRule(rule("place", "role:member", Effect.ALLOW))
                .withRole(MEMBER, RoleName.MEMBER);
        WorldIndex index = compile(List.of(arena));

        assertThat(check(index, 1, 1, 1, "place", player(MEMBER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 1, 1, 1, "place", player(STRANGER))).isEqualTo(Verdict.DENY);
    }

    @Test
    void denyWinsBetweenRulesOfTheSameKind() {
        Region shop = cuboid("shop", 0, 0, 0, 10, 10, 10)
                .withRule(rule("use", "perm:shop.use", Effect.ALLOW))
                .withRule(rule("use", "perm:shop.banned", Effect.DENY));
        WorldIndex index = compile(List.of(shop));

        GuardSubject both = player(STRANGER, Set.of("shop.use", "shop.banned"), Set.of());
        GuardSubject allowed = player(STRANGER, Set.of("shop.use"), Set.of());
        assertThat(check(index, 1, 1, 1, "use", both)).isEqualTo(Verdict.DENY);
        assertThat(check(index, 1, 1, 1, "use", allowed)).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void aChildActionFollowsItsParentUntilItHasARuleOfItsOwn() {
        Region town = cuboid("town", 0, 0, 0, 10, 10, 10)
                .withRule(rule("use", "everyone", Effect.DENY))
                .withRule(rule("use.door", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(town));

        assertThat(check(index, 1, 1, 1, "use.container", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 1, 1, 1, "use.door", player(STRANGER))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void theHigherPriorityRegionDecides() {
        Region outer =
                cuboid("outer", 0, 0, 0, 30, 30, 30).withPriority(10).withRule(rule("break", "everyone", Effect.ALLOW));
        Region inner = cuboid("inner", 5, 5, 5, 10, 10, 10).withRule(rule("break", "everyone", Effect.DENY));
        WorldIndex index = compile(List.of(outer, inner));

        assertThat(check(index, 7, 7, 7, "break", player(STRANGER))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void theInnerRegionDecidesAtEqualPriority() {
        Region outer = cuboid("outer", 0, 0, 0, 30, 30, 30).withRule(rule("break", "everyone", Effect.DENY));
        Region inner = cuboid("inner", 5, 5, 5, 10, 10, 10).withRule(rule("break", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(outer, inner));

        assertThat(check(index, 7, 7, 7, "break", player(STRANGER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 20, 20, 20, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
    }

    @Test
    void crossingRegionsOfEqualPriorityDenyTogether() {
        Region west = cuboid("west", 0, 0, 0, 10, 10, 10).withRule(rule("break", "everyone", Effect.ALLOW));
        Region east = cuboid("east", 5, 0, 0, 15, 10, 10).withRule(rule("break", "everyone", Effect.DENY));
        WorldIndex index = compile(List.of(west, east));

        assertThat(check(index, 7, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 2, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 13, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
    }

    @Test
    void aRegionWithoutARuleLetsTheNextOneDecide() {
        Region outer = cuboid("outer", 0, 0, 0, 30, 30, 30).withRule(rule("break", "everyone", Effect.DENY));
        Region inner = cuboid("inner", 5, 5, 5, 10, 10, 10).withRule(rule("place", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(outer, inner));

        assertThat(check(index, 7, 7, 7, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 7, 7, 7, "place", player(STRANGER))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void worldDefaultsDecideOnlyWhereNoRegionDoes() {
        Region garden = cuboid("garden", 0, 0, 0, 10, 10, 10).withRule(rule("break", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(garden), List.of(rule("break", "everyone", Effect.DENY)), Map.of(), null);

        assertThat(check(index, 5, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 500, 70, -500, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
    }

    @Test
    void aChildInheritsItsParentsRulesAndOverridesTheSameSlot() {
        Region parent = cuboid("city", 0, 0, 0, 100, 100, 100)
                .withRule(rule("break", "everyone", Effect.DENY))
                .withRule(rule("place", "everyone", Effect.DENY));
        Region child = cuboid("plot", 200, 0, 0, 210, 10, 10)
                .withParent(RegionId.of("city"))
                .withRule(rule("place", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(parent, child));

        assertThat(check(index, 205, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 205, 5, 5, "place", player(STRANGER))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void aTemplateSitsUnderTheRegionsOwnRules() {
        Template spawn = new Template(
                "spawn",
                List.of(rule("break", "everyone", Effect.DENY), rule("use", "everyone", Effect.DENY)),
                Behaviours.empty());
        Region hub = cuboid("hub", 0, 0, 0, 10, 10, 10)
                .withTemplate("spawn")
                .withRule(rule("use", "everyone", Effect.ALLOW));
        WorldIndex index = compile(List.of(hub), List.of(), Map.of("spawn", spawn), null);

        assertThat(check(index, 5, 5, 5, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 5, 5, 5, "use", player(STRANGER))).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void permissionAndConditionTargetsAskTheSubject() {
        Region vip = cuboid("vip", 0, 0, 0, 10, 10, 10)
                .withRule(rule("move.enter", "everyone", Effect.DENY))
                .withRule(rule("move.enter", "perm:vip.enter", Effect.ALLOW))
                .withRule(rule("break", "everyone", Effect.DENY))
                .withRule(rule("break", "cond:%level% >= 10", Effect.ALLOW));
        WorldIndex index = compile(List.of(vip));

        assertThat(check(index, 1, 1, 1, "move.enter", player(STRANGER, Set.of("vip.enter"), Set.of())))
                .isEqualTo(Verdict.ALLOW);
        assertThat(check(index, 1, 1, 1, "move.enter", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(check(index, 1, 1, 1, "break", player(STRANGER, Set.of(), Set.of("%level% >= 10"))))
                .isEqualTo(Verdict.ALLOW);
    }

    @Test
    void natureSeesOnlyEveryoneRules() {
        Region farm = cuboid("farm", 0, 0, 0, 10, 10, 10)
                .withRule(rule("world.fire", "nonmember", Effect.DENY))
                .withRule(rule("world.liquid", "everyone", Effect.DENY));
        WorldIndex index = compile(List.of(farm));

        assertThat(check(index, 1, 1, 1, "world.fire.spread", GuardSubject.WORLD))
                .isEqualTo(Verdict.UNSET);
        assertThat(check(index, 1, 1, 1, "world.liquid.water", GuardSubject.WORLD))
                .isEqualTo(Verdict.DENY);
    }

    @Test
    void anOwnerIsNotAMemberUnlessGivenThatRole() {
        Region house = cuboid("house", 0, 0, 0, 10, 10, 10)
                .withRule(rule("use", "role:member", Effect.ALLOW))
                .withRule(rule("use", "everyone", Effect.DENY))
                .withRole(OWNER, RoleName.OWNER);
        WorldIndex index = compile(List.of(house));

        assertThat(check(index, 1, 1, 1, "use", player(OWNER))).isEqualTo(Verdict.DENY);
    }

    @Test
    void aLockedWorldRefusesEverythingAndLetsNothingCross() {
        WorldIndex index = GuardWorlds.locked();

        assertThat(check(index, 0, 64, 0, "break", player(STRANGER))).isEqualTo(Verdict.DENY);
        assertThat(index.reachable(0, 64, 0, 1, 64, 0)).isFalse();
    }

    @Test
    void effectsCrossIntoARegionOnlyFromItsOwnFamily() {
        Region a = cuboid("a", 0, 0, 0, 10, 10, 10).withFamily("farms");
        Region b = cuboid("b", 11, 0, 0, 20, 10, 10).withFamily("farms");
        Region c = cuboid("c", 21, 0, 0, 30, 10, 10);
        WorldIndex index = compile(List.of(a, b, c));

        assertThat(index.reachable(10, 5, 5, 11, 5, 5)).isTrue();
        assertThat(index.reachable(-5, 5, 5, 1, 5, 5)).isFalse();
        assertThat(index.reachable(20, 5, 5, 21, 5, 5)).isFalse();
        assertThat(index.reachable(5, 5, 5, -5, 5, 5)).isTrue();
    }

    @Test
    void anActionNoRegionNamesIsAnsweredWithoutLookingAtTheWorld() {
        Region box = cuboid("box", 0, 0, 0, 10, 10, 10).withRule(rule("break", "everyone", Effect.DENY));
        WorldIndex index = compile(List.of(box));

        assertThat(index.sets(action("break"))).isTrue();
        assertThat(index.sets(action("damage.player"))).isFalse();
    }
}

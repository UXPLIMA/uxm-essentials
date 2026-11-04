package com.uxplima.uxmessentials.guard.domain.engine;

import static com.uxplima.uxmessentials.guard.GuardWorlds.TREE;
import static com.uxplima.uxmessentials.guard.GuardWorlds.compile;
import static com.uxplima.uxmessentials.guard.GuardWorlds.player;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.guard.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The compiled engine (section cells, interned sets, rule tables, column reuse) against the rules read the slow
 * way: every region that contains the point, highest priority first, each walking the action up to its root. Worlds
 * are random but seeded, so a failure names the seed that reproduces it.
 */
class GuardEngineAgreesWithBruteForceTest {

    private static final int WORLDS = 150;
    private static final int POINTS = 400;
    private static final int SPAN = 24;
    private static final List<String> ACTIONS = List.of(
            "break",
            "place",
            "use",
            "use.door",
            "use.container",
            "damage",
            "damage.player",
            "item.use",
            "item.use.ender-pearl",
            "move.enter",
            "world.fire.spread",
            "world.liquid.water",
            "world");
    private static final List<String> TARGETS =
            List.of("everyone", "nonmember", "role:member", "role:owner", "role:guest", "perm:a", "perm:b", "cond:x");
    private static final UUID[] PLAYERS = {
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        UUID.fromString("00000000-0000-0000-0000-000000000003"),
        UUID.fromString("00000000-0000-0000-0000-000000000004")
    };
    private static final RoleName[] ROLES = {RoleName.OWNER, RoleName.MEMBER, RoleName.GUEST};

    @Test
    void everyDecisionMatchesTheRulesReadOneByOne() {
        for (long seed = 1; seed <= WORLDS; seed++) {
            Random random = new Random(seed);
            List<Region> regions = regions(random);
            List<Rule> defaults = random.nextInt(3) == 0 ? List.of(rule(random, "everyone")) : List.of();
            WorldIndex index = compile(regions, defaults, Map.of(), null);
            agree(seed, random, index, regions, defaults);

            // Edit one region and compile again on top of the old index: columns away from the edit are reused,
            // and must still answer for the new world.
            List<Region> edited = new ArrayList<>(regions);
            if (!edited.isEmpty()) {
                int at = random.nextInt(edited.size());
                edited.set(
                        at,
                        edited.get(at)
                                .withRule(rule(random, TARGETS.get(random.nextInt(TARGETS.size()))))
                                .withShape(shape(random)));
            }
            WorldIndex next = compile(edited, defaults, Map.of(), index);
            agree(seed, random, next, edited, defaults);
        }
    }

    private static void agree(long seed, Random random, WorldIndex index, List<Region> regions, List<Rule> defaults) {
        List<GuardSubject> subjects = subjects();
        for (int i = 0; i < POINTS; i++) {
            // Most points inside some region, where overlaps and edges are; the rest anywhere nearby.
            int x;
            int y;
            int z;
            if (!regions.isEmpty() && random.nextInt(10) < 8) {
                Bounds b = regions.get(random.nextInt(regions.size())).shape().bounds();
                x = b.minX() - 2 + random.nextInt(b.maxX() - b.minX() + 5);
                y = b.minY() - 2 + random.nextInt(Math.min(200, b.maxY() - b.minY()) + 5);
                z = b.minZ() - 2 + random.nextInt(b.maxZ() - b.minZ() + 5);
            } else {
                x = random.nextInt(SPAN * 4) - SPAN * 2;
                y = random.nextInt(SPAN * 3) - 10;
                z = random.nextInt(SPAN * 4) - SPAN * 2;
            }
            String act = ACTIONS.get(random.nextInt(ACTIONS.size()));
            GuardSubject who = subjects.get(random.nextInt(subjects.size()));
            Verdict expected = bruteForce(regions, defaults, x, y, z, ActionId.of(act), who);
            Verdict actual = index.check(x, y, z, TREE.indexOf(ActionId.of(act)), who);
            assertThat(actual)
                    .as("seed %d, %s at %d %d %d for %s", seed, act, x, y, z, who)
                    .isEqualTo(expected);
        }
    }

    // ---- the slow reading ---------------------------------------------------------------------------------------

    private static Verdict bruteForce(
            List<Region> regions, List<Rule> defaults, int x, int y, int z, ActionId act, GuardSubject who) {
        List<Region> covering = new ArrayList<>();
        for (Region region : regions) {
            if (region.shape().contains(x, y, z)) {
                covering.add(region);
            }
        }
        covering.sort(Comparator.comparingInt(Region::priority).reversed());
        for (Region region : covering) {
            Verdict verdict = decide(region.rules(), region, act, who);
            if (verdict != Verdict.UNSET) {
                return verdict;
            }
        }
        return decide(defaults, null, act, who);
    }

    private static Verdict decide(List<Rule> rules, @Nullable Region region, ActionId act, GuardSubject who) {
        for (ActionId node = act; node != null; node = node.parent()) {
            int best = Integer.MAX_VALUE;
            boolean deny = false;
            for (Rule rule : rules) {
                if (!rule.action().equals(node) || !matches(rule.target(), region, who)) {
                    continue;
                }
                int rank = rank(rule.target());
                if (rank < best) {
                    best = rank;
                    deny = rule.effect() == Effect.DENY;
                } else if (rank == best && rule.effect() == Effect.DENY) {
                    deny = true;
                }
            }
            if (best != Integer.MAX_VALUE) {
                return deny ? Verdict.DENY : Verdict.ALLOW;
            }
        }
        return Verdict.UNSET;
    }

    private static boolean matches(Target target, @Nullable Region region, GuardSubject who) {
        UUID id = who.id();
        Optional<RoleName> role = id == null || region == null ? Optional.empty() : region.roleOf(id);
        return switch (target) {
            case Target.Everyone e -> true;
            case Target.NonMember n -> id != null && role.isEmpty();
            case Target.Role r -> role.filter(r.role()::equals).isPresent();
            case Target.Permission p -> id != null && who.has(p.node());
            case Target.Condition c -> id != null && who.meets(c.expression());
        };
    }

    private static int rank(Target target) {
        return switch (target) {
            case Target.Everyone e -> 2;
            case Target.Permission p -> 1;
            case Target.Condition c -> 1;
            default -> 0;
        };
    }

    // ---- random worlds ------------------------------------------------------------------------------------------

    private static List<Region> regions(Random random) {
        int count = random.nextInt(10);
        List<Region> regions = new ArrayList<>();
        // Distinct priorities: the slow reading orders by priority alone; ties have their own tests.
        List<Integer> priorities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            priorities.add(i * 3 - 10);
        }
        Collections.shuffle(priorities, random);
        for (int i = 0; i < count; i++) {
            Region region = GuardWorlds.region("r" + i, shape(random)).withPriority(priorities.get(i));
            int rules = 2 + random.nextInt(8);
            for (int j = 0; j < rules; j++) {
                region = region.withRule(rule(random, TARGETS.get(random.nextInt(TARGETS.size()))));
            }
            for (UUID id : PLAYERS) {
                if (random.nextInt(3) == 0) {
                    region = region.withRole(id, ROLES[random.nextInt(ROLES.length)]);
                }
            }
            regions.add(region);
        }
        return regions;
    }

    private static Shape shape(Random random) {
        int x = random.nextInt(SPAN * 2) - SPAN;
        int y = random.nextInt(SPAN) - 10;
        int z = random.nextInt(SPAN * 2) - SPAN;
        return switch (random.nextInt(4)) {
            case 0 ->
                new Shape.Cylinder(x, z, 1 + random.nextInt(20), 1 + random.nextInt(20), y, y + random.nextInt(30));
            case 1 -> new Shape.Sphere(x, y, z, 1 + random.nextInt(18));
            case 2 ->
                Shape.Polygon.of(
                        new int[] {x, x + 5 + random.nextInt(30), x + random.nextInt(20)},
                        new int[] {z, z + random.nextInt(10), z + 5 + random.nextInt(30)},
                        y,
                        y + random.nextInt(40));
            default ->
                new Shape.Cuboid(
                        Bounds.of(x, y, z, x + random.nextInt(45), y + random.nextInt(40), z + random.nextInt(45)));
        };
    }

    private static Rule rule(Random random, String target) {
        return new Rule(
                ActionId.of(ACTIONS.get(random.nextInt(ACTIONS.size()))),
                Target.parse(target).orElseThrow(),
                random.nextBoolean() ? Effect.ALLOW : Effect.DENY);
    }

    private static List<GuardSubject> subjects() {
        List<GuardSubject> out = new ArrayList<>();
        out.add(GuardSubject.WORLD);
        out.add(player(PLAYERS[0]));
        out.add(player(PLAYERS[1], Set.of("a"), Set.of()));
        out.add(player(PLAYERS[2], Set.of("a", "b"), Set.of("x")));
        out.add(player(PLAYERS[3], Set.of(), Set.of("x")));
        return out;
    }
}

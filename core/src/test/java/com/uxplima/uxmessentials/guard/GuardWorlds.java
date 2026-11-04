package com.uxplima.uxmessentials.guard;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.guard.domain.engine.Compiler;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import org.jspecify.annotations.Nullable;

/** Small builders for guard tests: regions, rules, subjects and compiled worlds. */
public final class GuardWorlds {

    public static final String WORLD = "world";
    public static final GuardScope SCOPE = GuardScope.server("test");
    public static final ActionTree TREE = ActionTree.builtIn();
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;

    private GuardWorlds() {}

    /** A cuboid region with nothing set. */
    public static Region cuboid(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        return Region.create(
                new RegionKey(WORLD, RegionId.of(id)), SCOPE, new Shape.Cuboid(Bounds.of(x1, y1, z1, x2, y2, z2)));
    }

    /** A region of any shape with nothing set. */
    public static Region region(String id, Shape shape) {
        return Region.create(new RegionKey(WORLD, RegionId.of(id)), SCOPE, shape);
    }

    /** A rule from its three words, the way the config writes it. */
    public static Rule rule(String action, String target, Effect effect) {
        return new Rule(ActionId.of(action), Target.parse(target).orElseThrow(), effect);
    }

    /** Compile one world with no defaults. */
    public static WorldIndex compile(Collection<Region> regions) {
        return compile(regions, List.of(), Map.of(), null);
    }

    /** Compile one world with default rules, templates and an index to reuse. */
    public static WorldIndex compile(
            Collection<Region> regions,
            List<Rule> defaults,
            Map<String, Template> templates,
            @Nullable WorldIndex previous) {
        return Compiler.compile(
                TREE,
                new Compiler.Input(WORLD, MIN_Y, MAX_Y, regions, defaults, Behaviours.empty(), false),
                templates,
                previous,
                previous == null ? null : TREE);
    }

    /** Compile a world whose regions could not be loaded. */
    public static WorldIndex locked() {
        return Compiler.compile(
                TREE,
                new Compiler.Input(WORLD, MIN_Y, MAX_Y, List.of(), List.of(), Behaviours.empty(), true),
                Map.of(),
                null,
                null);
    }

    /** A player with an id and the given permission nodes and conditions. */
    public static GuardSubject player(UUID id, Set<String> nodes, Set<String> conditions) {
        return new GuardSubject() {
            @Override
            public UUID id() {
                return id;
            }

            @Override
            public boolean has(String node) {
                return nodes.contains(node);
            }

            @Override
            public boolean meets(String expression) {
                return conditions.contains(expression);
            }

            @Override
            public String toString() {
                return "player " + id + " " + nodes + " " + conditions;
            }
        };
    }

    /** A player with no permissions. */
    public static GuardSubject player(UUID id) {
        return player(id, Set.of(), Set.of());
    }

    /** The index of an action in the shared tree. */
    public static int action(String id) {
        return TREE.indexOf(ActionId.of(id));
    }
}

package com.uxplima.uxmessentials.guard.domain.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Verdict;

/**
 * Everything the listeners read, as one immutable value swapped atomically on every change. A world with no
 * index has no regions: every check answers {@link Verdict#UNSET}.
 *
 * @param tree the action tree the indexes were compiled against
 * @param worlds one index per world name
 * @param generation increases with every swap, for status and tests
 */
public record GuardSnapshot(ActionTree tree, Map<String, WorldIndex> worlds, long generation) {

    public GuardSnapshot {
        worlds = Map.copyOf(worlds);
    }

    /** No regions anywhere. */
    public static GuardSnapshot empty(ActionTree tree) {
        return new GuardSnapshot(tree, Map.of(), 0);
    }

    public Optional<WorldIndex> world(String name) {
        return Optional.ofNullable(worlds.get(name));
    }

    /** The decision for an action by id; an unknown action is {@link Verdict#UNSET}. */
    public Verdict check(String world, int x, int y, int z, ActionId action, GuardSubject subject) {
        return check(world, x, y, z, tree.indexOf(action), subject);
    }

    /** The decision for an action by tree index; the hot path. */
    public Verdict check(String world, int x, int y, int z, int action, GuardSubject subject) {
        WorldIndex index = worlds.get(world);
        return index == null ? Verdict.UNSET : index.check(x, y, z, action, subject);
    }

    /** The border test in one world; a world without an index lets everything cross. */
    public boolean reachable(String world, int sx, int sy, int sz, int tx, int ty, int tz) {
        WorldIndex index = worlds.get(world);
        return index == null || index.reachable(sx, sy, sz, tx, ty, tz);
    }

    /** Explain a decision; a world without an index has nothing to explain. */
    public com.uxplima.uxmessentials.guard.domain.Explanation explain(
            String world, int x, int y, int z, ActionId action, GuardSubject subject) {
        WorldIndex index = worlds.get(world);
        if (index == null) {
            return new com.uxplima.uxmessentials.guard.domain.Explanation(Verdict.UNSET, java.util.List.of(), false);
        }
        return Explainer.explain(tree, index, x, y, z, action, subject);
    }

    /** True when any world sets {@code action}, so its listener family must be registered. */
    public boolean anyWorldSets(int action) {
        for (WorldIndex index : worlds.values()) {
            if (index.locked() || index.sets(action)) {
                return true;
            }
        }
        return false;
    }

    /** This snapshot with one world replaced. */
    public GuardSnapshot with(WorldIndex index) {
        Map<String, WorldIndex> next = new HashMap<>(worlds);
        next.put(index.world(), index);
        return new GuardSnapshot(tree, next, generation + 1);
    }

    /** This snapshot without a world. */
    public GuardSnapshot without(String world) {
        Map<String, WorldIndex> next = new HashMap<>(worlds);
        next.remove(world);
        return new GuardSnapshot(tree, next, generation + 1);
    }
}

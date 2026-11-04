package com.uxplima.uxmessentials.guard.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * Every action the guard knows, as an immutable tree with dense integer indexes.
 *
 * <p>The compiled rule tables are arrays indexed by these numbers, so a check reads one slot instead of walking
 * strings. Every intermediate node exists: adding {@code world.explosion.creeper} adds {@code world} and
 * {@code world.explosion} too. Indexes follow the sorted id order, so a parent always has a smaller index than
 * its children and the same set of ids always gets the same numbers.
 *
 * <p>Other plugins add branches with {@link #with(Collection)}, which returns a new tree; the compiler rebuilds
 * against it.
 */
public final class ActionTree {

    private static final int NO_PARENT = -1;

    private final ActionId[] ids;
    private final int[] parents;
    private final int[] depths;
    private final Map<ActionId, Integer> indexes;

    private ActionTree(Collection<ActionId> seeds) {
        TreeSet<ActionId> all = new TreeSet<>();
        for (ActionId seed : seeds) {
            for (ActionId at = seed; at != null; at = at.parent()) {
                all.add(at);
            }
        }
        this.ids = all.toArray(ActionId[]::new);
        this.parents = new int[ids.length];
        this.depths = new int[ids.length];
        this.indexes = new HashMap<>(ids.length * 2);
        for (int i = 0; i < ids.length; i++) {
            indexes.put(ids[i], i);
        }
        for (int i = 0; i < ids.length; i++) {
            ActionId parent = ids[i].parent();
            parents[i] = parent == null ? NO_PARENT : indexes.get(parent);
            depths[i] = parent == null ? 0 : depths[parents[i]] + 1;
        }
    }

    /** A tree holding exactly these ids and their ancestors. */
    public static ActionTree of(Collection<ActionId> ids) {
        return new ActionTree(Objects.requireNonNull(ids, "ids"));
    }

    /** The tree the guard ships with. */
    public static ActionTree builtIn() {
        return of(BuiltInActions.ALL);
    }

    /** This tree plus the given ids (and their ancestors). */
    public ActionTree with(Collection<ActionId> extra) {
        List<ActionId> merged = new ArrayList<>(List.of(ids));
        merged.addAll(Objects.requireNonNull(extra, "extra"));
        return new ActionTree(merged);
    }

    /** How many nodes the tree has; rule tables are this long. */
    public int size() {
        return ids.length;
    }

    /** The index of {@code id}, or {@code -1} when the tree does not know it. */
    public int indexOf(ActionId id) {
        Integer index = indexes.get(id);
        return index == null ? -1 : index;
    }

    /** True when the tree knows {@code id}. */
    public boolean contains(ActionId id) {
        return indexes.containsKey(id);
    }

    /** The id at {@code index}. */
    public ActionId id(int index) {
        return ids[index];
    }

    /** The parent index of {@code index}, or {@code -1} for a root. */
    public int parent(int index) {
        return parents[index];
    }

    /** How deep {@code index} sits: 0 for a root. */
    public int depth(int index) {
        return depths[index];
    }

    /** The node itself followed by its ancestors up to the root. */
    public List<ActionId> chain(ActionId id) {
        int index = indexOf(id);
        if (index < 0) {
            return List.of();
        }
        List<ActionId> chain = new ArrayList<>();
        for (int at = index; at != NO_PARENT; at = parents[at]) {
            chain.add(ids[at]);
        }
        return List.copyOf(chain);
    }

    /** The direct children of {@code id}, in id order. */
    public List<ActionId> children(@Nullable ActionId id) {
        int parent = id == null ? NO_PARENT : indexOf(id);
        List<ActionId> children = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            if (parents[i] == parent && (id == null || parent >= 0)) {
                children.add(ids[i]);
            }
        }
        return List.copyOf(children);
    }

    /** Every id, parents before children. */
    public List<ActionId> ids() {
        return List.of(ids);
    }

    /** Every index under {@code id} including itself. */
    public int[] subtree(ActionId id) {
        int root = indexOf(id);
        if (root < 0) {
            return new int[0];
        }
        int[] out = new int[ids.length];
        int n = 0;
        for (int i = root; i < ids.length; i++) {
            if (ids[i].isWithin(id)) {
                out[n++] = i;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }
}

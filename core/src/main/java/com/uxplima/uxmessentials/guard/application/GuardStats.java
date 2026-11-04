package com.uxplima.uxmessentials.guard.application;

import com.uxplima.uxmessentials.guard.domain.ActionTree;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters behind {@code /guard status}: checks, denials and time spent per top level action family. Striped
 * adders, so counting from many region threads at once costs no contention.
 */
public final class GuardStats {

    private final ActionTree tree;
    private final int[] familyOf;
    private final String[] families;
    private final LongAdder[] checks;
    private final LongAdder[] denials;
    private final LongAdder[] nanos;
    private final long startedAt = System.nanoTime();

    public GuardStats(ActionTree tree) {
        this.tree = tree;
        this.familyOf = new int[tree.size()];
        List<String> names = new ArrayList<>();
        for (int i = 0; i < tree.size(); i++) {
            int root = i;
            while (tree.parent(root) >= 0) {
                root = tree.parent(root);
            }
            String name = tree.id(root).value();
            int index = names.indexOf(name);
            if (index < 0) {
                names.add(name);
                index = names.size() - 1;
            }
            familyOf[i] = index;
        }
        this.families = names.toArray(String[]::new);
        this.checks = adders(families.length);
        this.denials = adders(families.length);
        this.nanos = adders(families.length);
    }

    /** The tree these counters index; a new tree needs new counters. */
    public ActionTree tree() {
        return tree;
    }

    /** Count one check of {@code action} that took {@code elapsedNanos}. */
    public void record(int action, long elapsedNanos, boolean denied) {
        if (action < 0 || action >= familyOf.length) {
            return;
        }
        int family = familyOf[action];
        checks[family].increment();
        nanos[family].add(elapsedNanos);
        if (denied) {
            denials[family].increment();
        }
    }

    /** The counters per family, most time spent first. */
    public List<Family> families() {
        List<Family> out = new ArrayList<>(families.length);
        for (int i = 0; i < families.length; i++) {
            out.add(new Family(families[i], checks[i].sum(), denials[i].sum(), nanos[i].sum()));
        }
        out.sort(Comparator.comparingLong(Family::nanos).reversed());
        return out;
    }

    /** Seconds since the counters started. */
    public double uptimeSeconds() {
        return Math.max(1e-3, (System.nanoTime() - startedAt) / 1e9);
    }

    /**
     * One family's totals.
     *
     * @param name the root action id
     * @param checks how many checks ran
     * @param denials how many were denied
     * @param nanos total time spent
     */
    public record Family(String name, long checks, long denials, long nanos) {
        /** Average nanoseconds per check. */
        public long averageNanos() {
            return checks == 0 ? 0 : nanos / checks;
        }
    }

    private static LongAdder[] adders(int n) {
        LongAdder[] out = new LongAdder[n];
        for (int i = 0; i < n; i++) {
            out[i] = new LongAdder();
        }
        return out;
    }
}

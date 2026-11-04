package com.uxplima.uxmessentials.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

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
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;

/**
 * {@code ./gradlew :core:guardBench}: nanoseconds per protection check on the compiled engine, against walking every
 * region the slow way, for worlds of growing size. Not a test: it prints, so a change that slows the hot path shows.
 */
public final class GuardBench {

    private static final int CHECKS = 2_000_000;
    private static final int AREA = 6000;
    private static final String[] ACTIONS = {
        "break", "place", "use.door", "use.container", "damage.player", "move.enter", "world.fire.spread"
    };

    private GuardBench() {}

    public static void main(String[] args) {
        System.out.printf(
                Locale.ROOT,
                "%10s %12s %12s %12s %12s %10s%n",
                "regions",
                "compile ms",
                "random ns",
                "players ns",
                "naive ns",
                "columns");
        for (int count : new int[] {10, 100, 1_000, 5_000, 20_000}) {
            run(count);
        }
    }

    private static void run(int count) {
        Random random = new Random(42);
        List<Region> regions = new ArrayList<>();
        UUID member = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(AREA) - AREA / 2;
            int z = random.nextInt(AREA) - AREA / 2;
            int size = 8 + random.nextInt(random.nextInt(10) == 0 ? 400 : 60);
            Region region = GuardWorlds.region("r" + i, new Shape.Cuboid(Bounds.of(x, -64, z, x + size, 200, z + size)))
                    .withPriority(random.nextInt(5))
                    .withRule(new Rule(
                            ActionId.of("break"), Target.parse("nonmember").orElseThrow(), Effect.DENY))
                    .withRule(new Rule(
                            ActionId.of("use"), Target.parse("everyone").orElseThrow(), Effect.DENY))
                    .withRule(new Rule(
                            ActionId.of("use.door"), Target.parse("everyone").orElseThrow(), Effect.ALLOW));
            if (random.nextBoolean()) {
                region = region.withRole(member, RoleName.MEMBER);
            }
            regions.add(region);
        }
        long started = System.nanoTime();
        WorldIndex index = GuardWorlds.compile(regions);
        long compileMs = (System.nanoTime() - started) / 1_000_000;

        GuardSubject who = GuardWorlds.player(member);
        int[] xs = new int[CHECKS];
        int[] ys = new int[CHECKS];
        int[] zs = new int[CHECKS];
        int[] acts = new int[CHECKS];
        for (int i = 0; i < CHECKS; i++) {
            xs[i] = random.nextInt(AREA) - AREA / 2;
            ys[i] = random.nextInt(120);
            zs[i] = random.nextInt(AREA) - AREA / 2;
            acts[i] = GuardWorlds.action(ACTIONS[random.nextInt(ACTIONS.length)]);
        }
        long sink = 0;
        for (int warm = 0; warm < 2; warm++) {
            for (int i = 0; i < CHECKS; i++) {
                sink += index.check(xs[i], ys[i], zs[i], acts[i], who) == Verdict.DENY ? 1 : 0;
            }
        }
        started = System.nanoTime();
        for (int i = 0; i < CHECKS; i++) {
            sink += index.check(xs[i], ys[i], zs[i], acts[i], who) == Verdict.DENY ? 1 : 0;
        }
        double engine = (System.nanoTime() - started) / (double) CHECKS;

        // 100 players, each acting around where they stand and walking on: how a server really asks.
        int[] px = new int[100];
        int[] pz = new int[100];
        for (int p = 0; p < px.length; p++) {
            px[p] = random.nextInt(AREA) - AREA / 2;
            pz[p] = random.nextInt(AREA) - AREA / 2;
        }
        for (int i = 0; i < CHECKS; i++) {
            int p = i % px.length;
            if (i % 2000 < px.length) {
                px[p] += random.nextInt(9) - 4;
                pz[p] += random.nextInt(9) - 4;
            }
            xs[i] = px[p] + random.nextInt(9) - 4;
            zs[i] = pz[p] + random.nextInt(9) - 4;
        }
        for (int i = 0; i < CHECKS; i++) {
            sink += index.check(xs[i], ys[i], zs[i], acts[i], who) == Verdict.DENY ? 1 : 0;
        }
        started = System.nanoTime();
        for (int i = 0; i < CHECKS; i++) {
            sink += index.check(xs[i], ys[i], zs[i], acts[i], who) == Verdict.DENY ? 1 : 0;
        }
        double local = (System.nanoTime() - started) / (double) CHECKS;

        int naiveChecks = Math.max(1_000, CHECKS / Math.max(1, count / 10));
        started = System.nanoTime();
        for (int i = 0; i < naiveChecks; i++) {
            sink += naive(regions, xs[i], ys[i], zs[i]) == Verdict.DENY ? 1 : 0;
        }
        double naive = (System.nanoTime() - started) / (double) naiveChecks;
        System.out.printf(
                Locale.ROOT,
                "%10d %12d %12.1f %12.1f %12.1f %10d%n",
                count,
                compileMs,
                engine,
                local,
                naive,
                index.columnCount());
        if (sink == Long.MIN_VALUE) {
            System.out.println(sink);
        }
    }

    /** Every region asked in turn: what a plugin without an index does on every event. */
    private static Verdict naive(List<Region> regions, int x, int y, int z) {
        Verdict best = Verdict.UNSET;
        int priority = Integer.MIN_VALUE;
        for (Region region : regions) {
            if (region.priority() > priority && region.shape().contains(x, y, z)) {
                priority = region.priority();
                best = region.rules().isEmpty() ? Verdict.UNSET : Verdict.DENY;
            }
        }
        return best;
    }
}

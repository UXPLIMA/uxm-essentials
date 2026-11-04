package com.uxplima.uxmessentials.guard.domain.engine;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One region in the form the engine reads: its rules flattened through the parent chain and template, then
 * through the action tree, into one byte per role row and action.
 *
 * <p>Row 0 is nature (only {@code everyone} rules), row 1 a player without a role, then one row per role. A
 * slot holds {@link #UNSET}, {@link #ALLOW}, {@link #DENY} or {@link #DYNAMIC}; dynamic means a permission or
 * condition rule sits on the deciding node, so the walk runs again for that player at call time.
 */
public final class CompiledRegion {

    static final byte UNSET = 0;
    static final byte ALLOW = 1;
    static final byte DENY = 2;
    static final byte DYNAMIC = 3;

    static final int ROW_WORLD = 0;
    static final int ROW_NON_MEMBER = 1;

    private final Region region;
    private final Region effective;
    private final ActionTree tree;
    private final byte[][] table;
    private final Map<UUID, Integer> rows;
    private final RoleName[] rowRoles;
    private final long[] setActions;
    private final String family;
    private final boolean worldDefaults;
    private final Map<Integer, List<Rule>> rulesByNode;

    CompiledRegion(Region region, Region effective, ActionTree tree, String family, boolean worldDefaults) {
        this.region = region;
        this.effective = effective;
        this.tree = tree;
        this.family = family;
        this.worldDefaults = worldDefaults;

        Map<RoleName, Integer> roleRows = new LinkedHashMap<>();
        for (RoleName role : effective.roles().values()) {
            roleRows.putIfAbsent(role, ROW_NON_MEMBER + 1 + roleRows.size());
        }
        for (Rule rule : effective.rules()) {
            if (rule.target() instanceof Target.Role(RoleName role)) {
                roleRows.putIfAbsent(role, ROW_NON_MEMBER + 1 + roleRows.size());
            }
        }
        this.rowRoles = new RoleName[ROW_NON_MEMBER + 1 + roleRows.size()];
        roleRows.forEach((role, row) -> rowRoles[row] = role);
        Map<UUID, Integer> playerRows = new HashMap<>();
        effective.roles().forEach((player, role) -> playerRows.put(player, roleRows.get(role)));
        this.rows = Map.copyOf(playerRows);

        this.rulesByNode = new HashMap<>();
        for (Rule rule : effective.rules()) {
            int node = tree.indexOf(rule.action());
            if (node >= 0) {
                rulesByNode.computeIfAbsent(node, n -> new ArrayList<>()).add(rule);
            }
        }
        this.setActions = new long[(tree.size() + 63) >>> 6];
        this.table = new byte[rowRoles.length][tree.size()];
        for (int action = 0; action < tree.size(); action++) {
            boolean any = false;
            for (int row = 0; row < rowRoles.length; row++) {
                byte slot = staticWalk(action, row);
                table[row][action] = slot;
                any |= slot != UNSET;
            }
            if (any) {
                setActions[action >>> 6] |= 1L << action;
            }
        }
    }

    /** The region as stored. */
    public Region region() {
        return region;
    }

    /** The region with the parent chain and template folded in. */
    public Region effective() {
        return effective;
    }

    /** The behaviours that apply here, inherited ones included. */
    public Behaviours behaviours() {
        return effective.behaviours();
    }

    ActionTree tree() {
        return tree;
    }

    /** The border family. */
    public String family() {
        return family;
    }

    /** True for the implicit world defaults region. */
    public boolean worldDefaults() {
        return worldDefaults;
    }

    /** True when any row decides something for {@code action}. */
    boolean sets(int action) {
        return (setActions[action >>> 6] & (1L << action)) != 0;
    }

    long[] setActions() {
        return setActions;
    }

    /** The row for a subject: nature, a role holder, or a player without a role. */
    int row(GuardSubject subject) {
        UUID id = subject.id();
        if (id == null) {
            return ROW_WORLD;
        }
        Integer row = rows.get(id);
        return row == null ? ROW_NON_MEMBER : row;
    }

    /** The role {@code subject} holds here, or null. */
    @Nullable RoleName role(GuardSubject subject) {
        int row = row(subject);
        return row > ROW_NON_MEMBER ? rowRoles[row] : null;
    }

    /** This region's answer for {@code action}. */
    Verdict decide(int action, GuardSubject subject) {
        int row = row(subject);
        byte slot = table[row][action];
        return switch (slot) {
            case ALLOW -> Verdict.ALLOW;
            case DENY -> Verdict.DENY;
            case DYNAMIC -> dynamicWalk(action, row, subject).verdict();
            default -> Verdict.UNSET;
        };
    }

    /** The full walk with the deciding node and rule, for explanations. */
    Decision explain(int action, GuardSubject subject) {
        return dynamicWalk(action, row(subject), subject);
    }

    /** What one region said and why. */
    record Decision(Verdict verdict, @Nullable ActionId node, @Nullable Rule rule) {
        static final Decision NONE = new Decision(Verdict.UNSET, null, null);
    }

    private byte staticWalk(int action, int row) {
        for (int node = action; node >= 0; node = tree.parent(node)) {
            List<Rule> rules = rulesByNode.get(node);
            if (rules == null) {
                continue;
            }
            int bestRank = Integer.MAX_VALUE;
            boolean deny = false;
            boolean dynamic = false;
            for (Rule rule : rules) {
                Target target = rule.target();
                if (target.dynamic()) {
                    if (row != ROW_WORLD) {
                        dynamic = true;
                    }
                    continue;
                }
                if (!matchesStatic(target, row)) {
                    continue;
                }
                if (target.rank() < bestRank) {
                    bestRank = target.rank();
                    deny = rule.effect() == Effect.DENY;
                } else if (target.rank() == bestRank && rule.effect() == Effect.DENY) {
                    deny = true;
                }
            }
            if (dynamic) {
                return DYNAMIC;
            }
            if (bestRank != Integer.MAX_VALUE) {
                return deny ? DENY : ALLOW;
            }
        }
        return UNSET;
    }

    private Decision dynamicWalk(int action, int row, GuardSubject subject) {
        for (int node = action; node >= 0; node = tree.parent(node)) {
            List<Rule> rules = rulesByNode.get(node);
            if (rules == null) {
                continue;
            }
            int bestRank = Integer.MAX_VALUE;
            @Nullable Rule decisive = null;
            for (Rule rule : rules) {
                Target target = rule.target();
                boolean matches = target.dynamic() ? row != ROW_WORLD && matchesDynamic(target, subject)
                        : matchesStatic(target, row);
                if (!matches) {
                    continue;
                }
                if (target.rank() < bestRank
                        || (target.rank() == bestRank && rule.effect() == Effect.DENY)) {
                    bestRank = target.rank();
                    decisive = rule;
                }
            }
            if (decisive != null) {
                return new Decision(Verdict.of(decisive.effect()), tree.id(node), decisive);
            }
        }
        return Decision.NONE;
    }

    private boolean matchesStatic(Target target, int row) {
        return switch (target) {
            case Target.Everyone e -> true;
            case Target.NonMember n -> row == ROW_NON_MEMBER;
            case Target.Role r -> row > ROW_NON_MEMBER && rowRoles[row].equals(r.role());
            case Target.Permission p -> false;
            case Target.Condition c -> false;
        };
    }

    private static boolean matchesDynamic(Target target, GuardSubject subject) {
        return switch (target) {
            case Target.Permission p -> subject.has(p.node());
            case Target.Condition c -> subject.meets(c.expression());
            default -> false;
        };
    }

    @Override
    public String toString() {
        return "CompiledRegion[" + region.key() + "]";
    }
}

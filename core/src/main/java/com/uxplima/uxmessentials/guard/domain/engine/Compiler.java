package com.uxplima.uxmessentials.guard.domain.engine;

import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Turns a world's stored regions into a {@link WorldIndex}.
 *
 * <p>Each region is flattened first: the parent chain from the root down, each level's template under its own
 * entries, so a child writes only what differs. Regions that did not change keep their compiled form, and chunk
 * columns away from every change are carried over, so an edit costs work near the edit only.
 */
public final class Compiler {

    private Compiler() {}

    /**
     * What one world compiles from.
     *
     * @param world the world name
     * @param minY the world's lowest block
     * @param maxY the world's highest block
     * @param regions the stored regions of the world
     * @param defaultRules the world default rules from config
     * @param defaultBehaviours the world default behaviours from config
     * @param locked true when loading failed and the world must be fully protected
     */
    public record Input(
            String world,
            int minY,
            int maxY,
            Collection<Region> regions,
            List<Rule> defaultRules,
            Behaviours defaultBehaviours,
            boolean locked) {

        public Input {
            Objects.requireNonNull(world, "world");
            regions = List.copyOf(regions);
            defaultRules = List.copyOf(defaultRules);
            Objects.requireNonNull(defaultBehaviours, "defaultBehaviours");
        }
    }

    /** Compile a world, reusing what it can from {@code previous}. */
    public static WorldIndex compile(
            ActionTree tree, Input input, Map<String, Template> templates, @Nullable WorldIndex previous,
            @Nullable ActionTree previousTree) {
        Map<RegionId, Region> byId = new HashMap<>();
        for (Region region : input.regions()) {
            byId.put(region.id(), region);
        }
        boolean sameTree = previousTree == tree;
        List<CompiledRegion> compiled = new ArrayList<>(byId.size());
        List<Bounds> changed = new ArrayList<>();
        Set<RegionId> seen = new HashSet<>();
        for (Region region : byId.values()) {
            Region effective = flatten(region, byId, templates);
            String family = familyOf(region, byId);
            CompiledRegion old = previous == null ? null : previous.region(region.id()).orElse(null);
            if (sameTree && old != null && old.region().equals(region) && old.effective().equals(effective)
                    && old.family().equals(family)) {
                compiled.add(old);
            } else {
                compiled.add(new CompiledRegion(region, effective, tree, family, false));
                changed.add(region.shape().bounds());
                if (old != null) {
                    changed.add(old.region().shape().bounds());
                }
            }
            seen.add(region.id());
        }
        if (previous != null) {
            for (Region gone : previous.regions()) {
                if (!seen.contains(gone.id())) {
                    changed.add(gone.shape().bounds());
                }
            }
        }
        compiled.sort(Precedence.ORDER);

        Region defaultsRegion = defaultsRegion(input);
        CompiledRegion defaults = defaultsRegion.rules().isEmpty() && defaultsRegion.behaviours().isEmpty()
                ? null
                : new CompiledRegion(defaultsRegion, defaultsRegion, tree, RegionId.WORLD_DEFAULTS.value(), true);

        Map<Long, Column> reuse = Map.of();
        if (previous != null && sameTree && !previous.locked() && !input.locked()
                && sameDefaults(previous, defaultsRegion)
                && changed.stream().noneMatch(b -> b.equals(Bounds.EVERYTHING))) {
            reuse = previous.columnsOutside(changed);
        }
        return new WorldIndex(input.world(), input.minY(), input.maxY(), input.locked(),
                compiled.toArray(CompiledRegion[]::new), defaults, reuse, defaultsRegion);
    }

    /** A region with its parent chain and templates folded in. */
    public static Region flatten(Region region, Map<RegionId, Region> byId, Map<String, Template> templates) {
        List<Region> chain = chain(region, byId);
        Map<String, Rule> rules = new LinkedHashMap<>();
        Behaviours behaviours = Behaviours.empty();
        Map<UUID, RoleName> roles = new HashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            Region level = chain.get(i);
            Template template = level.template() == null ? null : templates.get(level.template());
            if (template != null) {
                for (Rule rule : template.rules()) {
                    rules.put(slot(rule.action(), rule.target()), rule);
                }
                behaviours = behaviours.overlay(template.behaviours());
            }
            for (Rule rule : level.rules()) {
                rules.put(slot(rule.action(), rule.target()), rule);
            }
            behaviours = behaviours.overlay(level.behaviours());
            roles.putAll(level.roles());
        }
        return region.withRules(List.copyOf(rules.values())).withBehaviours(behaviours).withRoles(roles);
    }

    /** The region and its ancestors, nearest first; stops at a missing parent or a cycle. */
    public static List<Region> chain(Region region, Map<RegionId, Region> byId) {
        List<Region> chain = new ArrayList<>();
        Set<RegionId> visited = new HashSet<>();
        for (Region at = region; at != null && visited.add(at.id()); ) {
            chain.add(at);
            RegionId parent = at.parent();
            at = parent == null ? null : byId.get(parent);
        }
        return chain;
    }

    private static String familyOf(Region region, Map<RegionId, Region> byId) {
        for (Region at : chain(region, byId)) {
            if (at.family() != null) {
                return at.family();
            }
        }
        List<Region> chain = chain(region, byId);
        return chain.get(chain.size() - 1).id().value();
    }

    private static String slot(ActionId action, Target target) {
        return action.value() + " " + target.text();
    }

    private static Region defaultsRegion(Input input) {
        return Region.create(new RegionKey(input.world(), RegionId.WORLD_DEFAULTS), GuardScope.server("defaults"),
                        new Shape.Global())
                .withPriority(Integer.MIN_VALUE)
                .withRules(input.defaultRules())
                .withBehaviours(input.defaultBehaviours());
    }

    private static boolean sameDefaults(WorldIndex previous, Region defaults) {
        return previous.defaultsRegion().equals(defaults);
    }
}

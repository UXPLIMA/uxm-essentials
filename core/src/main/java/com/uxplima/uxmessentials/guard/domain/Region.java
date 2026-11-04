package com.uxplima.uxmessentials.guard.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A protected region: where it is, how it ranks against overlapping regions, who holds roles in it, its rules
 * and its behaviours. Immutable; every edit returns a new region with the version bumped by the store.
 *
 * @param key world and id
 * @param scope which servers load it
 * @param shape the space it covers
 * @param priority explicit precedence; higher wins before containment is considered
 * @param parent the region it inherits from, in the same world
 * @param family the border family; {@code null} means the root of the parent chain
 * @param template the linked template, whose rules and behaviours sit under the region's own
 * @param tags names for bulk edits
 * @param locked protected against staff too
 * @param rules protection rules
 * @param behaviours behaviour values
 * @param roles players and the role each holds
 * @param importBatch the WorldGuard import that created it, for undo
 */
public record Region(
        RegionKey key,
        GuardScope scope,
        Shape shape,
        int priority,
        @Nullable RegionId parent,
        @Nullable String family,
        @Nullable String template,
        Set<String> tags,
        boolean locked,
        List<Rule> rules,
        Behaviours behaviours,
        Map<UUID, RoleName> roles,
        @Nullable String importBatch) {

    public Region {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(behaviours, "behaviours");
        tags = Set.copyOf(new TreeSet<>(Objects.requireNonNull(tags, "tags")));
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        roles = Map.copyOf(Objects.requireNonNull(roles, "roles"));
        if (key.id().equals(parent)) {
            throw new IllegalArgumentException("a region cannot be its own parent");
        }
    }

    /** A new region with nothing set. */
    public static Region create(RegionKey key, GuardScope scope, Shape shape) {
        return new Region(
                key, scope, shape, 0, null, null, null, Set.of(), false, List.of(), Behaviours.empty(), Map.of(), null);
    }

    public RegionId id() {
        return key.id();
    }

    public String world() {
        return key.world();
    }

    /** The role {@code player} holds here, if any. */
    public Optional<RoleName> roleOf(UUID player) {
        return Optional.ofNullable(roles.get(player));
    }

    public Region withShape(Shape newShape) {
        return new Region(
                key,
                scope,
                newShape,
                priority,
                parent,
                family,
                template,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withScope(GuardScope newScope) {
        return new Region(
                key,
                newScope,
                shape,
                priority,
                parent,
                family,
                template,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withPriority(int newPriority) {
        return new Region(
                key,
                scope,
                shape,
                newPriority,
                parent,
                family,
                template,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withParent(@Nullable RegionId newParent) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                newParent,
                family,
                template,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withFamily(@Nullable String newFamily) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                newFamily,
                template,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withTemplate(@Nullable String newTemplate) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                newTemplate,
                tags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withTags(Set<String> newTags) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                template,
                newTags,
                locked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withLocked(boolean newLocked) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                template,
                tags,
                newLocked,
                rules,
                behaviours,
                roles,
                importBatch);
    }

    public Region withBehaviours(Behaviours newBehaviours) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                template,
                tags,
                locked,
                rules,
                newBehaviours,
                roles,
                importBatch);
    }

    public Region withRoles(Map<UUID, RoleName> newRoles) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                template,
                tags,
                locked,
                rules,
                behaviours,
                newRoles,
                importBatch);
    }

    public Region withImportBatch(@Nullable String batch) {
        return new Region(
                key, scope, shape, priority, parent, family, template, tags, locked, rules, behaviours, roles, batch);
    }

    /** This region with {@code rule} added, replacing a rule for the same action and target. */
    public Region withRule(Rule rule) {
        List<Rule> next = new ArrayList<>(rules.size() + 1);
        for (Rule existing : rules) {
            if (!existing.sameSlot(rule)) {
                next.add(existing);
            }
        }
        next.add(rule);
        return withRules(next);
    }

    /** This region without the rule for {@code action} and {@code target}. */
    public Region withoutRule(ActionId action, Target target) {
        return withRules(rules.stream()
                .filter(r -> !(r.action().equals(action) && r.target().equals(target)))
                .toList());
    }

    public Region withRules(List<Rule> newRules) {
        return new Region(
                key,
                scope,
                shape,
                priority,
                parent,
                family,
                template,
                tags,
                locked,
                newRules,
                behaviours,
                roles,
                importBatch);
    }

    /** This region with {@code player} holding {@code role}, or without any role when {@code role} is null. */
    public Region withRole(UUID player, @Nullable RoleName role) {
        Map<UUID, RoleName> next = new java.util.HashMap<>(roles);
        if (role == null) {
            next.remove(player);
        } else {
            next.put(player, role);
        }
        return withRoles(next);
    }
}

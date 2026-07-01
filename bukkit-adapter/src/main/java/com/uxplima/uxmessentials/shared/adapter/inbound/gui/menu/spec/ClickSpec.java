package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The actions, conditions, requirement blocks, and else-chains bound to an item's click gestures, keyed by {@link
 * ClickKind}. A gesture's effective action list is its own list followed by whatever is bound to {@link ClickKind#ANY},
 * so authors can share behaviour across every gesture without repeating it. A gesture's effective requirement block
 * ({@link #requirementFor}) merges its own block with the {@code ANY} block the same way, so a shared gate ("must have
 * money") can sit under {@code ANY} once.
 *
 * <p>A gesture may also carry an else-chain ({@link #elseFor}) — the fallback branches tried when its requirement block
 * fails: try block A, else block B, and so on, nested to any depth. Unlike actions and requirements the else-chain is
 * <em>not</em> merged with {@code ANY}: a fallback ladder is a self-contained if / else-if / else structure specific to
 * one gesture, so blending it with a shared {@code ANY} chain would make the branch order ambiguous.
 */
public record ClickSpec(
        Map<ClickKind, List<Ref>> actions,
        Map<ClickKind, List<Ref>> conditions,
        Map<ClickKind, RequirementSpec> requirements,
        Map<ClickKind, ClickBranch> orElse) {

    /**
     * The historic two-argument form. It forwards to the canonical constructor with no requirement blocks and no
     * else-chains, so every existing {@code new ClickSpec(actions, conditions)} call-site keeps compiling unchanged —
     * only the loader reaches for the longer forms to attach a gesture's requirements and fallbacks.
     */
    public ClickSpec(Map<ClickKind, List<Ref>> actions, Map<ClickKind, List<Ref>> conditions) {
        this(actions, conditions, Map.of());
    }

    /**
     * The three-argument form (actions, conditions, requirement blocks). It forwards to the canonical constructor with
     * no else-chains, so every existing {@code new ClickSpec(actions, conditions, requirements)} call-site keeps
     * compiling unchanged — only the loader reaches for the four-argument form to attach a gesture's else-chain.
     */
    public ClickSpec(
            Map<ClickKind, List<Ref>> actions,
            Map<ClickKind, List<Ref>> conditions,
            Map<ClickKind, RequirementSpec> requirements) {
        this(actions, conditions, requirements, Map.of());
    }

    public ClickSpec {
        actions = copyRefs(Objects.requireNonNull(actions, "actions"));
        conditions = copyRefs(Objects.requireNonNull(conditions, "conditions"));
        requirements = copyRequirements(Objects.requireNonNull(requirements, "requirements"));
        orElse = copyBranches(Objects.requireNonNull(orElse, "orElse"));
    }

    /**
     * The actions that should fire for {@code kind}: the gesture's own list first, then the shared {@code ANY}
     * list. The result is a fresh immutable list so callers can't mutate the underlying spec.
     */
    public List<Ref> actionsFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        List<Ref> merged = new ArrayList<>(actions.getOrDefault(kind, List.of()));
        merged.addAll(actions.getOrDefault(ClickKind.ANY, List.of()));
        return List.copyOf(merged);
    }

    /**
     * The requirement block that gates {@code kind}: the gesture's own block merged with the shared {@link
     * ClickKind#ANY} block. The merge concatenates their requirements (the gesture's first, then {@code ANY}'s) and
     * their deny actions the same way (the gesture's deny runs before {@code ANY}'s); the gesture's own {@code minimum}
     * applies to the concatenated set, so a specific gesture keeps control of how its combined gate combines. When only
     * one side is set that side is returned as-is, and when neither is set the result is {@link RequirementSpec#NONE}
     * (always passes, denies nothing).
     */
    public RequirementSpec requirementFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        RequirementSpec own = requirements.get(kind);
        RequirementSpec any = requirements.get(ClickKind.ANY);
        if (own == null) {
            return any == null ? RequirementSpec.NONE : any;
        }
        if (any == null || kind == ClickKind.ANY) {
            return own;
        }
        List<Requirement> mergedReqs = new ArrayList<>(own.requirements());
        mergedReqs.addAll(any.requirements());
        List<Ref> mergedDeny = new ArrayList<>(own.deny());
        mergedDeny.addAll(any.deny());
        return new RequirementSpec(mergedReqs, own.minimum(), mergedDeny);
    }

    /**
     * The head of the else-chain the runtime tries when {@code kind}'s requirement block fails, or empty when the
     * gesture has no fallback (in which case the block's own {@code deny} runs instead). This is deliberately per-kind
     * with no {@link ClickKind#ANY} merge: an else-chain is a single ordered fallback ladder, so a {@code left}
     * gesture's chain and any {@code ANY} chain stay independent rather than being spliced into one ambiguous order.
     */
    public Optional<ClickBranch> elseFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Optional.ofNullable(orElse.get(kind));
    }

    private static Map<ClickKind, List<Ref>> copyRefs(Map<ClickKind, List<Ref>> source) {
        Map<ClickKind, List<Ref>> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, refs) ->
                copy.put(Objects.requireNonNull(kind, "kind"), List.copyOf(Objects.requireNonNull(refs, "refs"))));
        return Map.copyOf(copy);
    }

    private static Map<ClickKind, RequirementSpec> copyRequirements(Map<ClickKind, RequirementSpec> source) {
        Map<ClickKind, RequirementSpec> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, spec) ->
                copy.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(spec, "requirementSpec")));
        return Map.copyOf(copy);
    }

    private static Map<ClickKind, ClickBranch> copyBranches(Map<ClickKind, ClickBranch> source) {
        Map<ClickKind, ClickBranch> copy = new EnumMap<>(ClickKind.class);
        source.forEach((kind, branch) ->
                copy.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(branch, "branch")));
        return Map.copyOf(copy);
    }
}

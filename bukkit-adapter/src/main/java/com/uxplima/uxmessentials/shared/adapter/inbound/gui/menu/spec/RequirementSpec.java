package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * A block of {@link Requirement}s bound to a click gesture, plus how many of them must pass and what to run when the
 * block fails. It is the pure model behind the config grammar {@code left { click = [...], requirements = [...],
 * minimum = N, deny = [...] }}: the {@code requirements} are the gates, {@code minimum} is how many must hold, and
 * {@code deny} is the action list the runtime fires instead of the click's own actions when the gate fails.
 *
 * <p>The {@link #minimum} yields the three common combinators without a separate flag:
 * <ul>
 *   <li>{@code minimum <= 0} or {@code minimum >= requirements.size()} — ALL must pass (AND).</li>
 *   <li>{@code minimum == 1} — ANY may pass (OR).</li>
 *   <li>otherwise — at least N of the M must pass (N-of-M).</li>
 * </ul>
 * {@link #effectiveMinimum()} folds those rules into the concrete count the runtime compares its pass tally against,
 * so the runtime carries no combinator logic of its own.
 *
 * <p>Pure by design: which requirements actually pass is decided in the runtime (it holds the condition registry);
 * this record only says how they combine, so it stays Bukkit-free for plain-JUnit testing. {@link #NONE} is the empty
 * block that always passes and denies nothing — the value a gesture with no requirement block resolves to.
 */
public record RequirementSpec(List<Requirement> requirements, int minimum, List<Ref> deny) {

    /** The empty block: no requirements (so it always passes) and no deny actions. */
    public static final RequirementSpec NONE = new RequirementSpec(List.of(), 0, List.of());

    public RequirementSpec {
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        deny = List.copyOf(Objects.requireNonNull(deny, "deny"));
    }

    /**
     * How many requirements must pass for this block to pass, resolved from the {@link #minimum} rules: a
     * non-positive minimum means all of them (AND), and a minimum larger than the block is capped at the block size
     * so an over-large N still means "all". A block with no requirements yields {@code 0}, so it always passes.
     */
    public int effectiveMinimum() {
        return minimum <= 0 ? requirements.size() : Math.min(minimum, requirements.size());
    }
}

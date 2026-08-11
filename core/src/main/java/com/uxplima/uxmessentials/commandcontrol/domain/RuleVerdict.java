package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link RuleSet} decision together with what produced it: the mode that was read, whether the command root was
 * found in the list, and which list was consulted.
 *
 * <p>The gate itself only needs {@link RuleSet.Decision}, which is why {@link RuleSet#decide} exists and stays the
 * hot path. This carries the rest for anything that has to explain the outcome rather than act on it: an operator
 * asking why {@code /fly} is blocked, or a plugin outside the module reading the same answer through the published
 * API. It is produced by the same resolution the gate runs, so an explanation can never disagree with what actually
 * happens to the command.
 *
 * @param commandRoot the normalised root the decision was made about, lowercase and without its leading slash
 * @param mode whether the list that applied was read as a whitelist or a blacklist
 * @param decision whether the command may run
 * @param reason what settled it: the bypass node, the root being listed, or the root being absent
 * @param group the permission group whose own list decided, or empty when the {@code default} list did (also empty
 *     for {@link Reason#BYPASS}, where no list was consulted at all)
 */
public record RuleVerdict(
        String commandRoot,
        RuleMode mode,
        RuleSet.Decision decision,
        RuleVerdict.Reason reason,
        Optional<String> group) {

    /** What settled the decision. */
    public enum Reason {
        /** The player holds the bypass node, so no list was read. */
        BYPASS,
        /** The command root is in the list that applied. */
        LISTED,
        /** The command root is absent from the list that applied. */
        UNLISTED
    }

    public RuleVerdict {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(group, "group");
    }

    /** Whether the command may run. */
    public boolean allowed() {
        return decision == RuleSet.Decision.ALLOW;
    }
}

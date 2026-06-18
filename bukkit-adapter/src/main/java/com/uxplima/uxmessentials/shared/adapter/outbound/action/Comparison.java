package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A parsed {@code CONDITION} action spec: a left operand, an operator, and a right operand. The operands are
 * pre-resolution templates ({@code <left> <op> <right>}); the runner resolves each side's placeholders per-viewer
 * and then calls {@link #evaluate}. The two-token-equality operators ({@code ==}/{@code !=}) work on any value;
 * the ordering operators ({@code > < >= <=}) require both resolved sides to parse as numbers (a non-numeric
 * ordering compare is simply false). Numeric {@code ==}/{@code !=} compares by value so {@code 1.0 == 1} holds;
 * otherwise equality is a literal string compare.
 *
 * <p>The longest operator is matched first so {@code >=} is not mistaken for {@code >} followed by a stray
 * {@code =}. A spec with no recognised operator is unparseable and the gate is skipped by the caller.
 */
@NullMarked
record Comparison(String left, Operator operator, String right) {

    /** The comparison operators a {@code CONDITION} spec may use, longest token first for greedy matching. */
    enum Operator {
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        EQUAL("=="),
        NOT_EQUAL("!="),
        GREATER(">"),
        LESS("<");

        private final String token;

        Operator(String token) {
            this.token = token;
        }

        String token() {
            return token;
        }
    }

    /** Split {@code spec} on its operator into a comparison, or empty when no operator is present. */
    static Optional<Comparison> parse(String spec) {
        for (Operator operator : Operator.values()) {
            int at = spec.indexOf(operator.token());
            if (at >= 0) {
                String left = spec.substring(0, at).strip();
                String right = spec.substring(at + operator.token().length()).strip();
                if (!left.isEmpty() && !right.isEmpty()) {
                    return Optional.of(new Comparison(left, operator, right));
                }
            }
        }
        return Optional.empty();
    }

    /** Evaluate this comparison against the resolved {@code left}/{@code right} operand values. */
    boolean evaluate(String resolvedLeft, String resolvedRight) {
        Double leftNumber = asNumber(resolvedLeft);
        Double rightNumber = asNumber(resolvedRight);
        if (operator == Operator.EQUAL || operator == Operator.NOT_EQUAL) {
            return equality(leftNumber, rightNumber, resolvedLeft, resolvedRight);
        }
        // The ordering operators need both sides numeric; a non-numeric ordering compare is simply false.
        if (leftNumber == null || rightNumber == null) {
            return false;
        }
        double left = leftNumber;
        double right = rightNumber;
        return switch (operator) {
            case GREATER -> left > right;
            case LESS -> left < right;
            case GREATER_OR_EQUAL -> left >= right;
            case LESS_OR_EQUAL -> left <= right;
            default -> false; // EQUAL/NOT_EQUAL handled above
        };
    }

    /** Equality / inequality: by numeric value when both sides parse as numbers, else literal string compare. */
    private boolean equality(
            @Nullable Double leftNumber, @Nullable Double rightNumber, String resolvedLeft, String resolvedRight) {
        boolean equal = (leftNumber != null && rightNumber != null)
                ? leftNumber.doubleValue() == rightNumber.doubleValue()
                : resolvedLeft.equals(resolvedRight);
        return operator == Operator.NOT_EQUAL ? !equal : equal;
    }

    private static @Nullable Double asNumber(String value) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}

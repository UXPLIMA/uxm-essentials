package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

/**
 * The value semantics the {@link Parser} leans on: coercing a parsed value to the number or boolean an operator
 * needs, applying an arithmetic or comparison operator, and rejecting any result that is not a finite number.
 * Values are only ever {@link Double}, {@link Boolean}, or {@link String}; a coercion that does not fit raises an
 * {@link ExpressionException} so a type mismatch fails the evaluation rather than guessing.
 */
final class Values {

    private Values() {}

    static double number(Object value) throws ExpressionException {
        if (value instanceof Double d) {
            return d;
        }
        throw new ExpressionException("expected a number but found " + kind(value));
    }

    static boolean bool(Object value) throws ExpressionException {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new ExpressionException("expected a boolean but found " + kind(value));
    }

    static double finite(double value) throws ExpressionException {
        if (!Double.isFinite(value)) {
            throw new ExpressionException("result is not a finite number");
        }
        return value;
    }

    static double arithmetic(double a, String op, double b) throws ExpressionException {
        double result =
                switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> divide(a, b);
                    case "%" -> modulo(a, b);
                    case "^" -> Math.pow(a, b);
                    default -> throw new ExpressionException("unknown operator: " + op);
                };
        return finite(result);
    }

    static boolean compare(Object left, String op, Object right) throws ExpressionException {
        if (left instanceof Double a && right instanceof Double b) {
            return numeric(a, op, b);
        }
        return switch (op) {
            case "==" -> stringOf(left).equals(stringOf(right));
            case "!=" -> !stringOf(left).equals(stringOf(right));
            default -> throw new ExpressionException(op + " needs numeric operands");
        };
    }

    private static boolean numeric(double a, String op, double b) throws ExpressionException {
        return switch (op) {
            case "==" -> a == b;
            case "!=" -> a != b;
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case ">=" -> a >= b;
            default -> throw new ExpressionException("unknown comparison: " + op);
        };
    }

    private static double divide(double a, double b) throws ExpressionException {
        if (b == 0) {
            throw new ExpressionException("division by zero");
        }
        return a / b;
    }

    private static double modulo(double a, double b) throws ExpressionException {
        if (b == 0) {
            throw new ExpressionException("modulo by zero");
        }
        return a % b;
    }

    private static String stringOf(Object value) {
        if (value instanceof Double d && Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) (double) d);
        }
        return String.valueOf(value);
    }

    private static String kind(Object value) {
        if (value instanceof Boolean) {
            return "a boolean";
        }
        if (value instanceof String) {
            return "text";
        }
        return "a number";
    }
}

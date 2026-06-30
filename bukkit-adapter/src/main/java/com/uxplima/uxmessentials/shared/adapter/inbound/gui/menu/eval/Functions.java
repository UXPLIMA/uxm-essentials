package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * The complete, fixed allow-list of functions an expression may call. Nothing here reflects, performs I/O, or
 * reaches the host runtime; the parser rejects any identifier that is not one of these names, so the function
 * surface is exactly this file. {@code min}/{@code max} are variadic (one argument or more); the rest are unary.
 */
final class Functions {

    private static final Set<String> NAMES = Set.of("min", "max", "abs", "floor", "ceil", "round", "sqrt");

    private Functions() {}

    static boolean isFunction(String name) {
        return NAMES.contains(name);
    }

    static double apply(String name, List<Double> args) throws ExpressionException {
        return switch (name) {
            case "min" -> reduce(name, args, Math::min);
            case "max" -> reduce(name, args, Math::max);
            case "abs" -> unary(name, args, Math::abs);
            case "floor" -> unary(name, args, Math::floor);
            case "ceil" -> unary(name, args, Math::ceil);
            case "round" -> unary(name, args, x -> (double) Math.round(x));
            case "sqrt" -> sqrt(args);
            default -> throw new ExpressionException("unknown function: " + name);
        };
    }

    private static double reduce(String name, List<Double> args, DoubleBinaryOperator op) throws ExpressionException {
        if (args.isEmpty()) {
            throw new ExpressionException(name + " needs at least one argument");
        }
        double acc = args.get(0);
        for (int i = 1; i < args.size(); i++) {
            acc = op.applyAsDouble(acc, args.get(i));
        }
        return acc;
    }

    private static double unary(String name, List<Double> args, DoubleUnaryOperator op) throws ExpressionException {
        if (args.size() != 1) {
            throw new ExpressionException(name + " needs exactly one argument");
        }
        return op.applyAsDouble(args.get(0));
    }

    private static double sqrt(List<Double> args) throws ExpressionException {
        double x = unary("sqrt", args, DoubleUnaryOperator.identity());
        if (x < 0) {
            throw new ExpressionException("sqrt of a negative number");
        }
        return Math.sqrt(x);
    }
}

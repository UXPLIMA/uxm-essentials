package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;

import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import org.jspecify.annotations.NullMarked;

/**
 * Validates an {@code /npc action add} value against its {@link NpcActionType} at add time, so an operator gets a
 * clear rejection rather than a silently-broken action that only fails on click. Only the cheap, structural
 * checks live here — a numeric {@code DELAY}/{@code CHANCE}/{@code COST}, a positive-int {@code RANDOM} group
 * count, and a resolvable {@code GIVE} material (or an accepted serialized token); the free-text gates
 * ({@code PERMISSION}, {@code CONDITION}) and the operator-content effects ({@code MESSAGE}, commands, …) accept
 * any value. A rejection carries a short {@code hint} the catalog message interpolates so the operator is told
 * the expected shape.
 */
@NullMarked
final class NpcActionValueCheck {

    private static final double FULL_PERCENT = 100.0;
    private static final String SERIALIZED_PREFIX = "b64:";

    private NpcActionValueCheck() {}

    /** The outcome of a value check: a passed value, or a rejection carrying the expected-shape hint. */
    sealed interface Result permits Result.Valid, Result.Invalid {

        static Result valid() {
            return Result.Valid.INSTANCE;
        }

        static Result invalid(String hint) {
            return new Result.Invalid(hint);
        }

        boolean isValid();

        record Valid() implements Result {
            private static final Valid INSTANCE = new Valid();

            @Override
            public boolean isValid() {
                return true;
            }
        }

        record Invalid(String hint) implements Result {
            @Override
            public boolean isValid() {
                return false;
            }
        }
    }

    /** Check {@code value} for {@code type}, returning {@link Result#valid()} for any type with no cheap check. */
    static Result check(NpcActionType type, String value) {
        return switch (type) {
            case DELAY -> nonNegativeLong(value, "delay must be a whole number of ticks, e.g. 40");
            case CHANCE -> percent(value);
            case COST -> nonNegativeNumber(value, "cost must be a number, e.g. 50");
            case RANDOM -> positiveInt(value, "random needs a positive count of the actions that follow, e.g. 3");
            case GIVE -> give(value);
            default -> Result.valid();
        };
    }

    private static Result positiveInt(String value, String hint) {
        try {
            return Integer.parseInt(value.strip()) > 0 ? Result.valid() : Result.invalid(hint);
        } catch (NumberFormatException notANumber) {
            return Result.invalid(hint);
        }
    }

    private static Result nonNegativeLong(String value, String hint) {
        try {
            return Long.parseLong(value.strip()) >= 0 ? Result.valid() : Result.invalid(hint);
        } catch (NumberFormatException notANumber) {
            return Result.invalid(hint);
        }
    }

    private static Result nonNegativeNumber(String value, String hint) {
        try {
            return Double.parseDouble(value.strip()) >= 0 ? Result.valid() : Result.invalid(hint);
        } catch (NumberFormatException notANumber) {
            return Result.invalid(hint);
        }
    }

    private static Result percent(String value) {
        String hint = "chance must be a percent from 0 to 100, e.g. 25";
        try {
            double percent = Double.parseDouble(value.strip());
            return percent >= 0 && percent <= FULL_PERCENT ? Result.valid() : Result.invalid(hint);
        } catch (NumberFormatException notANumber) {
            return Result.invalid(hint);
        }
    }

    /**
     * A give value is either a serialized item token ({@code b64:…}, what {@code give hand} captures — accepted
     * as-is, its shape is the codec's concern) or a {@code <material>[:amount]} the material registry resolves.
     */
    private static Result give(String value) {
        String spec = value.strip();
        if (spec.startsWith(SERIALIZED_PREFIX)) {
            return Result.valid();
        }
        int colon = spec.indexOf(':');
        String name = colon < 0 ? spec : spec.substring(0, colon);
        Material material = Material.matchMaterial(name.strip().toUpperCase(Locale.ROOT));
        return material != null && material.isItem()
                ? Result.valid()
                : Result.invalid("give needs a material like DIAMOND or DIAMOND:3, or hand to capture your held item");
    }

    /** Map the operator's type word to an {@link NpcActionType}, or empty when unknown (case-insensitive). */
    static Optional<NpcActionType> parseType(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "console" -> Optional.of(NpcActionType.RUN_CONSOLE);
            case "player" -> Optional.of(NpcActionType.RUN_PLAYER);
            case "player_op" -> Optional.of(NpcActionType.RUN_PLAYER_AS_OP);
            case "message" -> Optional.of(NpcActionType.MESSAGE);
            case "actionbar" -> Optional.of(NpcActionType.ACTIONBAR);
            case "title" -> Optional.of(NpcActionType.TITLE);
            case "sound" -> Optional.of(NpcActionType.SOUND);
            case "connect" -> Optional.of(NpcActionType.CONNECT);
            case "delay" -> Optional.of(NpcActionType.DELAY);
            case "random" -> Optional.of(NpcActionType.RANDOM);
            case "chance" -> Optional.of(NpcActionType.CHANCE);
            case "permission" -> Optional.of(NpcActionType.PERMISSION);
            case "condition" -> Optional.of(NpcActionType.CONDITION);
            case "cost" -> Optional.of(NpcActionType.COST);
            case "give" -> Optional.of(NpcActionType.GIVE);
            default -> Optional.empty();
        };
    }
}

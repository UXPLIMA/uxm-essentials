package com.uxplima.uxmessentials.customcommands.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a player may put into a command line through a custom command's arguments. An action such as
 * {@code console: give %arg_player% diamond 1} runs a command the operator wrote, with the player's typed value in it,
 * and the console then runs whatever the line says. So a value going into a command line has to be what the operator
 * meant there.
 *
 * <p>A <em>word</em> is the default: no space to add more arguments, no {@code @} selector, none of
 * {@code { } [ ] " ' \} that open NBT, JSON or a quoted string, and no {@code %} that would start a placeholder or
 * another argument's token. <em>Text</em> may hold spaces, brackets and quotes. Nothing may hold a line break or another
 * control character, which would end the command and start another.
 *
 * <p>Which one a value must be depends on who runs the line:
 *
 * <ul>
 *   <li>A line the console or an operator runs ({@code console}, {@code console-random}, {@code command-as-op},
 *       {@code command-op}) takes text only from an argument declared with {@code in-commands = "text"}, and that text
 *       may hold no {@code %}, since a placeholder is filled after this check. Everything else, {@code %args%}
 *       included, is a word.
 *   <li>A line the player runs as themselves ({@code command}, {@code command-random}, {@code commandevent}) is held
 *       to the player's own permissions, so it also takes text from a {@code rest} argument and from {@code %args%},
 *       which is how an {@code alias} forwards what the player typed.
 * </ul>
 *
 * <p>Every step of every chain the command may run is checked before anything runs, so a refused value never leaves a
 * command half done.
 */
public final class CommandLineArguments {

    /** Who runs a command line, which decides what a typed value may be in it. */
    public enum Line {
        /** The player runs it as themselves. */
        PLAYER,
        /** The console runs it, or the player runs it with operator rights. */
        PRIVILEGED
    }

    /** The action heads whose value is a command line the player runs as themselves. */
    private static final Set<String> PLAYER_HEADS = Set.of("command", "command-random", "commandevent");

    /** The action heads whose value is a command line the console runs, or the player runs with operator rights. */
    private static final Set<String> PRIVILEGED_HEADS =
            Set.of("console", "console-random", "command-as-op", "command-op");

    /** The key everything typed after the command word is kept under, reached as {@code %args%}. */
    public static final String RAW = "args";

    /** A {@code %name%} token in an action; the menu engine also fills {@code %argument_<name>%} and {@code %<name>%}. */
    private static final Pattern TOKEN = Pattern.compile("%([^%\\s]+)%");

    /** What a single word may never hold: a placeholder, a selector, the brackets and quotes of NBT, JSON and strings. */
    private static final String NEVER_IN_A_WORD = "%@{}[]\"'\\";

    private CommandLineArguments() {}

    /** Who runs the command line of an action with this {@code head}, or empty when its value is no command line. */
    public static Optional<Line> lineOf(String head) {
        String key = head.strip().toLowerCase(Locale.ROOT);
        if (PRIVILEGED_HEADS.contains(key)) {
            return Optional.of(Line.PRIVILEGED);
        }
        return PLAYER_HEADS.contains(key) ? Optional.of(Line.PLAYER) : Optional.empty();
    }

    /**
     * The name of the first argument whose value {@code command} would put into a command line although it is not what
     * that line allows, or empty when every value fits.
     */
    public static Optional<String> refused(CustomCommand command, Map<String, String> values) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(values, "values");
        for (ActionChain chain : List.of(command.actions(), command.requirementDeny())) {
            for (ActionStep step : chain.steps()) {
                Optional<Line> line = lineOf(head(step.token()));
                if (line.isEmpty()) {
                    continue;
                }
                Optional<String> bad = refusedIn(step.token(), line.get(), command, values);
                if (bad.isPresent()) {
                    return bad;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Only the {@code values} that may go into a {@code line}, each by its own argument's rule. A command line is given
     * these and nothing else, so a token that reaches it some other way (the menu engine fills
     * {@code %argument_<name>%} from what it is handed) can only ever bring in a value that passed.
     */
    public static Map<String, String> fitFor(Line line, CustomCommand command, Map<String, String> values) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(values, "values");
        Map<String, String> fit = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (fits(line, name, declared(command, name), value)) {
                fit.put(name, value);
            }
        });
        return fit;
    }

    private static Optional<String> refusedIn(
            String token, Line line, CustomCommand command, Map<String, String> values) {
        Matcher matcher = TOKEN.matcher(token);
        while (matcher.find()) {
            String name = argumentName(matcher.group(1));
            Optional<CommandArgument> argument = declared(command, name);
            if (argument.isEmpty() && !RAW.equals(name)) {
                continue;
            }
            String value = values.get(name);
            if (value != null && !fits(line, name, argument, value)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static Optional<CommandArgument> declared(CustomCommand command, String name) {
        return command.arguments().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst();
    }

    /** Whether {@code value}, kept under {@code name}, may go into a {@code line}. */
    private static boolean fits(Line line, String name, Optional<CommandArgument> argument, String value) {
        boolean optedIn = argument.map(CommandArgument::textInCommands).orElse(false);
        return switch (line) {
            case PRIVILEGED -> optedIn ? fitsText(value) && value.indexOf('%') < 0 : fitsWord(value);
            case PLAYER -> {
                boolean text = optedIn
                        || argument.map(CommandArgument::rest).orElse(false)
                        || (argument.isEmpty() && RAW.equals(name));
                yield text ? fitsText(value) : fitsWord(value);
            }
        };
    }

    /** The argument a token names: {@code arg_x}, {@code argument_x} and {@code x} all name {@code x}. */
    private static String argumentName(String token) {
        if (token.startsWith("argument_")) {
            return token.substring("argument_".length());
        }
        if (token.startsWith("arg_")) {
            return token.substring("arg_".length());
        }
        return token;
    }

    /** Whether {@code value} is one word a command line can take as one argument and nothing more. */
    static boolean fitsWord(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character)
                    || Character.isSpaceChar(character)
                    || NEVER_IN_A_WORD.indexOf(character) >= 0
                    || breaksTheLine(character)) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code value} is text that stays on its line. */
    static boolean fitsText(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (breaksTheLine(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** A control character, or a line or paragraph separator, either of which may end a command and start another. */
    private static boolean breaksTheLine(char character) {
        return Character.isISOControl(character) || character == ' ' || character == ' ';
    }

    private static String head(String token) {
        int colon = token.indexOf(':');
        return colon < 0 ? token : token.substring(0, colon);
    }
}

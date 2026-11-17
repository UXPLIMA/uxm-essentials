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
 * What a player may put into an action through a custom command's arguments. An action such as
 * {@code console: give %arg_player% diamond 1} runs a command the operator wrote, with the player's typed value in it,
 * and the console then runs whatever the line says; {@code meta-set:%arg_key% %arg_value%} writes whatever the value
 * names. So a value going into an action has to be what the operator meant there.
 *
 * <p>A <em>word</em> is the default: no space to add more arguments, no {@code @} selector, none of
 * {@code { } [ ] " ' \} that open NBT, JSON or a quoted string, and no {@code %} that would start a placeholder or
 * another argument's token. <em>Text</em> may hold spaces, brackets and quotes. Nothing may hold a line break or another
 * control character, which would end the command and start another.
 *
 * <p>Which one a value must be depends on the action, by an allow-list so an action added later is held to the strict
 * rule until it is listed:
 *
 * <ul>
 *   <li>An action that shows text ({@code broadcast}, {@code message}, {@code title} and the rest of
 *       {@link #TEXT_HEADS}) is not checked here: the value goes in as words, escaped for that action.
 *   <li>A line the player runs or says as themselves ({@code command}, {@code command-random}, {@code commandevent},
 *       {@code chat}, {@code chat-as-player}) is held to the player's own permissions, so it also takes text from a
 *       {@code rest} argument and from {@code %args%}, which is how an {@code alias} forwards what the player typed.
 *   <li>Every other action acts with the plugin's authority: a console or operator line, an item, points, data, meta,
 *       a teleport, a server switch. It takes text only from an argument declared with {@code in-commands = "text"},
 *       and that text may hold no {@code %}, since a placeholder is filled after this check. Everything else,
 *       {@code %args%} included, is a word.
 * </ul>
 *
 * <p>Every step of every chain the command may run is checked before anything runs, so a refused value never leaves a
 * command half done.
 */
public final class CommandLineArguments {

    /** Who acts on a value, which decides what a typed value may be in it. */
    public enum Line {
        /** The player runs or says it as themselves. */
        PLAYER,
        /** The console, an operator or the plugin acts on it. */
        PRIVILEGED
    }

    /** Why a value was refused: the argument, and whether it was held to the text rule rather than to a word. */
    public record Refusal(String argument, boolean text) {

        public Refusal {
            Objects.requireNonNull(argument, "argument");
        }
    }

    /** The action heads that show their value as text, where an argument goes in escaped as words. */
    public static final Set<String> TEXT_HEADS = Set.of(
            "broadcast",
            "message",
            "message-to",
            "whisper",
            "action-bar",
            "actionbar",
            "title",
            "toast",
            "broadcast-json",
            "broadcast-legacy");

    /** The action heads whose value the player runs or says as themselves. */
    private static final Set<String> PLAYER_HEADS =
            Set.of("command", "command-random", "commandevent", "chat", "chat-as-player");

    /** The key everything typed after the command word is kept under, reached as {@code %args%}. */
    public static final String RAW = "args";

    /** A {@code %name%} token in an action; the menu engine also fills {@code %argument_<name>%} and {@code %<name>%}. */
    private static final Pattern TOKEN = Pattern.compile("%([^%\\s]+)%");

    /** What a single word may never hold: a placeholder, a selector, the brackets and quotes of NBT, JSON and strings. */
    private static final String NEVER_IN_A_WORD = "%@{}[]\"'\\";

    private CommandLineArguments() {}

    /** Who acts on the value of an action with this {@code head}, or empty when the action only shows it as text. */
    public static Optional<Line> lineOf(String head) {
        String key = head.strip().toLowerCase(Locale.ROOT);
        if (TEXT_HEADS.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(PLAYER_HEADS.contains(key) ? Line.PLAYER : Line.PRIVILEGED);
    }

    /**
     * The first argument whose value {@code command} would put into an action although it is not what that action
     * allows, or empty when every value fits.
     */
    public static Optional<Refusal> refused(CustomCommand command, Map<String, String> values) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(values, "values");
        for (ActionChain chain : List.of(command.actions(), command.requirementDeny())) {
            for (ActionStep step : chain.steps()) {
                Optional<Line> line = lineOf(head(step.token()));
                if (line.isEmpty()) {
                    continue;
                }
                Optional<Refusal> bad = refusedIn(step.token(), line.get(), command, values);
                if (bad.isPresent()) {
                    return bad;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Only the {@code values} that may go into a {@code line}, each by its own argument's rule. An action that acts on
     * its value is given these and nothing else, so a token that reaches it some other way (the menu engine fills
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

    private static Optional<Refusal> refusedIn(
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
                return Optional.of(new Refusal(name, asText(line, name, argument)));
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
        if (!asText(line, name, argument)) {
            return fitsWord(value);
        }
        return line == Line.PRIVILEGED ? fitsText(value) && value.indexOf('%') < 0 : fitsText(value);
    }

    /** Whether the value kept under {@code name} goes into a {@code line} as text rather than as a word. */
    private static boolean asText(Line line, String name, Optional<CommandArgument> argument) {
        boolean optedIn = argument.map(CommandArgument::textInCommands).orElse(false);
        return switch (line) {
            case PRIVILEGED -> optedIn;
            case PLAYER ->
                optedIn
                        || argument.map(CommandArgument::rest).orElse(false)
                        || (argument.isEmpty() && RAW.equals(name));
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

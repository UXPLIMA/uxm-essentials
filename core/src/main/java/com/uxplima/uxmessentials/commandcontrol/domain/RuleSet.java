package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The pure allow/deny decision behind the command whitelist / blacklist. Built once from the module's config, it
 * holds the {@link RuleMode}, a {@code default} command list, a per-group command list map, and the {@code .bypass}
 * permission node; it decides {@link Decision#ALLOW} or {@link Decision#DENY} for one command and one player's
 * {@link PlayerFacts}. Nothing here is Bukkit-aware. The event handling and the group/permission lookups are
 * adapter-side, so the decision is a plain function that unit-tests exactly.
 *
 * <p>Resolution: a {@code .bypass} holder is always allowed. Otherwise the player's group selects its command list
 * (falling back to the {@code default} list when the group has none or is absent), and the mode reads that list:
 * {@link RuleMode#WHITELIST} allows only a listed command, {@link RuleMode#BLACKLIST} denies only a listed command.
 * Console is never modelled here: the adapter's listener fires only for player commands, so a console command is never
 * gated.
 *
 * <p>An entry is a command and, optionally, the arguments it covers: {@code "gamemode"} covers every
 * {@code /gamemode ...}, {@code "gamemode creative"} covers {@code /gamemode creative} and anything after it, and
 * {@code *} stands for any one argument ({@code "warp *"}). An entry that starts with {@code !} is an exception: it
 * takes the command it names back out of the list. When several entries cover a command, the one naming the most
 * arguments wins, and an exception wins a tie, so {@code ["gamemode", "!gamemode spectator"]} lists every game mode but
 * spectator.
 *
 * <p>Every command and every configured entry is normalised to lowercase without its leading slash, so {@code /Home},
 * {@code home}, and a config entry of {@code "/HOME"} all compare equal.
 */
public final class RuleSet {

    /** The outcome of {@link #decide}: the command may run, or it is blocked. */
    public enum Decision {
        ALLOW,
        DENY
    }

    private final RuleMode mode;
    private final CommandList defaultCommands;
    private final Map<String, CommandList> groupCommands;
    private final String bypassPermission;

    private RuleSet(
            RuleMode mode,
            CommandList defaultCommands,
            Map<String, CommandList> groupCommands,
            String bypassPermission) {
        this.mode = mode;
        this.defaultCommands = defaultCommands;
        this.groupCommands = groupCommands;
        this.bypassPermission = bypassPermission;
    }

    /**
     * Build a rule set, normalising every group key and command entry to lowercase without a leading slash.
     *
     * @param mode whether the lists are read as a whitelist or a blacklist
     * @param defaultCommands the command list applied to a player whose group has no list of its own
     * @param groupCommands the per-group command lists, keyed by permission group name
     * @param bypassPermission the node that always allows a command through (never blank)
     */
    public static RuleSet of(
            RuleMode mode,
            List<String> defaultCommands,
            Map<String, List<String>> groupCommands,
            String bypassPermission) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(defaultCommands, "defaultCommands");
        Objects.requireNonNull(groupCommands, "groupCommands");
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        Map<String, CommandList> groups = new LinkedHashMap<>();
        groupCommands.forEach((group, list) -> {
            Objects.requireNonNull(group, "group name");
            groups.put(normalize(group), CommandList.of(list));
        });
        return new RuleSet(mode, CommandList.of(defaultCommands), Map.copyOf(groups), bypassPermission);
    }

    /**
     * Decide whether {@code command} may run for {@code facts}. The command is read with its arguments, with or without
     * its leading slash. A {@code .bypass} holder is always allowed; otherwise the player's group selects its list (or
     * the default list) and the mode reads it.
     */
    public Decision decide(String command, PlayerFacts facts) {
        return explain(command, facts).decision();
    }

    /**
     * The same decision {@link #decide} makes, with what produced it: the mode that was read, whether the command was
     * listed, and whose list it was. For anything that has to explain the outcome rather than act on it, so an
     * explanation runs the resolution rather than restating it and can never drift from the gate.
     */
    public RuleVerdict explain(String command, PlayerFacts facts) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(facts, "facts");
        List<String> words = words(command);
        String root = words.isEmpty() ? "" : words.getFirst();
        if (facts.hasPermission(bypassPermission)) {
            return new RuleVerdict(root, mode, Decision.ALLOW, RuleVerdict.Reason.BYPASS, Optional.empty());
        }
        Optional<String> group = facts.group();
        boolean listed = listFor(group).listed(words);
        Decision decision =
                switch (mode) {
                    case WHITELIST -> listed ? Decision.ALLOW : Decision.DENY;
                    case BLACKLIST -> listed ? Decision.DENY : Decision.ALLOW;
                };
        RuleVerdict.Reason reason = listed ? RuleVerdict.Reason.LISTED : RuleVerdict.Reason.UNLISTED;
        return new RuleVerdict(root, mode, decision, reason, listOwner(group));
    }

    /**
     * {@link #explain}, and when {@code foldNamespace} is set and the command carries a {@code namespace:} prefix, the
     * bare command too: the bare answer replaces the first only when it is the one that blocks, so
     * {@code /minecraft:gamemode} is stopped exactly when {@code /gamemode} is.
     */
    public RuleVerdict explain(String command, PlayerFacts facts, boolean foldNamespace) {
        RuleVerdict asked = explain(command, facts);
        if (!asked.allowed() || !foldNamespace) {
            return asked;
        }
        return bareCommand(command)
                .map(bare -> explain(bare, facts))
                .filter(bare -> !bare.allowed())
                .orElse(asked);
    }

    /**
     * Whether {@code command} belongs in what the player is shown: the command list, the arguments a client offers,
     * the suggestions. That is true when it may run, and also when something typed after it may, so
     * {@code /gamemode} stays in the list of a whitelist that names only {@code "gamemode creative"}. The namespace is
     * folded as {@link #explain(String, PlayerFacts, boolean)} folds it.
     */
    public boolean visible(String command, PlayerFacts facts, boolean foldNamespace) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(facts, "facts");
        if (!visible(command, facts)) {
            return false;
        }
        return !foldNamespace
                || bareCommand(command).map(bare -> visible(bare, facts)).orElse(true);
    }

    private boolean visible(String command, PlayerFacts facts) {
        if (explain(command, facts).allowed()) {
            return true;
        }
        // A blocked command is still worth showing when an entry further down lets something after it through: a
        // whitelist entry that names more arguments, or a blacklist exception that does.
        return listFor(facts.group()).opensBelow(words(command), mode == RuleMode.BLACKLIST);
    }

    /**
     * Whether the list governing {@code facts} has an entry that names arguments of {@code root}. When none does,
     * every argument of the command shares the command's own answer, so a caller walking the arguments can stop at
     * the command. The namespace is folded as {@link #explain(String, PlayerFacts, boolean)} folds it.
     */
    public boolean namesArgumentsOf(String root, PlayerFacts facts, boolean foldNamespace) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(facts, "facts");
        CommandList list = listFor(facts.group());
        if (list.namesArgumentsOf(normalize(root))) {
            return true;
        }
        return foldNamespace && bareCommand(root).map(list::namesArgumentsOf).orElse(false);
    }

    /**
     * True when this rule set can never deny any command: a blacklist whose every list is empty. The adapter reads
     * this to short-circuit its listener before any per-command work, so an operator who leaves the lists blank pays
     * nothing. A whitelist is never inert: an empty whitelist denies everything, which is a deliberate lock-down.
     */
    public boolean isInert() {
        return mode == RuleMode.BLACKLIST
                && defaultCommands.isEmpty()
                && groupCommands.values().stream().allMatch(CommandList::isEmpty);
    }

    /** The group whose own list governs it, or empty when the {@code default} list does. */
    private Optional<String> listOwner(Optional<String> group) {
        return group.map(RuleSet::normalize).filter(groupCommands::containsKey);
    }

    /** The list that governs {@code group}: the group's own list when it has one, else the {@code default} list. */
    private CommandList listFor(Optional<String> group) {
        Objects.requireNonNull(group, "group");
        return group.map(name -> groupCommands.getOrDefault(normalize(name), defaultCommands))
                .orElse(defaultCommands);
    }

    /** The command with a leading {@code namespace:} taken off its first word, or empty when it carries none. */
    private static Optional<String> bareCommand(String command) {
        List<String> words = words(command);
        if (words.isEmpty()) {
            return Optional.empty();
        }
        return NamespaceBypassRule.bareRoot(words.getFirst()).map(root -> {
            List<String> bare = new ArrayList<>(words);
            bare.set(0, root);
            return String.join(" ", bare);
        });
    }

    /**
     * The words of a command, lowercase, without its leading slash; empty for a blank command. A quoted argument is
     * one word without its quotes, read the way Brigadier reads a quoted string ({@code \\} escapes the next
     * character), so {@code /warp "shop" set} meets the same entries as {@code /warp shop set}.
     */
    private static List<String> words(String command) {
        String text = normalize(command);
        List<String> words = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char first = text.charAt(i);
            if (Character.isWhitespace(first)) {
                i++;
                continue;
            }
            StringBuilder word = new StringBuilder();
            if ((first == '"' || first == '\'') && !words.isEmpty()) {
                i++;
                boolean escaped = false;
                for (; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (escaped) {
                        word.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == first) {
                        i++;
                        break;
                    } else {
                        word.append(c);
                    }
                }
            } else {
                for (; i < text.length() && !Character.isWhitespace(text.charAt(i)); i++) {
                    word.append(text.charAt(i));
                }
            }
            words.add(word.toString());
        }
        return List.copyOf(words);
    }

    /** Lowercase, trim, and strip a single leading slash so labels compare uniformly. */
    private static String normalize(String label) {
        String trimmed = label.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }

    /** One command list: its entries grouped by the command they start with. */
    private record CommandList(Map<String, List<Entry>> byRoot) {

        static CommandList of(List<String> raw) {
            Objects.requireNonNull(raw, "raw");
            Map<String, List<Entry>> byRoot = new LinkedHashMap<>();
            for (String line : raw) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String entry = line.strip();
                boolean exception = entry.startsWith("!");
                List<String> words = words(exception ? entry.substring(1) : entry);
                if (!words.isEmpty()) {
                    byRoot.computeIfAbsent(words.getFirst(), ignored -> new ArrayList<>())
                            .add(new Entry(words.subList(1, words.size()), exception));
                }
            }
            Map<String, List<Entry>> frozen = new LinkedHashMap<>();
            byRoot.forEach((root, entries) -> frozen.put(root, List.copyOf(new LinkedHashSet<>(entries))));
            return new CommandList(Map.copyOf(frozen));
        }

        boolean isEmpty() {
            return byRoot.isEmpty();
        }

        boolean namesArgumentsOf(String root) {
            return byRoot.getOrDefault(root, List.of()).stream()
                    .anyMatch(entry -> !entry.args().isEmpty());
        }

        /** Whether the entry that covers {@code words} most closely lists it, rather than being an exception. */
        boolean listed(List<String> words) {
            if (words.isEmpty()) {
                return false;
            }
            List<String> args = words.subList(1, words.size());
            @Nullable Entry best = null;
            for (Entry entry : byRoot.getOrDefault(words.getFirst(), List.of())) {
                if (entry.covers(args)
                        && (best == null
                                || entry.args().size() > best.args().size()
                                || (entry.args().size() == best.args().size() && entry.exception()))) {
                    best = entry;
                }
            }
            return best != null && !best.exception();
        }

        /** Whether an entry naming more than {@code words}, and starting with them, is an exception or not. */
        boolean opensBelow(List<String> words, boolean exception) {
            if (words.isEmpty()) {
                return false;
            }
            List<String> args = words.subList(1, words.size());
            return byRoot.getOrDefault(words.getFirst(), List.of()).stream()
                    .anyMatch(entry -> entry.exception() == exception && entry.reachesPast(args));
        }
    }

    /**
     * One entry under its command: the arguments it names ({@code *} for any one) and whether it is an exception.
     */
    private record Entry(List<String> args, boolean exception) {

        Entry {
            args = List.copyOf(args);
        }

        /** Whether this entry covers a command typed with {@code typed} after its root. */
        boolean covers(List<String> typed) {
            return args.size() <= typed.size() && startsLike(typed, args.size());
        }

        /** Whether this entry names more arguments than {@code typed} and starts with them. */
        boolean reachesPast(List<String> typed) {
            return args.size() > typed.size() && startsLike(typed, typed.size());
        }

        private boolean startsLike(List<String> typed, int count) {
            for (int i = 0; i < count; i++) {
                String expected = args.get(i);
                if (!expected.equals("*") && !expected.equals(typed.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}

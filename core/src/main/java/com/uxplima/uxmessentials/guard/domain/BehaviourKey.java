package com.uxplima.uxmessentials.guard.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What a region does to a player inside it, apart from protection. Each key has a value kind the setter checks,
 * so a typo is refused when typed rather than ignored at runtime.
 */
public enum BehaviourKey {
    ENTER_PUSH_BACK("enter.push-back", Kind.BOOLEAN),
    ENTER_DENY_MESSAGE("enter.deny-message", Kind.TEXT),
    EXIT_DENY_MESSAGE("exit.deny-message", Kind.TEXT),
    MESSAGE_ENTER("message.enter", Kind.TEXT),
    MESSAGE_EXIT("message.exit", Kind.TEXT),
    MESSAGE_CHANNEL("message.channel", Kind.CHANNEL),
    ACTIONS_ENTER("actions.enter", Kind.ACTIONS),
    ACTIONS_EXIT("actions.exit", Kind.ACTIONS),
    ACTIONS_INTERVAL("actions.interval", Kind.ACTIONS),
    ACTIONS_INTERVAL_SECONDS("actions.interval-seconds", Kind.POSITIVE_INT),
    FLY("fly", Kind.TRISTATE),
    GLIDE("glide", Kind.TRISTATE),
    GOD("god", Kind.TRISTATE),
    WALK_SPEED("walk-speed", Kind.SPEED),
    FLY_SPEED("fly-speed", Kind.SPEED),
    GAME_MODE("game-mode", Kind.GAME_MODE),
    INVINCIBLE("invincible", Kind.BOOLEAN),
    KEEP_INVENTORY("keep-inventory", Kind.BOOLEAN),
    KEEP_EXP("keep-exp", Kind.BOOLEAN),
    HEAL("heal", Kind.AMOUNT_EVERY),
    FEED("feed", Kind.AMOUNT_EVERY),
    EFFECTS_GIVE("effects.give", Kind.LIST),
    EFFECTS_BLOCK("effects.block", Kind.LIST),
    TIME("time", Kind.TIME),
    WEATHER("weather", Kind.WEATHER),
    VOID_TELEPORT("void-teleport", Kind.VOID),
    BLOCKS_BREAK_ALLOW("blocks.break.allow", Kind.LIST),
    BLOCKS_BREAK_DENY("blocks.break.deny", Kind.LIST),
    BLOCKS_PLACE_ALLOW("blocks.place.allow", Kind.LIST),
    BLOCKS_PLACE_DENY("blocks.place.deny", Kind.LIST),
    ITEMS_USE_DENY("items.use.deny", Kind.LIST),
    SPAWN_DENY("spawn.deny", Kind.LIST),
    COMMANDS_DENY("commands.deny", Kind.LIST),
    COMMANDS_ALLOW("commands.allow", Kind.LIST);

    /** The shape of a behaviour's value. */
    public enum Kind {
        /** {@code true} or {@code false}. */
        BOOLEAN,
        /** {@code allow}, {@code deny} or {@code keep} (leave the player's own setting). */
        TRISTATE,
        /** Any text; MiniMessage for messages. */
        TEXT,
        /** {@code chat}, {@code title}, {@code actionbar} or {@code bossbar}. */
        CHANNEL,
        /** Essentials click actions, one per line, the same lines menus and NPCs use. */
        ACTIONS,
        /** A whole number above zero. */
        POSITIVE_INT,
        /** A speed from 0 to 1 as Bukkit counts it, 0.2 being normal walking. */
        SPEED,
        /** {@code survival}, {@code creative}, {@code adventure} or {@code spectator}. */
        GAME_MODE,
        /** {@code <amount> <seconds>}: health or food given every so many seconds. */
        AMOUNT_EVERY,
        /** Comma separated names. */
        LIST,
        /** A tick of the day 0 to 23999, or {@code day}, {@code noon}, {@code night}, {@code midnight}. */
        TIME,
        /** {@code clear} or {@code rain}. */
        WEATHER,
        /** {@code <y> <world> <x> <y> <z>} or {@code <y> spawn}: below y the player is sent there. */
        VOID
    }

    private final String key;
    private final Kind kind;

    BehaviourKey(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    /** The key as written in commands and storage. */
    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    /** Find a key by its written form. */
    public static Optional<BehaviourKey> parse(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(k -> k.key.equals(text)).findFirst();
    }

    /** Normalise {@code raw} for this key, or empty when it does not fit the kind. */
    public Optional<String> normalise(String raw) {
        String text = raw.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case BOOLEAN ->
                switch (lower) {
                    case "true", "yes", "on", "evet", "acik" -> Optional.of("true");
                    case "false", "no", "off", "hayir", "kapali" -> Optional.of("false");
                    default -> Optional.empty();
                };
            case TRISTATE ->
                switch (lower) {
                    case "allow", "true", "on", "izin" -> Optional.of("allow");
                    case "deny", "false", "off", "yasak" -> Optional.of("deny");
                    case "keep", "koru" -> Optional.of("keep");
                    default -> Optional.empty();
                };
            case TEXT, ACTIONS -> text.isEmpty() ? Optional.empty() : Optional.of(text);
            case CHANNEL ->
                switch (lower) {
                    case "chat", "title", "actionbar", "bossbar" -> Optional.of(lower);
                    default -> Optional.empty();
                };
            case POSITIVE_INT -> parseInt(lower).filter(n -> n > 0).map(String::valueOf);
            case SPEED -> parseDouble(lower).filter(n -> n >= 0 && n <= 1).map(String::valueOf);
            case GAME_MODE ->
                switch (lower) {
                    case "survival", "creative", "adventure", "spectator" -> Optional.of(lower);
                    default -> Optional.empty();
                };
            case AMOUNT_EVERY -> {
                String[] parts = lower.split("\\s+", -1);
                if (parts.length != 2) {
                    yield Optional.empty();
                }
                Optional<Double> amount = parseDouble(parts[0]).filter(n -> n > 0);
                Optional<Integer> seconds = parseInt(parts[1]).filter(n -> n > 0);
                yield amount.isPresent() && seconds.isPresent()
                        ? Optional.of(amount.get() + " " + seconds.get())
                        : Optional.empty();
            }
            case LIST -> {
                String joined = String.join(
                        ",",
                        Arrays.stream(lower.split("[,\\s]+", -1))
                                .filter(s -> !s.isEmpty())
                                .distinct()
                                .toList());
                yield joined.isEmpty() ? Optional.empty() : Optional.of(joined);
            }
            case TIME ->
                switch (lower) {
                    case "day" -> Optional.of("1000");
                    case "noon" -> Optional.of("6000");
                    case "night" -> Optional.of("13000");
                    case "midnight" -> Optional.of("18000");
                    default -> parseInt(lower).filter(n -> n >= 0 && n < 24000).map(String::valueOf);
                };
            case WEATHER ->
                switch (lower) {
                    case "clear", "rain" -> Optional.of(lower);
                    default -> Optional.empty();
                };
            case VOID -> {
                String[] parts = text.split("\\s+", -1);
                if (parts.length == 2
                        && parts[1].equalsIgnoreCase("spawn")
                        && parseInt(parts[0]).isPresent()) {
                    yield Optional.of(parts[0] + " spawn");
                }
                if (parts.length == 5
                        && parseInt(parts[0]).isPresent()
                        && parseDouble(parts[2]).isPresent()
                        && parseDouble(parts[3]).isPresent()
                        && parseDouble(parts[4]).isPresent()) {
                    yield Optional.of(String.join(" ", parts));
                }
                yield Optional.empty();
            }
        };
    }

    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

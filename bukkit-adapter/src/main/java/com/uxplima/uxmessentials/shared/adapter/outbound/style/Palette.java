package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.format.TextColor;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The colours of the interface, either the shipped ones or the ones a server wrote down.
 *
 * <p>A theme file has three layers. {@code palette} is the server's own colours under the server's own names,
 * and nothing in this plugin refers to those names, so a server may rename or drop any of them. {@code roles}
 * is the vocabulary the interface speaks: {@code accent}, {@code value}, {@code good}. Each role points at a
 * palette name or a hex code, and the map is open, so a server may add a role of its own and use it as a tag
 * in the files it writes. {@code wheel} is an ordered list of colours a gradient takes its second stop from.
 *
 * <p>Every role has a shipped colour behind it, so a server with no file, a file with three lines, and a file
 * with a role this version never heard of all work.
 */
@NullMarked
public final class Palette {

    private static final Map<String, String> SHIPPED = shippedColours();

    private final Map<String, TextColor> roles;
    private final List<TextColor> wheel;

    private Palette(Map<String, TextColor> roles, List<TextColor> wheel) {
        this.roles = Map.copyOf(roles);
        this.wheel = List.copyOf(wheel);
    }

    /** The colours this plugin ships, which is what a server that wrote no file sees. */
    public static Palette shipped() {
        Map<String, TextColor> roles = new LinkedHashMap<>();
        SHIPPED.forEach((role, hex) -> roles.put(role, parse(hex)));
        return new Palette(roles, List.of());
    }

    /**
     * The colours in {@code node}, with the shipped colour behind every role the file leaves out.
     *
     * @throws IllegalArgumentException when a value is neither a colour nor a name the palette holds, which
     *     an operator has to see at load rather than as a black message in the game
     */
    public static Palette from(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        Map<String, TextColor> named = colours(node.node("palette"));
        Map<String, TextColor> roles = new LinkedHashMap<>();
        SHIPPED.forEach((role, hex) -> roles.put(role, parse(hex)));
        roles.putAll(colours(node.node("colours"), named));
        roles.putAll(colours(node.node("roles"), named));
        return new Palette(roles, wheel(node.node("wheel"), named));
    }

    /** The colour of {@code role}, or the body colour when this palette does not know the role. */
    public TextColor role(String role) {
        Objects.requireNonNull(role, "role");
        TextColor found = roles.get(role);
        return found != null ? found : Objects.requireNonNull(roles.get("body"), "body");
    }

    /** Whether {@code role} is a role of this palette, which is what makes a word a tag. */
    public boolean has(String role) {
        Objects.requireNonNull(role, "role");
        return roles.containsKey(role);
    }

    /** Every role this palette holds, so the tag resolver can offer one tag per role. */
    public Map<String, TextColor> roles() {
        return roles;
    }

    /**
     * The colour after {@code colour} on the wheel, or {@code fallback} when the wheel does not hold it.
     *
     * <p>A gradient runs from a colour to its neighbour, and the wheel is where a server says which colour
     * that is. A server with no wheel keeps the lighter shades this plugin ships.
     */
    public TextColor next(TextColor colour, TextColor fallback) {
        Objects.requireNonNull(colour, "colour");
        Objects.requireNonNull(fallback, "fallback");
        int at = wheel.indexOf(colour);
        return at < 0 ? fallback : wheel.get((at + 1) % wheel.size());
    }

    /**
     * The arc at {@code position}: that colour of the wheel and the one after it, or nothing when the wheel
     * is shorter than two colours.
     *
     * <p>This is what a file with several headings on one screen asks for. It names the position of the
     * heading and never a colour, so the headings differ from each other and a server that re-colours the
     * wheel re-colours them all. The wheel wraps, so a screen longer than the wheel keeps working.
     */
    public List<TextColor> arc(int position) {
        if (wheel.size() < 2) {
            return List.of();
        }
        int start = Math.floorMod(position, wheel.size());
        return List.of(wheel.get(start), wheel.get((start + 1) % wheel.size()));
    }

    public TextColor accent() {
        return role("accent");
    }

    public TextColor value() {
        return role("value");
    }

    public TextColor body() {
        return role("body");
    }

    public TextColor subtext() {
        return role("subtext");
    }

    public TextColor muted() {
        return role("muted");
    }

    public TextColor dim() {
        return role("dim");
    }

    public TextColor icon() {
        return role("icon");
    }

    public TextColor crumb() {
        return role("crumb");
    }

    public TextColor good() {
        return role("good");
    }

    public TextColor bad() {
        return role("bad");
    }

    public TextColor gold() {
        return role("warn");
    }

    public TextColor money() {
        return role("money");
    }

    public TextColor level() {
        return role("level");
    }

    public TextColor cta() {
        return role("cta");
    }

    public TextColor info() {
        return role("info");
    }

    public TextColor rank() {
        return role("rank");
    }

    public TextColor event() {
        return role("event");
    }

    /** The lighter half of the positive ramp: the wheel's next colour, or the shade this plugin ships. */
    public TextColor emerald() {
        return next(good(), role("emerald"));
    }

    /** The lighter half of the denial ramp. */
    public TextColor rose() {
        return next(bad(), role("rose"));
    }

    /** The lighter half of the attention ramp. */
    public TextColor yellow() {
        return next(gold(), role("yellow"));
    }

    private static Map<String, TextColor> colours(ConfigurationNode node) {
        return colours(node, Map.of());
    }

    private static Map<String, TextColor> colours(ConfigurationNode node, Map<String, TextColor> named) {
        Map<String, TextColor> colours = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.childrenMap().entrySet()) {
            String value = child.getValue().getString();
            if (value != null) {
                colours.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), resolve(value, named));
            }
        }
        return colours;
    }

    private static List<TextColor> wheel(ConfigurationNode node, Map<String, TextColor> named) {
        List<TextColor> wheel = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                wheel.add(resolve(value, named));
            }
        }
        return wheel;
    }

    private static TextColor resolve(String value, Map<String, TextColor> named) {
        TextColor found = named.get(value.toLowerCase(Locale.ROOT));
        return found != null ? found : parse(value);
    }

    private static TextColor parse(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        if (parsed == null) {
            throw new IllegalArgumentException("not a colour and not a palette name: " + hex);
        }
        return parsed;
    }

    /**
     * The shipped colours. Sky is the brand, ice is a value, white and subtext are the reading colours, and
     * green, red and gold are kept for outcome, denial and attention.
     */
    private static Map<String, String> shippedColours() {
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("accent", "#38b6ff");
        colours.put("value", "#8fd9ff");
        colours.put("body", "#ffffff");
        colours.put("subtext", "#dde8f0");
        colours.put("muted", "#93a4b3");
        colours.put("dim", "#6b7886");
        colours.put("icon", "#8a93a1");
        colours.put("crumb", "#565f6b");
        colours.put("good", "#5be38c");
        colours.put("bad", "#ff6b6b");
        colours.put("warn", "#ffc93c");
        colours.put("money", "#ffc93c");
        colours.put("level", "#ffc93c");
        colours.put("cta", "#ffc93c");
        colours.put("info", "#4fd6e8");
        colours.put("rank", "#b68cff");
        colours.put("event", "#ff8fd0");
        // A plain title carries the reading colour rather than a colour of its own.
        colours.put("title", "#ffffff");
        // The lighter half of each ramp, used when the file names no wheel to take a neighbour from.
        colours.put("emerald", "#45d9a6");
        colours.put("rose", "#ff7aa8");
        colours.put("yellow", "#ffe15c");
        return Map.copyOf(colours);
    }
}

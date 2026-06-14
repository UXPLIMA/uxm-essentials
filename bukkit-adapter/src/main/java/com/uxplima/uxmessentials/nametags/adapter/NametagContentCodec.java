package com.uxplima.uxmessentials.nametags.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses {@code modules/nametags/config.conf} into a {@link NametagConfig} (the named formats the renderer selects
 * among per wearer) plus the named animations and the global render cadence. Two operator-authored shapes are
 * accepted, mirroring the tablist and scoreboard codecs.
 *
 * <p><strong>Multiple formats (current shape).</strong> A {@code formats { <name> { condition, priority, lines[],
 * appearance { … }, visibility { … } } }} map, one entry per named format. <strong>Single nametag (back-compat).</strong>
 * When there is no {@code formats { … }} block but a top-level {@code nametag { lines, appearance, visibility }} block
 * exists, it is wrapped as one format named {@code default} with an always-true condition and priority {@code 0}.
 *
 * <p>An optional top-level {@code animations { … }} block declares named animations a line references as
 * {@code %anim_<name>%}, parsed by the shared {@link AnimationDef#parseAll} the other HUD codecs use, so the grammar is
 * identical and never drifts. The parse is tolerant throughout: a format with no {@code lines} is dropped (it would
 * render nothing), a bad appearance value falls back to the default for that field (the {@link NametagAppearance}
 * constructor still enforces the range invariants, and an invalid one is logged and skipped), and an absent or
 * non-positive {@code refresh-ticks} falls back to one second. An empty or virtual root yields {@link Parsed#inert()}.
 *
 * <p><strong>Tie-break.</strong> HOCON does not preserve declaration order, so the codec emits the formats sorted by
 * name; on equal priority {@link NametagConfig#select} keeps the alphabetically-first format name (deterministic and
 * reload-stable).
 */
@NullMarked
final class NametagContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final boolean DEFAULT_HIDE_VANILLA_NAME = true;

    private NametagContentCodec() {}

    /**
     * The parsed nametag config: the named formats the renderer selects among, the named animations the renderer
     * expands {@code %anim_<name>%} tokens against, the global render cadence the timer re-reads each reschedule, and
     * whether a wearer's vanilla above-head name is hidden while their custom nametag is live.
     */
    record Parsed(
            NametagConfig formats, List<AnimationDef> animations, Duration refreshInterval, boolean hideVanillaName) {
        Parsed {
            Objects.requireNonNull(formats, "formats");
            animations = List.copyOf(Objects.requireNonNull(animations, "animations"));
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }

        /** The do-nothing default an absent or unreadable config yields: no formats, no animations, once a second. */
        static Parsed inert() {
            return new Parsed(
                    NametagConfig.empty(),
                    List.of(),
                    Duration.ofMillis(DEFAULT_REFRESH_TICKS * MILLIS_PER_TICK),
                    DEFAULT_HIDE_VANILLA_NAME);
        }
    }

    /** Parse {@code root}; an empty or virtual root yields {@link Parsed#inert()}. {@code log} reports skipped entries. */
    static Parsed read(ConfigurationNode root, Logger log) {
        Objects.requireNonNull(log, "log");
        if (root.virtual() || root.empty()) {
            return Parsed.inert();
        }
        List<AnimationDef> animations = AnimationDef.parseAll(root.node("animations"), log);
        Duration interval = refreshInterval(root.node("refresh-ticks"));
        boolean hideVanillaName = root.node("hide-vanilla-name").getBoolean(DEFAULT_HIDE_VANILLA_NAME);
        ConfigurationNode formats = root.node("formats");
        if (!formats.virtual() && formats.isMap()) {
            return new Parsed(readFormats(formats, log), animations, interval, hideVanillaName);
        }
        return new Parsed(readSingleFormat(root.node("nametag"), log), animations, interval, hideVanillaName);
    }

    private static NametagConfig readFormats(ConfigurationNode formats, Logger log) {
        List<NametagFormat> parsed = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                formats.childrenMap().entrySet()) {
            readFormat(String.valueOf(entry.getKey()), entry.getValue(), log).ifPresent(parsed::add);
        }
        // HOCON does not preserve declaration order; sort by name for the documented, reload-stable tie-break.
        parsed.sort(Comparator.comparing(NametagFormat::name));
        return new NametagConfig(parsed);
    }

    private static Optional<NametagFormat> readFormat(String name, ConfigurationNode node, Logger log) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        List<String> lines = strings(node.node("lines"));
        if (lines.isEmpty()) {
            // A format with no lines renders nothing; drop it rather than tracking an empty nametag.
            return Optional.empty();
        }
        DisplayCondition condition =
                ConditionParser.parse(node.node("condition").getString());
        int priority = node.node("priority").getInt(0);
        return appearance(node.node("appearance"), log)
                .map(appearance -> new NametagFormat(
                        name, condition, priority, lines, appearance, visibility(node.node("visibility"))));
    }

    // Back-compat: wrap a top-level nametag { … } block as the single implicit default format (always-true, priority
    // 0).
    // A block with no lines yields no formats, like a missing block.
    private static NametagConfig readSingleFormat(ConfigurationNode nametag, Logger log) {
        if (nametag.virtual() || !nametag.isMap()) {
            return NametagConfig.empty();
        }
        List<String> lines = strings(nametag.node("lines"));
        if (lines.isEmpty()) {
            return NametagConfig.empty();
        }
        return appearance(nametag.node("appearance"), log)
                .map(appearance -> new NametagConfig(List.of(new NametagFormat(
                        "default",
                        DisplayCondition.always(),
                        0,
                        lines,
                        appearance,
                        visibility(nametag.node("visibility"))))))
                .orElseGet(NametagConfig::empty);
    }

    private static Optional<NametagAppearance> appearance(ConfigurationNode node, Logger log) {
        NametagAppearance defaults = NametagAppearance.defaults();
        try {
            return Optional.of(new NametagAppearance(
                    node.node("billboard").getString(defaults.billboard()),
                    node.node("see-through").getBoolean(defaults.seeThrough()),
                    optionalString(node.node("background-color")),
                    optionalInt(node.node("background-opacity")),
                    node.node("text-shadow").getBoolean(defaults.textShadow()),
                    optionalString(node.node("glow-color")),
                    optionalInt(node.node("line-width")),
                    optionalDouble(node.node("view-range")),
                    node.node("scale").getDouble(defaults.scale()),
                    node.node("y-offset").getDouble(defaults.yOffset()),
                    node.node("hide-through-blocks").getBoolean(defaults.hideThroughBlocks()),
                    optionalInt(node.node("obscured-opacity"))));
        } catch (IllegalArgumentException invalid) {
            // A range/structural violation (e.g. scale <= 0, opacity out of 0..255) falls back to the safe default
            // appearance rather than failing the whole format; the format still renders with sane visuals.
            log.warn("event=nametag_appearance_invalid reason={}", String.valueOf(invalid.getMessage()));
            return Optional.of(defaults);
        }
    }

    private static NametagVisibility visibility(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return NametagVisibility.alwaysVisible();
        }
        NametagVisibility defaults = NametagVisibility.alwaysVisible();
        return new NametagVisibility(
                ConditionParser.parse(node.node("show-when").getString()),
                node.node("hide-while-sneaking").getBoolean(defaults.hideWhileSneaking()),
                node.node("respect-vanish").getBoolean(defaults.respectVanish()),
                viewerDistance(node.node("viewer-distance")));
    }

    // The viewer-distance is a flat block radius for the renderer's eligible-viewer cull (distinct from the
    // appearance view-range, which is Paper's render-distance multiplier). A negative authored value is treated as
    // absent so a typo never blanks every nearby nametag; 0 (no cull) is kept verbatim.
    private static OptionalDouble viewerDistance(ConfigurationNode node) {
        if (node.virtual()) {
            return OptionalDouble.empty();
        }
        double blocks = node.getDouble();
        return Double.isFinite(blocks) && blocks >= 0 ? OptionalDouble.of(blocks) : OptionalDouble.empty();
    }

    private static Duration refreshInterval(ConfigurationNode node) {
        long ticks = node.getLong(DEFAULT_REFRESH_TICKS);
        if (ticks <= 0L) {
            ticks = DEFAULT_REFRESH_TICKS;
        }
        return Duration.ofMillis(ticks * MILLIS_PER_TICK);
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            // A blank entry is kept so an operator can author a spacer line; a missing value is skipped.
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static OptionalInt optionalInt(ConfigurationNode node) {
        return node.virtual() ? OptionalInt.empty() : OptionalInt.of(node.getInt());
    }

    private static OptionalDouble optionalDouble(ConfigurationNode node) {
        return node.virtual() ? OptionalDouble.empty() : OptionalDouble.of(node.getDouble());
    }
}

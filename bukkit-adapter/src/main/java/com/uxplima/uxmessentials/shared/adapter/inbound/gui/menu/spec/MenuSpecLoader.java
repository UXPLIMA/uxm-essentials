package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Turns a menu's HOCON spec into the immutable {@link MenuSpec} model the renderer and runtime consume. Parsing
 * is fail-fast: any malformed value — a bad row count, an out-of-range slot, an unknown item type — surfaces as
 * a {@link MenuSpecException} naming the file, so a typo is a loud configuration error the operator fixes rather
 * than a silently half-built menu. The model produced here never references Bukkit; material and text strings
 * are carried verbatim for the Bukkit-side renderer to resolve later.
 */
public final class MenuSpecLoader {

    /** Maps the HOCON click keys (both snake and kebab spellings) onto the {@link ClickKind} enum. */
    private static final Map<String, ClickKind> CLICK_KEYS = clickKeys();

    /** Parse a spec held in memory. Primarily a test seam; production loads go through {@link #load(Path)}. */
    public MenuSpec parse(String hocon) {
        Objects.requireNonNull(hocon, "hocon");
        try {
            ConfigurationNode root = HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(hocon)))
                    .build()
                    .load();
            return parseRoot(root, "<string>");
        } catch (MenuSpecException already) {
            throw already;
        } catch (Exception failure) {
            throw new MenuSpecException("failed to parse menu spec from string", failure);
        }
    }

    /** Load and parse a spec from disk, wrapping any I/O or parse failure with the file path. */
    public MenuSpec load(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            return parseRoot(root, file.toString());
        } catch (MenuSpecException already) {
            throw already;
        } catch (Exception failure) {
            throw new MenuSpecException("failed to load menu spec " + file, failure);
        }
    }

    private MenuSpec parseRoot(ConfigurationNode root, String origin) {
        int rows = root.node("rows").getInt(0);
        ConfigurationNode refresh = root.node("refresh");
        RefreshSpec refreshSpec = new RefreshSpec(
                refresh.node("enabled").getBoolean(false),
                refresh.node("interval-ticks").getInt(0));
        Map<String, MenuItemSpec> items = parseItems(root.node("items"), rows, origin);
        try {
            return new MenuSpec(
                    root.node("title").getString(""),
                    rows,
                    refreshSpec,
                    refs(root.node("open-requirement")),
                    refs(root.node("open-actions")),
                    refs(root.node("close-actions")),
                    items);
        } catch (IllegalArgumentException invalid) {
            throw new MenuSpecException("invalid menu in " + origin + ": " + invalid.getMessage(), invalid);
        }
    }

    private Map<String, MenuItemSpec> parseItems(ConfigurationNode itemsNode, int rows, String origin) {
        Map<String, MenuItemSpec> items = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                itemsNode.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            try {
                items.put(id, parseItem(entry.getValue(), rows));
            } catch (RuntimeException invalid) {
                throw new MenuSpecException(
                        "invalid item '" + id + "' in " + origin + ": " + invalid.getMessage(), invalid);
            }
        }
        return items;
    }

    private MenuItemSpec parseItem(ConfigurationNode node, int rows) {
        SlotSet slots = SlotSet.parse(slotTokens(node), rows * 9);
        return new MenuItemSpec(
                slots,
                node.node("priority").getInt(0),
                node.node("material").getString("STONE"),
                node.node("name").getString(""),
                strings(node.node("lore")),
                parseDecor(node.node("decor")),
                refs(node.node("view")),
                parseClick(node.node("click")),
                node.node("update").getBoolean(false),
                parseList(node.node("list"), rows),
                itemType(node.node("type")));
    }

    private List<String> slotTokens(ConfigurationNode node) {
        ConfigurationNode slots = node.node("slots");
        if (!slots.virtual() && !slots.isNull()) {
            return strings(slots);
        }
        return List.of(String.valueOf(node.node("slot").getInt(0)));
    }

    private ItemType itemType(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return ItemType.NONE;
        }
        return ItemType.valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
    }

    private ClickSpec parseClick(ConfigurationNode node) {
        Map<ClickKind, List<Ref>> actions = new EnumMap<>(ClickKind.class);
        if (!node.virtual() && !node.isNull()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                    node.childrenMap().entrySet()) {
                ClickKind kind = CLICK_KEYS.get(String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT));
                if (kind != null) {
                    actions.put(kind, refs(entry.getValue()));
                }
            }
        }
        // v1 keeps per-gesture conditions empty; visibility is gated by the item-level `view` list instead.
        return new ClickSpec(actions, Map.of());
    }

    /**
     * Parse the {@code decor} block. Beyond the original amount/model-data/glow/flags it reads the native
     * {@link RichMeta}, whose grammar is: {@code unbreakable=true}, {@code enchantments=["sharpness:5"]},
     * {@code stored-enchantments=["mending:1"]}, {@code leather-color="#A1FF33"} (hex / {@code "r,g,b"} / named),
     * {@code potion{ type=STRENGTH, color="#00AAFF", effects=["speed:1:600"] }},
     * {@code banner{ patterns=["stripe_top:red"] }}, {@code trim{ material=diamond, pattern=sentry }},
     * {@code damage=100}, {@code item-model="minecraft:diamond_sword"}. It also reads the native
     * {@link DataComponents}: {@code rarity=EPIC}, {@code tooltip-style="minecraft:fancy"},
     * {@code hide-tooltip=true}, {@code enchant-glint=true}, {@code enchantable=10},
     * {@code attribute-modifiers=["generic.attack_damage:5:add_number:hand"]},
     * {@code food{ nutrition=4, saturation=2.4, can-always-eat=true }},
     * {@code tool{ default-mining-speed=1.0, damage-per-block=2 }}. The {@code amount} and {@code model-data} values
     * may instead be a {@code %placeholder%} string, in which case the literal token is carried as the dynamic
     * override (the renderer resolves it to a number each draw) and the static default is kept.
     */
    private ItemDecor parseDecor(ConfigurationNode node) {
        ConfigurationNode amountNode = node.node("amount");
        ConfigurationNode modelNode = node.node("model-data");
        Optional<String> dynamicAmount = dynamicToken(amountNode);
        Optional<String> dynamicModel = dynamicToken(modelNode);
        int amount = dynamicAmount.isPresent() ? 1 : amountNode.getInt(1);
        Optional<Integer> model = dynamicModel.isPresent() || modelNode.virtual() || modelNode.isNull()
                ? Optional.empty()
                : Optional.of(modelNode.getInt());
        RichMeta meta = new RichMeta(
                node.node("unbreakable").getBoolean(false),
                strings(node.node("enchantments")),
                strings(node.node("stored-enchantments")),
                optionalString(node.node("leather-color")),
                parsePotion(node.node("potion")),
                strings(node.node("banner").node("patterns")),
                parseTrim(node.node("trim")),
                optionalInt(node.node("damage")),
                dynamicAmount,
                dynamicModel,
                optionalString(node.node("item-model")),
                parseDataComponents(node));
        return new ItemDecor(amount, model, node.node("glow").getBoolean(false), strings(node.node("flags")), meta);
    }

    /** Read the native data-component sub-nodes; every value stays a string/int/double/bool token for the renderer. */
    private DataComponents parseDataComponents(ConfigurationNode node) {
        return new DataComponents(
                optionalString(node.node("rarity")),
                optionalString(node.node("tooltip-style")),
                optionalBoolean(node.node("hide-tooltip")),
                optionalBoolean(node.node("enchant-glint")),
                optionalInt(node.node("enchantable")),
                strings(node.node("attribute-modifiers")),
                parseFood(node.node("food")),
                parseTool(node.node("tool")));
    }

    private Optional<DataComponents.FoodSpec> parseFood(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new DataComponents.FoodSpec(
                optionalInt(node.node("nutrition")),
                optionalDouble(node.node("saturation")),
                optionalBoolean(node.node("can-always-eat"))));
    }

    private Optional<DataComponents.ToolSpec> parseTool(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new DataComponents.ToolSpec(
                optionalDouble(node.node("default-mining-speed")), optionalInt(node.node("damage-per-block"))));
    }

    private RichMeta.PotionSpec parsePotion(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return RichMeta.PotionSpec.NONE;
        }
        return new RichMeta.PotionSpec(
                optionalString(node.node("type")), optionalString(node.node("color")), strings(node.node("effects")));
    }

    private Optional<RichMeta.TrimSpec> parseTrim(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        String material = node.node("material").getString("");
        String pattern = node.node("pattern").getString("");
        if (material.isBlank() || pattern.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RichMeta.TrimSpec(material, pattern));
    }

    /** The literal {@code %placeholder%} token a scalar node holds, present only when its value contains a {@code %}. */
    private static Optional<String> dynamicToken(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        String raw = node.getString("");
        return raw.contains("%") ? Optional.of(raw) : Optional.empty();
    }

    /** A node's trimmed-non-blank string value, or empty when the node is absent or blank. */
    private static Optional<String> optionalString(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** A node's int value, or empty when the node is absent. */
    private static Optional<Integer> optionalInt(ConfigurationNode node) {
        return node.virtual() || node.isNull() ? Optional.empty() : Optional.of(node.getInt());
    }

    /** A node's double value, or empty when the node is absent. */
    private static Optional<Double> optionalDouble(ConfigurationNode node) {
        return node.virtual() || node.isNull() ? Optional.empty() : Optional.of(node.getDouble());
    }

    /** A node's boolean value, or empty when the node is absent, so an unset toggle never overrides the item. */
    private static Optional<Boolean> optionalBoolean(ConfigurationNode node) {
        return node.virtual() || node.isNull() ? Optional.empty() : Optional.of(node.getBoolean());
    }

    private Optional<ListSpec> parseList(ConfigurationNode node, int rows) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        Ref source = Ref.parse(Objects.requireNonNull(node.node("source").getString(), "list.source"));
        return Optional.of(new ListSpec(source, parseItem(node.node("template"), rows)));
    }

    private List<Ref> refs(ConfigurationNode node) {
        List<Ref> refs = new ArrayList<>();
        for (String raw : strings(node)) {
            refs.add(Ref.parse(raw));
        }
        return refs;
    }

    private List<String> strings(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        try {
            @Nullable List<String> values = node.getList(String.class);
            return values == null ? List.of() : List.copyOf(values);
        } catch (org.spongepowered.configurate.serialize.SerializationException failure) {
            throw new MenuSpecException("expected a string list at " + node.path(), failure);
        }
    }

    private static Map<String, ClickKind> clickKeys() {
        Map<String, ClickKind> keys = new LinkedHashMap<>();
        keys.put("left", ClickKind.LEFT);
        keys.put("right", ClickKind.RIGHT);
        keys.put("shift_left", ClickKind.SHIFT_LEFT);
        keys.put("shift-left", ClickKind.SHIFT_LEFT);
        keys.put("shift_right", ClickKind.SHIFT_RIGHT);
        keys.put("shift-right", ClickKind.SHIFT_RIGHT);
        keys.put("middle", ClickKind.MIDDLE);
        keys.put("any", ClickKind.ANY);
        return Map.copyOf(keys);
    }
}

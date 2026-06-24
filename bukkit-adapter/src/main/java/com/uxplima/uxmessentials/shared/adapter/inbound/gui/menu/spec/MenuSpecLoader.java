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

    private ItemDecor parseDecor(ConfigurationNode node) {
        ConfigurationNode modelData = node.node("model-data");
        Optional<Integer> model =
                modelData.virtual() || modelData.isNull() ? Optional.empty() : Optional.of(modelData.getInt());
        return new ItemDecor(
                node.node("amount").getInt(1), model, node.node("glow").getBoolean(false), strings(node.node("flags")));
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

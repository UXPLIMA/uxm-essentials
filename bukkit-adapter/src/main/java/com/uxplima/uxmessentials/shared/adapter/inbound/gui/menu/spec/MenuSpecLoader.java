package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
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

    /** Operator-facing diagnostics for a spec that parses but has a skippable defect (a modifier map with no action). */
    private static final Logger LOG = Logger.getLogger(MenuSpecLoader.class.getName());

    /** A chest window is at most six rows of nine slots; the auto-sizer never grows a chest beyond this ceiling. */
    private static final int MAX_ROWS = 6;

    /** The width of every inventory row, the divisor that turns a slot index into the row count needed to hold it. */
    private static final int SLOTS_PER_ROW = 9;

    /** The reserved id the auto-fill background item is stored under; an operator item of the same id is overwritten. */
    private static final String FILL_ITEM_ID = "__fill__";

    /** The auto-fill background sits at the lowest possible priority, so any real item always wins a slot they share. */
    private static final int FILL_PRIORITY = Integer.MIN_VALUE;

    /** Parse a spec held in memory. Primarily a test seam; production loads go through {@link #load(Path)}. */
    public MenuSpec parse(String hocon) {
        return parse(hocon, emptyNode());
    }

    /**
     * Parse a spec held in memory, resolving its item patterns against a shared {@code globalPatterns} block as well
     * as the menu's own {@code patterns}. A menu-local pattern of the same name wins the merge. Passing an empty node
     * makes this behave exactly like {@link #parse(String)} — the target that overload delegates to.
     */
    public MenuSpec parse(String hocon, ConfigurationNode globalPatterns) {
        Objects.requireNonNull(hocon, "hocon");
        Objects.requireNonNull(globalPatterns, "globalPatterns");
        try {
            ConfigurationNode root = HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(hocon)))
                    .build()
                    .load();
            return parseRoot(root, "<string>", globalPatterns);
        } catch (MenuSpecException already) {
            throw already;
        } catch (Exception failure) {
            throw new MenuSpecException("failed to parse menu spec from string", failure);
        }
    }

    /** Load and parse a spec from disk, wrapping any I/O or parse failure with the file path. */
    public MenuSpec load(Path file) {
        return load(file, emptyNode());
    }

    /**
     * Load and parse a spec from disk, resolving its item patterns against a shared {@code globalPatterns} block —
     * patterns defined once in a shared file reusable across every menu — as well as the menu's own {@code patterns}.
     * A menu-local pattern of the same name wins the merge. Passing an empty node makes this behave exactly like
     * {@link #load(Path)} — the target that overload delegates to.
     */
    public MenuSpec load(Path file, ConfigurationNode globalPatterns) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(globalPatterns, "globalPatterns");
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            return parseRoot(root, file.toString(), globalPatterns);
        } catch (MenuSpecException already) {
            throw already;
        } catch (Exception failure) {
            throw new MenuSpecException("failed to load menu spec " + file, failure);
        }
    }

    /** An empty node standing in for "no shared patterns", the delegation target of the pattern-free overloads. */
    private static ConfigurationNode emptyNode() {
        return CommentedConfigurationNode.root();
    }

    private MenuSpec parseRoot(ConfigurationNode root, String origin, ConfigurationNode globalPatterns) {
        Optional<String> inventoryType = optionalString(root.node("inventory-type"));
        boolean chest = inventoryType.isEmpty();
        int declaredRows = root.node("rows").getInt(chest ? 0 : MAX_ROWS);
        // A chest parses its items against the six-row ceiling so every declared slot is known before its rows are
        // auto-sized to fit them. A non-chest sizes its window from its inventory type and keeps its declared (or
        // six-row) fallback, whose own out-of-range slots the renderer skips, so its item ceiling stays that count.
        int ceilingRows = chest ? MAX_ROWS : declaredRows;
        Map<String, Pattern> patterns = mergedPatterns(globalPatterns, root.node("patterns"));
        Map<Character, List<Integer>> layout = layoutSlots(root.node("layout"));
        Map<String, MenuItemSpec> items = parseItems(root.node("items"), ceilingRows, origin, patterns, layout);
        int rows = chest ? chestRows(declaredRows, items) : declaredRows;
        addFillItem(root.node("fill-item"), items, rows, ceilingRows, patterns);
        try {
            return new MenuSpec(
                    root.node("title").getString(""),
                    rows,
                    refreshSpec(root),
                    refs(root.node("open-requirement")),
                    refs(root.node("open-actions")),
                    refs(root.node("close-actions")),
                    items,
                    inventoryType,
                    // The menu's own `placeholders {}` block — custom %name% tokens scoped to this one menu, distinct
                    // from the `patterns {}` block above and from the shared menus/placeholders.conf file. A missing
                    // block flattens to an empty map, so a menu without it is unchanged.
                    varMap(root.node("placeholders")),
                    // The per-menu click-cooldown in milliseconds. Absent → 0 → the menu defers to the server-wide
                    // default, so a menu without the key opens and behaves exactly as before.
                    root.node("click-cooldown").getLong(0));
        } catch (IllegalArgumentException invalid) {
            throw new MenuSpecException("invalid menu in " + origin + ": " + invalid.getMessage(), invalid);
        }
    }

    /**
     * The refresh policy. The DeluxeMenus-style convenience key {@code update-interval = <ticks>}, when present and
     * positive, enables the refresh at that cadence so an author need not spell out the whole {@code refresh {}}
     * block; it wins over {@code refresh {}} when both are set. Otherwise the historic
     * {@code refresh { enabled, interval-ticks }} block is read unchanged (absent → refresh disabled).
     */
    private static RefreshSpec refreshSpec(ConfigurationNode root) {
        int updateInterval = root.node("update-interval").getInt(0);
        if (updateInterval > 0) {
            return new RefreshSpec(true, updateInterval);
        }
        ConfigurationNode refresh = root.node("refresh");
        return new RefreshSpec(
                refresh.node("enabled").getBoolean(false),
                refresh.node("interval-ticks").getInt(0));
    }

    /**
     * Auto-size a chest to hold its highest declared slot, growing an explicit-but-too-small {@code rows} to fit:
     * {@code rows = clamp(max(declaredRows, ceil((maxSlot+1)/9)), 1, 6)}. An empty chest (no item declares a slot)
     * is {@code max(declaredRows, 1)}, so a bare menu is one row rather than an invalid zero. Because a chest's
     * items are parsed against the six-row ceiling, {@code maxSlot} never exceeds 53, so a packed menu caps at six
     * rows and a slot past the six-row maximum a chest can render is rejected earlier as a fail-fast config error.
     */
    private static int chestRows(int declaredRows, Map<String, MenuItemSpec> items) {
        int highest = maxSlot(items);
        if (highest < 0) {
            return Math.max(declaredRows, 1);
        }
        int neededRows = highest / SLOTS_PER_ROW + 1;
        return Math.min(MAX_ROWS, Math.max(1, Math.max(declaredRows, neededRows)));
    }

    /** The highest inventory slot any item occupies, counting a list item's template slots, or {@code -1} if none. */
    private static int maxSlot(Map<String, MenuItemSpec> items) {
        int highest = -1;
        for (MenuItemSpec item : items.values()) {
            highest = Math.max(highest, highestSlot(item.slots()));
            if (item.list().isPresent()) {
                highest = Math.max(
                        highest, highestSlot(item.list().get().template().slots()));
            }
        }
        return highest;
    }

    /** The greatest index in a slot set, or {@code -1} when it declares none. */
    private static int highestSlot(SlotSet slots) {
        int highest = -1;
        for (int slot : slots.slots()) {
            highest = Math.max(highest, slot);
        }
        return highest;
    }

    /**
     * Parse the {@code items} block. An item whose id is a single character present in the menu's {@code layout} grid
     * takes its slots from that grid — the drawing wins over any {@code slot}/{@code slots} it declares — while every
     * other item keeps its own slots. A menu with no {@code layout} passes an empty grid, so no id is ever a grid
     * character and every item parses exactly as it did before.
     */
    private Map<String, MenuItemSpec> parseItems(
            ConfigurationNode itemsNode,
            int rows,
            String origin,
            Map<String, Pattern> patterns,
            Map<Character, List<Integer>> layout) {
        Map<String, MenuItemSpec> items = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                itemsNode.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            try {
                items.put(id, parseItem(entry.getValue(), rows, patterns, layoutOverride(id, layout)));
            } catch (RuntimeException invalid) {
                throw new MenuSpecException(
                        "invalid item '" + id + "' in " + origin + ": " + invalid.getMessage(), invalid);
            }
        }
        return items;
    }

    /**
     * Turn a menu {@code layout} of row-strings into the grid of slots each character claims. Row {@code r}, column
     * {@code c} maps to slot {@code r * 9 + c}, and a character's positions are collected in row-major order so an item
     * named by that single character can take its slots from the drawing. A space or a {@code '.'} is an empty cell and
     * maps to nothing, leaving its slot for the fill item or for nothing at all. Rows past the six a chest holds and
     * columns past the ninth are ignored, keeping every mapped slot in range; an absent or empty {@code layout} yields
     * an empty map, so a menu that draws no grid parses exactly as before.
     */
    private Map<Character, List<Integer>> layoutSlots(ConfigurationNode layoutNode) {
        Map<Character, List<Integer>> grid = new LinkedHashMap<>();
        List<String> rows = strings(layoutNode);
        for (int r = 0; r < rows.size() && r < MAX_ROWS; r++) {
            String row = rows.get(r);
            for (int c = 0; c < row.length() && c < SLOTS_PER_ROW; c++) {
                char cell = row.charAt(c);
                if (cell != ' ' && cell != '.') {
                    grid.computeIfAbsent(cell, key -> new ArrayList<>()).add(r * SLOTS_PER_ROW + c);
                }
            }
        }
        return grid;
    }

    /** The grid positions a single-character item id claims, or {@code null} when the id is not a grid character so the item keeps its own declared slots. */
    private static @Nullable List<Integer> layoutOverride(String id, Map<Character, List<Integer>> layout) {
        return id.length() == 1 ? layout.get(id.charAt(0)) : null;
    }

    /**
     * Add the {@code fill-item} — a background icon painted into every slot no item occupies — once every real item's
     * slots are known. It is an ordinary static item (it may carry a name, lore, decor) built at the lowest priority and
     * stored under {@link #FILL_ITEM_ID}, so even were an empty slot to overlap a real item the real item would still
     * win. An absent {@code fill-item} node adds nothing, so a menu without one parses exactly as before.
     */
    private void addFillItem(
            ConfigurationNode node,
            Map<String, MenuItemSpec> items,
            int rows,
            int ceilingRows,
            Map<String, Pattern> patterns) {
        if (node.virtual() || node.isNull()) {
            return;
        }
        items.put(FILL_ITEM_ID, fillItem(node, emptySlots(items, rows), ceilingRows, patterns));
    }

    /** Every slot in the sized window that no already-parsed item holds, in ascending order — the fill's territory. */
    private static List<Integer> emptySlots(Map<String, MenuItemSpec> items, int rows) {
        Set<Integer> occupied = new HashSet<>();
        for (MenuItemSpec item : items.values()) {
            occupied.addAll(item.slots().slots());
        }
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < rows * SLOTS_PER_ROW; slot++) {
            if (!occupied.contains(slot)) {
                empty.add(slot);
            }
        }
        return empty;
    }

    /** Build the fill item from its descriptor at {@code slots}, forcing the low fill priority over any it might declare. */
    private MenuItemSpec fillItem(
            ConfigurationNode node, List<Integer> slots, int rows, Map<String, Pattern> patterns) {
        MenuItemSpec parsed = parseItem(node, rows, patterns, slots);
        return new MenuItemSpec(
                parsed.slots(),
                FILL_PRIORITY,
                parsed.material(),
                parsed.name(),
                parsed.lore(),
                parsed.decor(),
                parsed.loreMode(),
                parsed.view(),
                parsed.click(),
                parsed.update(),
                parsed.list(),
                parsed.type(),
                parsed.itemDrag());
    }

    private MenuItemSpec parseItem(ConfigurationNode node, int rows, Map<String, Pattern> patterns) {
        return parseItem(node, rows, patterns, null);
    }

    /**
     * Parse one item, honouring an optional layout slot override. When {@code slotOverride} is non-null the item's
     * slots come straight from the grid the menu {@code layout} drew — any {@code slot}/{@code slots} it declares is
     * ignored, the grid position winning — otherwise its slots are parsed from its own tokens exactly as before.
     */
    private MenuItemSpec parseItem(
            ConfigurationNode node, int rows, Map<String, Pattern> patterns, @Nullable List<Integer> slotOverride) {
        ConfigurationNode item = resolvePattern(node, patterns);
        SlotSet slots = slotOverride != null ? new SlotSet(slotOverride) : SlotSet.parse(slotTokens(item), rows * 9);
        return new MenuItemSpec(
                slots,
                item.node("priority").getInt(0),
                item.node("material").getString("STONE"),
                item.node("name").getString(""),
                strings(item.node("lore")),
                parseDecor(item.node("decor")),
                LoreMode.fromToken(item.node("lore-mode").getString()),
                parseView(item.node("view")),
                parseClick(item.node("click")),
                item.node("update").getBoolean(false),
                parseList(item.node("list"), rows, patterns),
                itemType(item.node("type")),
                parseItemDrag(item.node("item-drag")));
    }

    /**
     * Parse an item's optional {@code item-drag} block, which turns the item into a drag target. The grammar is
     * {@code item-drag { rules { materials = [...], min-amount = 1, name-contains = "" }, consume = false,
     * actions = [ … ] }}: {@code materials} is a whitelist of {@code Material} names (empty or absent → any material),
     * {@code min-amount} the least stack size accepted (default one), {@code name-contains} a case-insensitive display
     * name substring (blank → no name check), {@code consume} whether the matched amount is removed from the cursor,
     * and {@code actions} the ref list fired on a match through the same parser the click actions use. An absent block
     * yields {@link Optional#empty()}, so an item without one parses exactly as before.
     */
    private Optional<ItemDragSpec> parseItemDrag(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        ConfigurationNode rules = node.node("rules");
        ItemRuleSpec ruleSpec = new ItemRuleSpec(
                upperCased(strings(rules.node("materials"))),
                rules.node("min-amount").getInt(1),
                rules.node("name-contains").getString(""));
        return Optional.of(
                new ItemDragSpec(ruleSpec, node.node("consume").getBoolean(false), refs(node.node("actions"))));
    }

    /** Upper-case every material name so the runtime match compares against {@code Material.name()} case-insensitively. */
    private static List<String> upperCased(List<String> materials) {
        return materials.stream()
                .map(m -> m.strip().toUpperCase(java.util.Locale.ROOT))
                .toList();
    }

    /**
     * Merge the shared {@code global} patterns with the menu's own {@code local} block, the menu-local definition
     * winning a name clash. Shared patterns are declared once in a file reusable across every menu; a menu that
     * redeclares a shared name overrides it for itself only. An empty {@code global} node leaves the menu-local
     * patterns unchanged, so a spec loaded without shared patterns resolves exactly as it did before.
     */
    private Map<String, Pattern> mergedPatterns(ConfigurationNode global, ConfigurationNode local) {
        Map<String, Pattern> patterns = new LinkedHashMap<>(parsePatterns(global));
        patterns.putAll(parsePatterns(local));
        return patterns;
    }

    /**
     * Read a {@code patterns} block into reusable item templates keyed by name. Each template is a deep copy of its
     * spec node — a shared template must never be mutated when an item fills its {@code %var%}s — with the optional
     * {@code defaults { var = value … }} child split out into that pattern's default var values and removed from the
     * copy so it is never mistaken for an item field. An absent block yields an empty map, so a menu declaring no
     * patterns parses exactly as it did before.
     */
    private Map<String, Pattern> parsePatterns(ConfigurationNode node) {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            ConfigurationNode template = entry.getValue().copy();
            Map<String, String> defaults = varMap(template.node("defaults"));
            template.removeChild("defaults");
            patterns.put(String.valueOf(entry.getKey()), new Pattern(template, defaults));
        }
        return patterns;
    }

    /**
     * Expand a {@code pattern = "<name>"} item into its effective node. An item with no {@code pattern} key is
     * returned unchanged, so a pattern-free spec parses byte-identically; a {@code pattern} naming no declared
     * template is warned about and the item parsed from its own fields (its {@code pattern}/{@code vars} keys are
     * simply not among the fields read). Otherwise the effective node is the template with the item's own fields
     * overlaid — the item wins, its {@code name}/{@code slots}/{@code click} replacing the template's — and every
     * {@code %var%} then filled from the item's {@code vars} merged over the pattern's {@code defaults}. Overlaying
     * before substituting is deliberate: it lets an item's own override reference a {@code %var%} too (the worked
     * grammar's {@code name = "<aqua>%label% (deal)"}). Resolution runs once, on the raw item — never on the node it
     * returns — so a {@code pattern} key on the template itself is ignored: patterns nest one level only.
     */
    private ConfigurationNode resolvePattern(ConfigurationNode itemNode, Map<String, Pattern> templates) {
        ConfigurationNode ref = itemNode.node("pattern");
        if (ref.virtual() || ref.isNull()) {
            return itemNode;
        }
        String name = ref.getString("");
        Pattern pattern = templates.get(name);
        if (pattern == null) {
            LOG.warning("menu item at " + itemNode.path() + " names unknown pattern '" + name
                    + "'; parsing its own fields");
            return itemNode;
        }
        ConfigurationNode effective = pattern.template().copy();
        overlay(effective, itemNode);
        substituteVars(effective, mergedVars(pattern.defaults(), itemNode.node("vars")));
        return effective;
    }

    /**
     * Copy the item's own fields onto the template, the item winning: for every key except {@code pattern} and
     * {@code vars} the item's node replaces the template's node at that key. The granularity is per top-level key —
     * an item {@code click { right = […] }} replaces the template's whole {@code click} (its {@code left} included)
     * rather than merging gesture-by-gesture — which keeps the override rule simple and predictable.
     */
    private static void overlay(ConfigurationNode base, ConfigurationNode overrides) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                overrides.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!key.equals("pattern") && !key.equals("vars")) {
                base.node(key).from(entry.getValue());
            }
        }
    }

    /** The item's {@code vars} layered over the pattern's {@code defaults}, so an item-supplied var wins a clash. */
    private static Map<String, String> mergedVars(Map<String, String> defaults, ConfigurationNode varsNode) {
        Map<String, String> vars = new LinkedHashMap<>(defaults);
        vars.putAll(varMap(varsNode));
        return vars;
    }

    /** Flatten a {@code { name = value … }} node into a plain var map, keeping every value as its string token. */
    private static Map<String, String> varMap(ConfigurationNode node) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (node.virtual() || node.isNull()) {
            return vars;
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            String value = entry.getValue().getString();
            if (value != null) {
                vars.put(String.valueOf(entry.getKey()), value);
            }
        }
        return vars;
    }

    /**
     * Fill every {@code %var%} in the node tree in place, recursing maps and lists. At a scalar leaf it substitutes
     * only {@code %token%}s whose {@code token} is a known var; an unknown {@code %placeholder%} (a registry token, a
     * PAPI expansion, a {@code %argument_%}) is left verbatim so it still resolves at render time. A pattern can thus
     * freely mix {@code %var%} (filled now) and {@code %placeholder%} (filled per draw).
     */
    private static void substituteVars(ConfigurationNode node, Map<String, String> vars) {
        if (node.isMap()) {
            node.childrenMap().values().forEach(child -> substituteVars(child, vars));
        } else if (node.isList()) {
            node.childrenList().forEach(child -> substituteVars(child, vars));
        } else {
            String raw = node.getString();
            if (raw != null) {
                String filled = substituteScalar(raw, vars);
                if (!filled.equals(raw)) {
                    node.raw(filled);
                }
            }
        }
    }

    /** Replace each known {@code %var%} token in a scalar; an unrecognised {@code %token%} is left untouched. */
    private static String substituteScalar(String raw, Map<String, String> vars) {
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        String result = raw;
        for (Map.Entry<String, String> var : vars.entrySet()) {
            result = result.replace("%" + var.getKey() + "%", var.getValue());
        }
        return result;
    }

    /** A menu-local item template: a deep copy of its spec node plus the default values for its {@code %var%}s. */
    private record Pattern(ConfigurationNode template, Map<String, String> defaults) {}

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

    /**
     * Parse an item's {@code view} gate into a {@link RequirementSpec}. It accepts either the historic flat list —
     * {@code view = ["perm:vip", "!has-empty-slots:1"]}, where a leading {@code !} on an entry inverts it and every
     * entry is mandatory (AND) — or a block map {@code view = { requirements = [...], minimum = N }} that brings the
     * same minimum (OR / N-of-M) power the click requirement blocks have. A flat list with no {@code !} builds the
     * same all-mandatory block it always did, so an existing spec renders identically. A view gate is a pure yes/no,
     * so it carries no {@code deny}, {@code success}, or {@code stop-at-success} — those keys play no part here even
     * if a block map names them by habit.
     */
    private RequirementSpec parseView(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return RequirementSpec.NONE;
        }
        if (node.isMap()) {
            return new RequirementSpec(
                    parseRequirements(node.node("requirements")),
                    node.node("minimum").getInt(0),
                    List.of());
        }
        return new RequirementSpec(parseRequirements(node), 0, List.of());
    }

    /**
     * Parse the {@code click} block. Each gesture key ({@code left}, {@code shift-right}, {@code any}, …) maps to
     * either a bare action list — the historic form, unchanged — or a map that adds a requirement block:
     * {@code { click|actions|do = [...], requirements = ["has-money:100", "!has-empty-slots:1"], minimum = 1,
     * deny = ["message:no"], stop-at-success = false }}. The actions come from whichever of {@code click}/
     * {@code actions}/{@code do} is present; a requirements entry is either a token with an optional leading {@code !}
     * (negate) or a map {@code { require = "<token>", optional = bool, success = [...], deny = [...] }} adding
     * per-requirement structure; {@code minimum} defaults to {@code 0} (all must pass); {@code stop-at-success} short-
     * circuits once the minimum is met; block-level {@code deny} is the action list run when the block fails. A bare
     * list yields {@link RequirementSpec#NONE}, so it behaves exactly as it did before requirement blocks existed.
     *
     * <p>A gesture map may also carry an {@code else} block — a fallback branch tried when the main requirement fails:
     * {@code else = { requirements = [...], click|actions|do = [...], minimum, stop-at-success, deny = [...], else = {
     * ... } }}. Its requirements gate it exactly like the main block, its actions run when that gate passes, and its own
     * nested {@code else} is the next branch tried if it fails — an if / else-if / else ladder. A terminal {@code else}
     * with no {@code requirements} always passes, so it is the unconditional "otherwise" arm.
     */
    private ClickSpec parseClick(ConfigurationNode node) {
        Map<ClickKind, List<Ref>> actions = new EnumMap<>(ClickKind.class);
        Map<ClickKind, RequirementSpec> requirements = new EnumMap<>(ClickKind.class);
        Map<ClickKind, ClickBranch> orElse = new EnumMap<>(ClickKind.class);
        if (!node.virtual() && !node.isNull()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                    node.childrenMap().entrySet()) {
                ClickKind kind = CLICK_KEYS.get(String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT));
                if (kind != null) {
                    parseClickEntry(entry.getValue(), kind, actions, requirements, orElse);
                }
            }
        }
        // v1 keeps per-gesture conditions empty; visibility is gated by the item-level `view` list instead.
        return new ClickSpec(actions, Map.of(), requirements, orElse);
    }

    /**
     * One gesture's value: a map carries actions, a requirement block, and an optional {@code else} fallback chain; a
     * bare list carries actions only. The {@code else} branch, when present, becomes the head of the gesture's
     * else-chain, tried at runtime when the main requirement block fails.
     */
    private void parseClickEntry(
            ConfigurationNode value,
            ClickKind kind,
            Map<ClickKind, List<Ref>> actions,
            Map<ClickKind, RequirementSpec> requirements,
            Map<ClickKind, ClickBranch> orElse) {
        if (value.isMap()) {
            actions.put(kind, refs(clickActionsNode(value)));
            RequirementSpec block = parseRequirementSpec(value);
            if (block != RequirementSpec.NONE) {
                requirements.put(kind, block);
            }
            parseElseBranch(value.node("else")).ifPresent(branch -> orElse.put(kind, branch));
        } else {
            actions.put(kind, refs(value));
        }
    }

    /**
     * Parse an {@code else} node into a {@link ClickBranch}, recursing on its own nested {@code else} so a fallback
     * ladder of any depth builds bottom-up. A branch reads its gate as a full requirement block — the same {@code
     * requirements}/{@code minimum}/{@code deny}/{@code stop-at-success} grammar the main block uses, via {@link
     * #parseRequirementSpec} — its actions from {@code click}/{@code actions}/{@code do}, and its {@code orElse} from
     * the next {@code else}. A branch that names no requirements resolves to {@link RequirementSpec#NONE}, which always
     * passes: that is the terminal else, the unconditional "otherwise" arm. A missing or non-map {@code else} node
     * yields no branch, so a plain gesture (and the tail of a chain) simply has no fallback.
     */
    private Optional<ClickBranch> parseElseBranch(ConfigurationNode elseNode) {
        if (elseNode.virtual() || elseNode.isNull() || !elseNode.isMap()) {
            return Optional.empty();
        }
        return Optional.of(new ClickBranch(
                parseRequirementSpec(elseNode),
                refs(clickActionsNode(elseNode)),
                parseElseBranch(elseNode.node("else"))));
    }

    /** The action list of a map-form gesture: the first present of {@code click}, {@code actions}, or {@code do}. */
    private static ConfigurationNode clickActionsNode(ConfigurationNode value) {
        for (String key : List.of("click", "actions", "do")) {
            ConfigurationNode candidate = value.node(key);
            if (!candidate.virtual() && !candidate.isNull()) {
                return candidate;
            }
        }
        return value.node("click");
    }

    /** The requirement block of a map-form gesture, or {@link RequirementSpec#NONE} when it names neither requirements nor deny. */
    private RequirementSpec parseRequirementSpec(ConfigurationNode value) {
        List<Requirement> reqs = parseRequirements(value.node("requirements"));
        List<Ref> deny = refs(value.node("deny"));
        if (reqs.isEmpty() && deny.isEmpty()) {
            return RequirementSpec.NONE;
        }
        return new RequirementSpec(reqs, value.node("minimum").getInt(0), deny, stopAtSuccess(value));
    }

    /** The {@code stop-at-success} (or {@code stop_at_success}) toggle of a requirement block; {@code false} when unset. */
    private static boolean stopAtSuccess(ConfigurationNode value) {
        ConfigurationNode kebab = value.node("stop-at-success");
        if (!kebab.virtual() && !kebab.isNull()) {
            return kebab.getBoolean(false);
        }
        return value.node("stop_at_success").getBoolean(false);
    }

    /**
     * Parse the {@code requirements} entries into a requirement list. An entry is either a compact string token (the
     * historic form: an optional leading {@code !} negates the rest) or a map that adds per-requirement structure —
     * {@code { require|requirement = "<token>", optional = bool, success = [...], deny = [...] }}. Blank or
     * {@code require}-less entries are skipped, mirroring the map-without-action skip on the action path.
     */
    private List<Requirement> parseRequirements(ConfigurationNode node) {
        List<Requirement> reqs = new ArrayList<>();
        if (node.virtual() || node.isNull()) {
            return reqs;
        }
        if (node.isList()) {
            for (ConfigurationNode child : node.childrenList()) {
                requirementEntry(child).ifPresent(reqs::add);
            }
        } else {
            requirementEntry(node).ifPresent(reqs::add);
        }
        return reqs;
    }

    /** One requirements entry: a map form carries per-requirement structure, otherwise it is a plain condition token. */
    private Optional<Requirement> requirementEntry(ConfigurationNode entry) {
        if (entry.isMap()) {
            return mapRequirement(entry);
        }
        String raw = entry.getString();
        return raw == null ? Optional.empty() : parseRequirement(raw);
    }

    /**
     * A map-form requirement: {@code require} (or its {@code requirement} alias) is the condition token (a leading
     * {@code !} negates it, as in the string form), {@code optional} marks it non-blocking, and {@code success}/
     * {@code deny} are the per-requirement action lists run on its own pass or failure. An entry with no condition token
     * is a config mistake — logged and skipped so one bad requirement does not abort the menu.
     */
    private Optional<Requirement> mapRequirement(ConfigurationNode entry) {
        String token = requireToken(entry);
        String trimmed = token == null ? "" : token.strip();
        boolean inverted = trimmed.startsWith("!");
        String body = inverted ? trimmed.substring(1).strip() : trimmed;
        if (body.isEmpty()) {
            LOG.warning("skipping menu requirement map without a require token at " + entry.path());
            return Optional.empty();
        }
        return Optional.of(new Requirement(
                Ref.parse(body),
                inverted,
                entry.node("optional").getBoolean(false),
                refs(entry.node("success")),
                refs(entry.node("deny"))));
    }

    /** The condition token of a map requirement: {@code require} takes precedence, then its {@code requirement} alias. */
    private static @Nullable String requireToken(ConfigurationNode entry) {
        ConfigurationNode explicit = entry.node("require");
        if (!explicit.virtual() && !explicit.isNull()) {
            return explicit.getString();
        }
        return entry.node("requirement").getString();
    }

    /** One requirement token: a leading {@code !} (after trimming) negates the condition, and the rest is a ref. */
    private static Optional<Requirement> parseRequirement(String token) {
        String trimmed = token.strip();
        boolean inverted = trimmed.startsWith("!");
        String body = inverted ? trimmed.substring(1).strip() : trimmed;
        return body.isEmpty() ? Optional.empty() : Optional.of(new Requirement(Ref.parse(body), inverted));
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

    private Optional<ListSpec> parseList(ConfigurationNode node, int rows, Map<String, Pattern> patterns) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        Ref source = Ref.parse(Objects.requireNonNull(node.node("source").getString(), "list.source"));
        return Optional.of(new ListSpec(source, parseItem(node.node("template"), rows, patterns)));
    }

    /**
     * A ref list, where every entry is either a compact scalar token (the historic form, unchanged) or a map
     * carrying an action token plus per-action modifiers. A list of scalars round-trips exactly as before; a map
     * entry reads its {@code do}/{@code action} token and layers {@code delay}/{@code chance}/{@code deny} on top.
     */
    private List<Ref> refs(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        List<Ref> refs = new ArrayList<>();
        if (node.isList()) {
            for (ConfigurationNode child : node.childrenList()) {
                refEntry(child).ifPresent(refs::add);
            }
        } else {
            refEntry(node).ifPresent(refs::add);
        }
        return refs;
    }

    /** One ref-list entry: a map form goes through {@link #modifiedRef}, otherwise it is a plain scalar token. */
    private Optional<Ref> refEntry(ConfigurationNode entry) {
        if (entry.isMap()) {
            return modifiedRef(entry);
        }
        String raw = entry.getString();
        return raw == null ? Optional.empty() : Optional.of(Ref.parse(raw));
    }

    /**
     * A map-form action entry: {@code do} (or its {@code action} alias) is the action token, and the optional
     * {@code delay} (ticks), {@code chance} (percent), and {@code deny} (a fallback action token) modify it. An
     * entry with no action token is a config mistake — logged and skipped so one bad button does not abort the menu.
     */
    private Optional<Ref> modifiedRef(ConfigurationNode entry) {
        String token = actionToken(entry);
        if (token == null || token.isBlank()) {
            LOG.warning("skipping menu action map without a do/action token at " + entry.path());
            return Optional.empty();
        }
        Ref base = Ref.parse(token);
        int delay = entry.node("delay").getInt(0);
        double chance = entry.node("chance").getDouble(100.0);
        return Optional.of(base.withModifiers(delay, chance, denyRef(entry)));
    }

    /** The action token of a map entry: {@code do} takes precedence, then its {@code action} alias, else none. */
    private static @Nullable String actionToken(ConfigurationNode entry) {
        ConfigurationNode explicit = entry.node("do");
        if (!explicit.virtual() && !explicit.isNull()) {
            return explicit.getString();
        }
        return entry.node("action").getString();
    }

    /** The deny fallback of a map entry: a single scalar action token, or {@code null} when the key is absent. */
    private static @Nullable Ref denyRef(ConfigurationNode entry) {
        ConfigurationNode deny = entry.node("deny");
        if (deny.virtual() || deny.isNull()) {
            return null;
        }
        String raw = deny.getString();
        return raw == null || raw.isBlank() ? null : Ref.parse(raw);
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
        keys.put("drop", ClickKind.DROP);
        keys.put("control_drop", ClickKind.CONTROL_DROP);
        keys.put("control-drop", ClickKind.CONTROL_DROP);
        keys.put("ctrl_drop", ClickKind.CONTROL_DROP);
        keys.put("double_click", ClickKind.DOUBLE_CLICK);
        keys.put("double-click", ClickKind.DOUBLE_CLICK);
        keys.put("any", ClickKind.ANY);
        return Map.copyOf(keys);
    }
}

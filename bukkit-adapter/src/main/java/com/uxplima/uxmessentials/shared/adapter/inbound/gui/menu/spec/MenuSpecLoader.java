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
import java.util.logging.Logger;

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

    /** Operator-facing diagnostics for a spec that parses but has a skippable defect (a modifier map with no action). */
    private static final Logger LOG = Logger.getLogger(MenuSpecLoader.class.getName());

    /** A chest window is at most six rows of nine slots; the auto-sizer never grows a chest beyond this ceiling. */
    private static final int MAX_ROWS = 6;

    /** The width of every inventory row, the divisor that turns a slot index into the row count needed to hold it. */
    private static final int SLOTS_PER_ROW = 9;

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
        Optional<String> inventoryType = optionalString(root.node("inventory-type"));
        boolean chest = inventoryType.isEmpty();
        int declaredRows = root.node("rows").getInt(chest ? 0 : MAX_ROWS);
        // A chest parses its items against the six-row ceiling so every declared slot is known before its rows are
        // auto-sized to fit them. A non-chest sizes its window from its inventory type and keeps its declared (or
        // six-row) fallback, whose own out-of-range slots the renderer skips, so its item ceiling stays that count.
        int ceilingRows = chest ? MAX_ROWS : declaredRows;
        Map<String, MenuItemSpec> items = parseItems(root.node("items"), ceilingRows, origin);
        int rows = chest ? chestRows(declaredRows, items) : declaredRows;
        try {
            return new MenuSpec(
                    root.node("title").getString(""),
                    rows,
                    refreshSpec(root),
                    refs(root.node("open-requirement")),
                    refs(root.node("open-actions")),
                    refs(root.node("close-actions")),
                    items,
                    inventoryType);
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
                LoreMode.fromToken(node.node("lore-mode").getString()),
                parseView(node.node("view")),
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

    private Optional<ListSpec> parseList(ConfigurationNode node, int rows) {
        if (node.virtual() || node.isNull()) {
            return Optional.empty();
        }
        Ref source = Ref.parse(Objects.requireNonNull(node.node("source").getString(), "list.source"));
        return Optional.of(new ListSpec(source, parseItem(node.node("template"), rows)));
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
        keys.put("any", ClickKind.ANY);
        return Map.copyOf(keys);
    }
}

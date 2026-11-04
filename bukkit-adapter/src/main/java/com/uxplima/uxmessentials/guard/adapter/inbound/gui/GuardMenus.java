package com.uxplima.uxmessentials.guard.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import org.jspecify.annotations.Nullable;

/**
 * The guard menus (E01) on the menu engine: the region list, one region's hub, the rule editor that walks the action
 * tree, roles, behaviours, templates, history and children. Every window is a file under {@code modules/guard/gui/}
 * and every Bedrock player gets the engine's form for it. Edits go through the same service calls and answers as
 * {@code /guard}, then the window opens again with the stored state.
 *
 * <p>Staff with {@code uxmessentials.guard.menu} reach everything their nodes allow; a region owner or manager without
 * it reaches the roles window of the region they stand in, where they may add and remove members and guests.
 */
public final class GuardMenus {

    static final String LIST = "guard-regions";
    static final String CHILDREN = "guard-children";
    static final String HUB = "guard-region";
    static final String RULES = "guard-rules";
    static final String ROLES = "guard-roles";
    static final String BEHAVIOURS = "guard-behaviours";
    static final String TEMPLATES = "guard-templates";
    static final String HISTORY = "guard-history";

    private static final List<String> SPECS =
            List.of(LIST, CHILDREN, HUB, RULES, ROLES, BEHAVIOURS, TEMPLATES, HISTORY);
    private static final String BASE = "uxmessentials.guard.";
    private static final int HISTORY_ROWS = 45;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Menus menus;
    private final GuardService service;
    private final GuardCommand command;
    private final CommandFeedback feedback;
    private final Messages messages;
    private final BiConsumer<Player, Runnable> onPlayer;

    public GuardMenus(
            Menus menus,
            GuardService service,
            GuardCommand command,
            CommandFeedback feedback,
            Messages messages,
            BiConsumer<Player, Runnable> onPlayer) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.service = Objects.requireNonNull(service, "service");
        this.command = Objects.requireNonNull(command, "command");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onPlayer = Objects.requireNonNull(onPlayer, "onPlayer");
    }

    // ---- subjects and rows --------------------------------------------------------------------------------------

    /** The region list of a world, or the children of one region. */
    public record RegionsView(String world, @Nullable RegionId parent) {}

    /** One region's hub. */
    public record RegionView(RegionKey key) {}

    /** The rule editor: the children of {@code node} (the roots when null) for one target. */
    public record RulesView(RegionKey key, @Nullable ActionId node, Target target) {}

    /** One action row of the rule editor. */
    public record ActionRow(ActionId id, @Nullable Effect effect, int children) {}

    /** The roles window, with names resolved when it opened. */
    public record RolesView(RegionKey key, List<RoleRow> rows) {}

    /** One player and their role. */
    public record RoleRow(UUID player, String name, RoleName role) {}

    /** The behaviours window. */
    public record BehavioursView(RegionKey key) {}

    /** One behaviour and its value in the region, or null when unset. */
    public record BehaviourRow(BehaviourKey key, @Nullable String value) {}

    /** The templates window. */
    public record TemplatesView(RegionKey key) {}

    /** One template and whether the region uses it. */
    public record TemplateRow(Template template, boolean linked) {}

    /** The history window, read before it opened. */
    public record HistoryView(RegionKey key, List<HistoryEntry> rows) {}

    // ---- registration -------------------------------------------------------------------------------------------

    /** Register list sources, placeholders, actions and the window files; once at wiring. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        bindings.list("guard:regions", this::regionRows);
        bindings.placeholder(
                "guard_region_id", ctx -> ctx.entry(Region.class).id().value());
        bindings.placeholder(
                "guard_region_world", ctx -> ctx.entry(Region.class).world());
        bindings.placeholder(
                "guard_region_shape", ctx -> ctx.entry(Region.class).shape().kind());
        bindings.placeholder(
                "guard_region_priority",
                ctx -> String.valueOf(ctx.entry(Region.class).priority()));
        bindings.placeholder(
                "guard_region_rules",
                ctx -> String.valueOf(ctx.entry(Region.class).rules().size()));
        bindings.placeholder(
                "guard_region_roles",
                ctx -> String.valueOf(ctx.entry(Region.class).roles().size()));
        bindings.placeholder("guard_region_icon", ctx -> regionIcon(ctx.entry(Region.class)));
        bindings.placeholder(
                "guard_list_world", ctx -> ctx.subject(RegionsView.class).world());
        bindings.placeholder(
                "guard_list_parent",
                ctx -> Optional.ofNullable(ctx.subject(RegionsView.class).parent())
                        .map(RegionId::value)
                        .orElse("-"));
        bindings.action(
                "guard:regions-open",
                ctx -> openHub(ctx.player(), ctx.entry(Region.class).key()));
        bindings.action("guard:regions-show", ctx -> {
            Region region = ctx.entry(Region.class);
            ctx.player().closeInventory();
            ctx.player()
                    .performCommand(
                            "guard show " + region.world() + ":" + region.id().value());
        });
        bindings.action("guard:children-back", ctx -> {
            RegionsView view = ctx.subject(RegionsView.class);
            RegionId parent = view.parent();
            if (parent != null) {
                openHub(ctx.player(), new RegionKey(view.world(), parent));
            }
        });

        hub(bindings);
        rules(bindings);
        roles(bindings);
        behaviours(bindings);
        templates(bindings);
        history(bindings);

        for (String id : SPECS) {
            menus.registerSpec(
                    id, MenuSpecs.loadOrBundled("modules/guard/gui/" + id + ".conf", dataFolder, 6, EngineLog.of(log)));
        }
    }

    private void hub(MenuBindings bindings) {
        bindings.placeholder(
                "guard_hub_id", ctx -> ctx.subject(RegionView.class).key().id().value());
        bindings.placeholder(
                "guard_hub_world", ctx -> ctx.subject(RegionView.class).key().world());
        bindings.placeholder("guard_hub_priority", ctx -> hubValue(ctx, r -> String.valueOf(r.priority())));
        bindings.placeholder(
                "guard_hub_shape", ctx -> hubValue(ctx, r -> r.shape().kind()));
        bindings.placeholder(
                "guard_hub_scope", ctx -> hubValue(ctx, r -> r.scope().value()));
        bindings.placeholder(
                "guard_hub_parent",
                ctx -> hubValue(ctx, r -> r.parent() == null ? "-" : r.parent().value()));
        bindings.placeholder("guard_hub_family", ctx -> hubValue(ctx, r -> r.family() == null ? "-" : r.family()));
        bindings.placeholder(
                "guard_hub_template", ctx -> hubValue(ctx, r -> r.template() == null ? "-" : r.template()));
        bindings.placeholder(
                "guard_hub_tags", ctx -> hubValue(ctx, r -> r.tags().isEmpty() ? "-" : String.join(", ", r.tags())));
        bindings.placeholder(
                "guard_hub_rules",
                ctx -> hubValue(ctx, r -> String.valueOf(r.rules().size())));
        bindings.placeholder(
                "guard_hub_roles",
                ctx -> hubValue(ctx, r -> String.valueOf(r.roles().size())));
        bindings.placeholder(
                "guard_hub_behaviours",
                ctx -> hubValue(ctx, r -> String.valueOf(r.behaviours().asMap().size())));
        bindings.placeholder(
                "guard_hub_children",
                ctx -> hubValue(ctx, r -> String.valueOf(children(r).size())));
        bindings.placeholder("guard_hub_locked", ctx -> hubValue(ctx, r -> r.locked() ? "IRON_BARS" : "OAK_FENCE"));

        bindings.action("guard:hub-rules", ctx -> {
            if (allowed(ctx, "rule")) {
                open(ctx.player(), RULES, new RulesView(hubKey(ctx), null, everyone()));
            }
        });
        bindings.action("guard:hub-roles", ctx -> openRoles(ctx.player(), hubKey(ctx)));
        bindings.action("guard:hub-behaviours", ctx -> {
            if (allowed(ctx, "set")) {
                open(ctx.player(), BEHAVIOURS, new BehavioursView(hubKey(ctx)));
            }
        });
        bindings.action("guard:hub-templates", ctx -> {
            if (allowed(ctx, "template")) {
                open(ctx.player(), TEMPLATES, new TemplatesView(hubKey(ctx)));
            }
        });
        bindings.action("guard:hub-history", ctx -> {
            if (allowed(ctx, "history")) {
                openHistory(ctx.player(), hubKey(ctx));
            }
        });
        bindings.action("guard:hub-children", ctx -> {
            RegionKey key = hubKey(ctx);
            open(ctx.player(), CHILDREN, new RegionsView(key.world(), key.id()));
        });
        bindings.action("guard:hub-show", ctx -> {
            RegionKey key = hubKey(ctx);
            ctx.player().closeInventory();
            ctx.player()
                    .performCommand("guard show " + key.world() + ":" + key.id().value());
        });
        bindings.action("guard:hub-lock", ctx -> {
            if (!allowed(ctx, "lock")) {
                return;
            }
            RegionKey key = hubKey(ctx);
            boolean locked = service.region(key).map(Region::locked).orElse(false);
            edit(
                    ctx.player(),
                    key,
                    "lock",
                    r -> r.withLocked(!locked),
                    locked ? GuardMessageKey.LOCK_OFF : GuardMessageKey.LOCK_ON,
                    Map.of(),
                    () -> openHub(ctx.player(), key));
        });
        bindings.action("guard:hub-remove", ctx -> {
            if (!allowed(ctx, "remove")) {
                return;
            }
            Player player = ctx.player();
            RegionKey key = hubKey(ctx);
            String title = messages.resolve(
                    BukkitRefs.toRef(player),
                    GuardMessageKey.GUI_REMOVE_CONFIRM,
                    Map.of("region", key.id().value()));
            menus.confirm(
                    player,
                    StyledText.render(title),
                    () -> command.answerMenu(
                            player,
                            service.remove(key, command.editContext(player)),
                            GuardMessageKey.REMOVED,
                            Map.of("region", key.id().value()),
                            () -> open(player, LIST, new RegionsView(key.world(), null))),
                    () -> openHub(player, key));
        });
        bindings.action("guard-hub-priority", ctx -> {
            if (!allowed(ctx, "priority")) {
                return;
            }
            RegionKey key = hubKey(ctx);
            int value;
            try {
                value = Integer.parseInt(ctx.arg().strip());
            } catch (NumberFormatException e) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", ctx.arg()));
                return;
            }
            edit(
                    ctx.player(),
                    key,
                    "priority",
                    r -> r.withPriority(value),
                    GuardMessageKey.PRIORITY_SET,
                    Map.of("priority", String.valueOf(value)),
                    () -> openHub(ctx.player(), key));
        });
        bindings.action("guard-hub-tag-add", ctx -> tag(ctx, true));
        bindings.action("guard-hub-tag-remove", ctx -> tag(ctx, false));
        bindings.action(
                "guard:hub-back",
                ctx -> open(ctx.player(), LIST, new RegionsView(hubKey(ctx).world(), null)));
    }

    private void tag(MenuActionContext ctx, boolean add) {
        if (!allowed(ctx, "tag")) {
            return;
        }
        RegionKey key = hubKey(ctx);
        String tag = ctx.arg().strip().toLowerCase(Locale.ROOT);
        if (tag.isEmpty() || !RegionId.valid(tag)) {
            feedback.send(ctx.player(), GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", ctx.arg()));
            return;
        }
        edit(
                ctx.player(),
                key,
                "tag",
                r -> {
                    Set<String> tags = new LinkedHashSet<>(r.tags());
                    if (add) {
                        tags.add(tag);
                    } else {
                        tags.remove(tag);
                    }
                    return r.withTags(tags);
                },
                add ? GuardMessageKey.TAG_ADDED : GuardMessageKey.TAG_REMOVED,
                Map.of("tag", tag),
                () -> openHub(ctx.player(), key));
    }

    private void rules(MenuBindings bindings) {
        bindings.list("guard:actions", this::actionRows);
        bindings.placeholder(
                "guard_action_id", ctx -> ctx.entry(ActionRow.class).id().value());
        bindings.placeholder("guard_action_state", ctx -> {
            Effect effect = ctx.entry(ActionRow.class).effect();
            return effect == null ? "-" : effect.name().toLowerCase(Locale.ROOT);
        });
        bindings.placeholder("guard_action_icon", ctx -> {
            Effect effect = ctx.entry(ActionRow.class).effect();
            return effect == null ? "GRAY_DYE" : effect == Effect.ALLOW ? "LIME_DYE" : "RED_DYE";
        });
        bindings.placeholder(
                "guard_action_children",
                ctx -> String.valueOf(ctx.entry(ActionRow.class).children()));
        bindings.placeholder(
                "guard_rules_target",
                ctx -> ctx.subject(RulesView.class).target().text());
        bindings.placeholder(
                "guard_rules_node",
                ctx -> Optional.ofNullable(ctx.subject(RulesView.class).node())
                        .map(ActionId::value)
                        .orElse("*"));
        bindings.placeholder(
                "guard_rules_region",
                ctx -> ctx.subject(RulesView.class).key().id().value());

        bindings.action("guard:rule-cycle", ctx -> {
            if (!allowed(ctx, "rule")) {
                return;
            }
            RulesView view = ctx.subject(RulesView.class);
            ActionRow row = ctx.entry(ActionRow.class);
            Effect current = row.effect();
            Map<String, String> values = Map.of(
                    "action",
                    row.id().value(),
                    "target",
                    view.target().text(),
                    "effect",
                    current == null ? "allow" : current == Effect.ALLOW ? "deny" : "clear");
            if (current == Effect.DENY) {
                edit(
                        ctx.player(),
                        view.key(),
                        "rule",
                        r -> r.withoutRule(row.id(), view.target()),
                        GuardMessageKey.RULE_CLEARED,
                        values,
                        () -> open(ctx.player(), RULES, view, ctx.page()));
            } else {
                Effect next = current == null ? Effect.ALLOW : Effect.DENY;
                edit(
                        ctx.player(),
                        view.key(),
                        "rule",
                        r -> r.withRule(new Rule(row.id(), view.target(), next)),
                        GuardMessageKey.RULE_SET,
                        values,
                        () -> open(ctx.player(), RULES, view, ctx.page()));
            }
        });
        bindings.action("guard:rule-open", ctx -> {
            RulesView view = ctx.subject(RulesView.class);
            ActionRow row = ctx.entry(ActionRow.class);
            if (row.children() > 0) {
                open(ctx.player(), RULES, new RulesView(view.key(), row.id(), view.target()));
            }
        });
        bindings.action("guard:rules-target-next", ctx -> {
            RulesView view = ctx.subject(RulesView.class);
            List<Target> targets = targets();
            int at = targets.indexOf(view.target());
            Target next = targets.get((at + 1) % targets.size());
            open(ctx.player(), RULES, new RulesView(view.key(), view.node(), next));
        });
        bindings.action("guard-rules-target", ctx -> {
            RulesView view = ctx.subject(RulesView.class);
            Optional<Target> target = Target.parse(ctx.arg());
            if (target.isEmpty()) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_BAD_TARGET, Map.of("target", ctx.arg()));
                open(ctx.player(), RULES, view);
                return;
            }
            open(ctx.player(), RULES, new RulesView(view.key(), view.node(), target.get()));
        });
        bindings.action("guard:rules-back", ctx -> {
            RulesView view = ctx.subject(RulesView.class);
            ActionId node = view.node();
            if (node == null) {
                openHub(ctx.player(), view.key());
            } else {
                open(ctx.player(), RULES, new RulesView(view.key(), node.parent(), view.target()));
            }
        });
    }

    private void roles(MenuBindings bindings) {
        bindings.list(
                "guard:roles", ctx -> List.copyOf(ctx.subject(RolesView.class).rows()));
        bindings.placeholder(
                "guard_role_player", ctx -> ctx.entry(RoleRow.class).name());
        bindings.placeholder(
                "guard_role_name", ctx -> ctx.entry(RoleRow.class).role().value());
        bindings.placeholder(
                "guard_roles_region",
                ctx -> ctx.subject(RolesView.class).key().id().value());
        bindings.action("guard:role-next", ctx -> {
            RolesView view = ctx.subject(RolesView.class);
            RoleRow row = ctx.entry(RoleRow.class);
            List<RoleName> cycle = roleCycle(ctx.player(), view.key());
            if (cycle.isEmpty() || !mayChange(ctx.player(), view.key(), row.player(), cycle)) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_NOT_MANAGER);
                return;
            }
            int at = cycle.indexOf(row.role());
            RoleName next = cycle.get((at + 1) % cycle.size());
            setRole(ctx.player(), view.key(), row.player(), row.name(), next);
        });
        bindings.action("guard:role-remove", ctx -> {
            RolesView view = ctx.subject(RolesView.class);
            RoleRow row = ctx.entry(RoleRow.class);
            List<RoleName> cycle = roleCycle(ctx.player(), view.key());
            if (cycle.isEmpty() || !mayChange(ctx.player(), view.key(), row.player(), cycle)) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_NOT_MANAGER);
                return;
            }
            setRole(ctx.player(), view.key(), row.player(), row.name(), null);
        });
        bindings.action("guard-role-add", ctx -> {
            RolesView view = ctx.subject(RolesView.class);
            String name = ctx.arg().strip();
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
            if (target == null) {
                Player online = Bukkit.getPlayerExact(name);
                target = online;
            }
            if (target == null) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_UNKNOWN_PLAYER, Map.of("player", name));
                openRoles(ctx.player(), view.key());
                return;
            }
            if (roleCycle(ctx.player(), view.key()).isEmpty()) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_NOT_MANAGER);
                return;
            }
            setRole(
                    ctx.player(),
                    view.key(),
                    target.getUniqueId(),
                    target.getName() == null ? name : target.getName(),
                    RoleName.MEMBER);
        });
        bindings.action("guard:roles-back", ctx -> {
            RegionKey key = ctx.subject(RolesView.class).key();
            if (ctx.player().hasPermission(BASE + "menu")) {
                openHub(ctx.player(), key);
            } else {
                ctx.player().closeInventory();
            }
        });
    }

    private void behaviours(MenuBindings bindings) {
        bindings.list("guard:behaviours", ctx -> {
            RegionKey key = ctx.subject(BehavioursView.class).key();
            Map<BehaviourKey, String> set =
                    service.region(key).map(r -> r.behaviours().asMap()).orElse(Map.of());
            List<Object> rows = new ArrayList<>();
            for (BehaviourKey behaviour : BehaviourKey.values()) {
                rows.add(new BehaviourRow(behaviour, set.get(behaviour)));
            }
            return rows;
        });
        bindings.placeholder(
                "guard_behaviour_key",
                ctx -> ctx.entry(BehaviourRow.class).key().key());
        bindings.placeholder("guard_behaviour_value", ctx -> {
            String value = ctx.entry(BehaviourRow.class).value();
            return value == null ? "-" : StyledText.escape(value);
        });
        bindings.placeholder(
                "guard_behaviour_icon", ctx -> ctx.entry(BehaviourRow.class).value() == null ? "GRAY_DYE" : "NAME_TAG");
        bindings.placeholder(
                "guard_behaviours_region",
                ctx -> ctx.subject(BehavioursView.class).key().id().value());
        bindings.action("guard-behaviour-set", ctx -> {
            if (!allowed(ctx, "set")) {
                return;
            }
            RegionKey key = ctx.subject(BehavioursView.class).key();
            BehaviourKey behaviour = ctx.entry(BehaviourRow.class).key();
            String value = ctx.arg().strip();
            Optional<String> normal = behaviour.normalise(value);
            if (normal.isEmpty()) {
                feedback.send(ctx.player(), GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", value));
                open(ctx.player(), BEHAVIOURS, new BehavioursView(key), ctx.page());
                return;
            }
            edit(
                    ctx.player(),
                    key,
                    "set",
                    r -> r.withBehaviours(r.behaviours().with(behaviour, value)),
                    GuardMessageKey.BEHAVIOUR_SET,
                    Map.of("key", behaviour.key(), "value", value),
                    () -> open(ctx.player(), BEHAVIOURS, new BehavioursView(key), ctx.page()));
        });
        bindings.action("guard:behaviour-clear", ctx -> {
            if (!allowed(ctx, "set")) {
                return;
            }
            RegionKey key = ctx.subject(BehavioursView.class).key();
            BehaviourRow row = ctx.entry(BehaviourRow.class);
            if (row.value() == null) {
                return;
            }
            edit(
                    ctx.player(),
                    key,
                    "set",
                    r -> r.withBehaviours(r.behaviours().without(row.key())),
                    GuardMessageKey.BEHAVIOUR_CLEARED,
                    Map.of("key", row.key().key(), "value", "-"),
                    () -> open(ctx.player(), BEHAVIOURS, new BehavioursView(key), ctx.page()));
        });
        bindings.action(
                "guard:behaviours-back",
                ctx -> openHub(ctx.player(), ctx.subject(BehavioursView.class).key()));
    }

    private void templates(MenuBindings bindings) {
        bindings.list("guard:templates", ctx -> {
            RegionKey key = ctx.subject(TemplatesView.class).key();
            String linked = service.region(key).map(Region::template).orElse(null);
            List<Object> rows = new ArrayList<>();
            service.config().templates().values().stream()
                    .sorted(Comparator.comparing(Template::name))
                    .forEach(t -> rows.add(new TemplateRow(t, t.name().equals(linked))));
            return rows;
        });
        bindings.placeholder(
                "guard_template_name",
                ctx -> ctx.entry(TemplateRow.class).template().name());
        bindings.placeholder(
                "guard_template_rules",
                ctx -> String.valueOf(
                        ctx.entry(TemplateRow.class).template().rules().size()));
        bindings.placeholder(
                "guard_template_behaviours",
                ctx -> String.valueOf(ctx.entry(TemplateRow.class)
                        .template()
                        .behaviours()
                        .asMap()
                        .size()));
        bindings.placeholder(
                "guard_template_icon", ctx -> ctx.entry(TemplateRow.class).linked() ? "ENCHANTED_BOOK" : "BOOK");
        bindings.placeholder(
                "guard_templates_region",
                ctx -> ctx.subject(TemplatesView.class).key().id().value());
        bindings.action("guard:template-apply", ctx -> {
            if (!allowed(ctx, "template")) {
                return;
            }
            RegionKey key = ctx.subject(TemplatesView.class).key();
            String name = ctx.entry(TemplateRow.class).template().name();
            edit(
                    ctx.player(),
                    key,
                    "template",
                    r -> r.withTemplate(name),
                    GuardMessageKey.TEMPLATE_SET,
                    Map.of("template", name),
                    () -> open(ctx.player(), TEMPLATES, new TemplatesView(key)));
        });
        bindings.action("guard:template-clear", ctx -> {
            if (!allowed(ctx, "template")) {
                return;
            }
            RegionKey key = ctx.subject(TemplatesView.class).key();
            edit(
                    ctx.player(),
                    key,
                    "template",
                    r -> r.withTemplate(null),
                    GuardMessageKey.TEMPLATE_CLEARED,
                    Map.of("template", "-"),
                    () -> open(ctx.player(), TEMPLATES, new TemplatesView(key)));
        });
        bindings.action(
                "guard:templates-back",
                ctx -> openHub(ctx.player(), ctx.subject(TemplatesView.class).key()));
    }

    private void history(MenuBindings bindings) {
        bindings.list(
                "guard:history",
                ctx -> List.copyOf(ctx.subject(HistoryView.class).rows()));
        bindings.placeholder(
                "guard_history_id",
                ctx -> String.valueOf(ctx.entry(HistoryEntry.class).id()));
        bindings.placeholder(
                "guard_history_time",
                ctx -> TIME.format(ctx.entry(HistoryEntry.class).at()));
        bindings.placeholder(
                "guard_history_actor", ctx -> ctx.entry(HistoryEntry.class).actor());
        bindings.placeholder(
                "guard_history_change", ctx -> ctx.entry(HistoryEntry.class).change());
        bindings.placeholder(
                "guard_history_region",
                ctx -> ctx.subject(HistoryView.class).key().id().value());
        bindings.action("guard:history-undo", ctx -> {
            if (!allowed(ctx, "undo")) {
                return;
            }
            HistoryEntry entry = ctx.entry(HistoryEntry.class);
            RegionKey key = ctx.subject(HistoryView.class).key();
            command.answerMenu(
                    ctx.player(),
                    service.undo(entry.id(), command.editContext(ctx.player())),
                    GuardMessageKey.UNDONE,
                    Map.of("id", String.valueOf(entry.id()), "region", key.id().value()),
                    () -> openHistory(ctx.player(), key));
        });
        bindings.action(
                "guard:history-back",
                ctx -> openHub(ctx.player(), ctx.subject(HistoryView.class).key()));
    }

    // ---- opening ------------------------------------------------------------------------------------------------

    /**
     * What {@code /guard} and {@code /guard menu} open: the region list for staff, the roles window of the region
     * the player stands in for its owner or manager.
     */
    public void openFor(Player player) {
        if (player.hasPermission(BASE + "menu")) {
            open(player, LIST, new RegionsView(player.getWorld().getName(), null));
            return;
        }
        Optional<RegionKey> managed = managedHere(player);
        if (managed.isPresent()) {
            openRoles(player, managed.get());
        } else {
            feedback.send(player, GuardMessageKey.ERROR_NOT_MANAGER);
        }
    }

    /** True when {@code player} owns or manages a region where they stand. */
    public boolean managesHere(Player player) {
        return managedHere(player).isPresent();
    }

    private Optional<RegionKey> managedHere(Player player) {
        Location at = player.getLocation();
        var index = service.snapshot().worlds().get(player.getWorld().getName());
        if (index == null || index.locked() || at == null) {
            return Optional.empty();
        }
        RegionSet set = index.at(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        for (Region region : set.stored()) {
            if (region.roleOf(player.getUniqueId()).map(RoleName::manages).orElse(false)) {
                return Optional.of(region.key());
            }
        }
        return Optional.empty();
    }

    private void openHub(Player player, RegionKey key) {
        if (service.region(key).isEmpty()) {
            feedback.send(
                    player,
                    GuardMessageKey.ERROR_NO_SUCH_REGION,
                    Map.of("region", key.id().value()));
            open(player, LIST, new RegionsView(key.world(), null));
            return;
        }
        open(player, HUB, new RegionView(key));
    }

    private void openRoles(Player player, RegionKey key) {
        Optional<Region> region = service.region(key);
        if (region.isEmpty()) {
            return;
        }
        if (roleCycle(player, key).isEmpty()) {
            feedback.send(player, GuardMessageKey.ERROR_NOT_MANAGER);
            return;
        }
        List<RoleRow> rows = new ArrayList<>();
        region.get().roles().forEach((id, role) -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            rows.add(new RoleRow(
                    id, offline.getName() == null ? id.toString().substring(0, 8) : offline.getName(), role));
        });
        rows.sort(Comparator.comparingInt((RoleRow r) ->
                        RoleName.BUILT_IN.indexOf(r.role()) < 0 ? 9 : RoleName.BUILT_IN.indexOf(r.role()))
                .thenComparing(RoleRow::name, String.CASE_INSENSITIVE_ORDER));
        open(player, ROLES, new RolesView(key, rows));
    }

    private void openHistory(Player player, RegionKey key) {
        var ignored = service.history(key, 0, HISTORY_ROWS)
                .whenComplete((rows, error) -> onPlayer.accept(player, () -> {
                    if (error != null) {
                        feedback.send(player, GuardMessageKey.ERROR_STORE_FAILED);
                        return;
                    }
                    open(player, HISTORY, new HistoryView(key, rows));
                }));
    }

    private void open(Player player, String spec, Object subject) {
        menus.open(player, spec, subject);
    }

    private void open(Player player, String spec, Object subject, int page) {
        menus.open(player, spec, subject, page);
    }

    // ---- rows ---------------------------------------------------------------------------------------------------

    private List<Object> regionRows(MenuContext ctx) {
        RegionsView view = ctx.subject(RegionsView.class);
        List<Region> regions = new ArrayList<>(service.regions(view.world()));
        RegionId parent = view.parent();
        if (parent != null) {
            regions.removeIf(r -> !parent.equals(r.parent()));
        }
        regions.sort(Comparator.comparingInt(Region::priority).reversed().thenComparing(r -> r.id().value()));
        return List.copyOf(regions);
    }

    private List<Object> actionRows(MenuContext ctx) {
        RulesView view = ctx.subject(RulesView.class);
        Optional<Region> region = service.region(view.key());
        List<Object> rows = new ArrayList<>();
        for (ActionId id : service.tree().children(view.node())) {
            Effect effect = null;
            if (region.isPresent()) {
                for (Rule rule : region.get().rules()) {
                    if (rule.action().equals(id) && rule.target().equals(view.target())) {
                        effect = rule.effect();
                    }
                }
            }
            rows.add(new ActionRow(id, effect, service.tree().children(id).size()));
        }
        return rows;
    }

    private List<Region> children(Region region) {
        List<Region> out = new ArrayList<>();
        for (Region other : service.regions(region.world())) {
            if (region.id().equals(other.parent())) {
                out.add(other);
            }
        }
        return out;
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private List<Target> targets() {
        Set<Target> out = new LinkedHashSet<>();
        out.add(everyone());
        Target.parse("nonmember").ifPresent(out::add);
        List<RoleName> roles = new ArrayList<>(RoleName.BUILT_IN);
        roles.addAll(service.config().extraRoles());
        for (RoleName role : roles) {
            Target.parse("role:" + role.value()).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static Target everyone() {
        return Target.parse("everyone").orElseThrow();
    }

    /** The roles {@code player} may hand out in the region: all for staff, member and guest for its managers. */
    private List<RoleName> roleCycle(Player player, RegionKey key) {
        if (player.hasPermission(BASE + "role")) {
            List<RoleName> all = new ArrayList<>(RoleName.BUILT_IN);
            all.addAll(service.config().extraRoles());
            return all;
        }
        boolean manages = service.region(key)
                .flatMap(r -> r.roleOf(player.getUniqueId()))
                .map(RoleName::manages)
                .orElse(false);
        return manages ? List.of(RoleName.MEMBER, RoleName.GUEST) : List.of();
    }

    private boolean mayChange(Player player, RegionKey key, UUID target, List<RoleName> cycle) {
        if (player.hasPermission(BASE + "role")) {
            return true;
        }
        RoleName current = service.region(key).flatMap(r -> r.roleOf(target)).orElse(null);
        return current == null || cycle.contains(current);
    }

    private void setRole(Player player, RegionKey key, UUID target, String name, @Nullable RoleName role) {
        Map<String, String> values =
                Map.of("region", key.id().value(), "player", name, "role", role == null ? "-" : role.value());
        edit(
                player,
                key,
                "role",
                r -> r.withRole(target, role),
                role == null ? GuardMessageKey.ROLE_CLEARED : GuardMessageKey.ROLE_SET,
                values,
                () -> openRoles(player, key));
    }

    private void edit(
            Player player,
            RegionKey key,
            String change,
            UnaryOperator<Region> edit,
            GuardMessageKey success,
            Map<String, String> extra,
            Runnable after) {
        Map<String, String> values = new HashMap<>(extra);
        values.putIfAbsent("region", key.id().value());
        command.answerMenu(
                player,
                service.edit(key, change, edit, command.editContext(player)),
                success,
                Map.copyOf(values),
                after);
    }

    private boolean allowed(MenuActionContext ctx, String node) {
        if (ctx.player().hasPermission(BASE + node)) {
            return true;
        }
        feedback.send(ctx.player(), SharedMessageKey.COMMAND_NO_PERMISSION);
        ctx.refuse();
        return false;
    }

    private static RegionKey hubKey(MenuActionContext ctx) {
        return ctx.subject(RegionView.class).key();
    }

    private String hubValue(MenuContext ctx, Function<Region, String> read) {
        return service.region(ctx.subject(RegionView.class).key()).map(read).orElse("-");
    }

    private static String regionIcon(Region region) {
        if (region.locked()) {
            return "IRON_BARS";
        }
        return switch (region.shape().kind()) {
            case "global" -> "FILLED_MAP";
            case "chunks" -> "GRASS_BLOCK";
            case "cylinder", "sphere" -> "SNOWBALL";
            case "polygon" -> "PRISMARINE_SHARD";
            default -> "STONE_BRICKS";
        };
    }
}

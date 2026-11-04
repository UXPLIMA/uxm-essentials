package com.uxplima.uxmessentials.guard.adapter.inbound.command;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.guard.adapter.BukkitSubject;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.adapter.Selections;
import com.uxplima.uxmessentials.guard.adapter.movement.PlayerTracks;
import com.uxplima.uxmessentials.guard.adapter.outbound.RegionDisplay;
import com.uxplima.uxmessentials.guard.application.EditContext;
import com.uxplima.uxmessentials.guard.application.GuardConfig;
import com.uxplima.uxmessentials.guard.application.GuardError;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.application.GuardStats;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.Explanation;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.Nullable;

/**
 * {@code /guard} (Turkish {@code /bolge}): every region operation. Each sub command carries its own permission
 * {@code uxmessentials.guard.<sub>}; region owners and managers may also use {@code member} on their own region.
 * Edits run on the async lane and answer when stored.
 */
public final class GuardCommand implements CommandRegistration {

    private static final String BASE = "uxmessentials.guard.";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final GuardRuntime guard;
    private final GuardService service;
    private final Selections selections;
    private final RegionDisplay display;
    private final PlayerTracks tracks;
    private final CommandFeedback feedback;
    private final GuardImports imports;
    private final Consumer<Player> openMenu;
    private final Runnable reload;
    private final BiConsumer<Player, Runnable> onPlayer;
    private final boolean rgAlias;

    public GuardCommand(
            GuardRuntime guard,
            Selections selections,
            RegionDisplay display,
            PlayerTracks tracks,
            CommandFeedback feedback,
            GuardImports imports,
            Consumer<Player> openMenu,
            Runnable reload,
            BiConsumer<Player, Runnable> onPlayer,
            boolean rgAlias) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.service = guard.service();
        this.selections = Objects.requireNonNull(selections, "selections");
        this.display = Objects.requireNonNull(display, "display");
        this.tracks = Objects.requireNonNull(tracks, "tracks");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.openMenu = Objects.requireNonNull(openMenu, "openMenu");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.onPlayer = Objects.requireNonNull(onPlayer, "onPlayer");
        this.rgAlias = rgAlias;
    }

    /** Whatever runs the WorldGuard import; kept behind an interface so the command does not know its files. */
    public interface GuardImports {
        /** Run {@code mode} (dry-run, run, undo) from {@code source} (yaml or sql) and answer what happened. */
        CompletableFuture<ImportOutcome> run(String mode, String source, EditContext ctx);
    }

    /**
     * What an import did, for the player's message; the full report goes to a file.
     *
     * @param kind what was done
     * @param regions regions imported, would be imported, or removed by an undo
     * @param rules rules converted
     * @param behaviours behaviours converted
     * @param notes report lines (flags not converted, unknown names, skipped regions)
     * @param skipped regions skipped because the id already exists
     */
    public record ImportOutcome(ImportKind kind, int regions, int rules, int behaviours, int notes, int skipped) {}

    /** The four ways an import ends. */
    public enum ImportKind {
        NOTHING_TO_UNDO,
        UNDONE,
        DRY_RUN,
        IMPORTED
    }

    @Override
    public String description() {
        return "Protect regions: rules, roles, behaviours, history";
    }

    @Override
    public List<String> aliases() {
        return rgAlias ? List.of("bolge", "rg") : List.of("bolge");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("guard")
                .requires(src -> src.getSender().hasPermission(BASE + "use"))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player
                            && (player.hasPermission(BASE + "menu") || managesHere(player))) {
                        openMenu.accept(player);
                    } else {
                        help(ctx.getSource().getSender());
                    }
                    return Command.SINGLE_SUCCESS;
                });
        for (Sub sub : subs()) {
            root.then(Commands.literal(sub.name())
                    .requires(src -> src.getSender().hasPermission(sub.permission()))
                    .executes(ctx -> run(ctx, sub, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggest(ctx, builder, sub))
                            .executes(ctx -> run(ctx, sub, StringArgumentType.getString(ctx, "args")))));
        }
        return root.build();
    }

    /** One sub command: its word, its permission node, its handler and what each argument position suggests. */
    private record Sub(
            String name,
            String permission,
            BiConsumer<CommandSender, String[]> handler,
            List<Function<CommandSender, List<String>>> suggestions) {}

    private List<Sub> subs() {
        List<Sub> subs = new ArrayList<>();
        Function<CommandSender, List<String>> regions = this::regionIds;
        Function<CommandSender, List<String>> actions =
                s -> service.tree().ids().stream().map(ActionId::value).toList();
        Function<CommandSender, List<String>> targets = s -> List.of(
                "everyone", "nonmember", "role:owner", "role:manager", "role:member", "role:guest", "perm:", "cond:");
        Function<CommandSender, List<String>> effects = s -> List.of("allow", "deny", "clear");
        Function<CommandSender, List<String>> players =
                s -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        Function<CommandSender, List<String>> roles = s -> {
            List<String> all = new ArrayList<>();
            RoleName.BUILT_IN.forEach(r -> all.add(r.value()));
            service.config().extraRoles().forEach(r -> all.add(r.value()));
            all.add("clear");
            return all;
        };
        Function<CommandSender, List<String>> keys = s -> {
            List<String> all = new ArrayList<>();
            for (BehaviourKey key : BehaviourKey.values()) {
                all.add(key.key());
            }
            return all;
        };
        Function<CommandSender, List<String>> shapes =
                s -> List.of("cuboid", "polygon", "global", "cylinder", "sphere", "chunks");
        Function<CommandSender, List<String>> templates = s -> {
            List<String> all = new ArrayList<>(service.config().templates().keySet());
            all.add("clear");
            return all;
        };
        add(subs, "help", "use", (s, a) -> help(s));
        add(subs, "define", "define", this::define, s -> List.of(), shapes);
        add(subs, "redefine", "define", this::redefine, regions, shapes);
        add(subs, "remove", "remove", this::remove, regions);
        add(subs, "restore", "remove", this::restore);
        add(subs, "info", "info", this::info, regions);
        add(subs, "list", "list", this::list, s -> worldNames());
        add(subs, "wand", "define", this::wand);
        add(subs, "pos1", "define", (s, a) -> corner(s, true));
        add(subs, "pos2", "define", (s, a) -> corner(s, false));
        add(subs, "point", "define", this::point);
        add(subs, "clear", "define", this::clearSelection);
        add(
                subs,
                "expand",
                "define",
                this::expand,
                s -> List.of("1", "5", "10", "vert"),
                s -> List.of("up", "down", "north", "south", "east", "west", "all"));
        add(subs, "rule", "rule", this::rule, regions, actions, targets, effects);
        add(subs, "role", "role", this::role, regions, players, roles);
        add(subs, "member", "member", this::member, regions, players, s -> List.of("member", "guest", "clear"));
        add(subs, "set", "set", this::set, regions, keys);
        add(subs, "priority", "priority", this::priority, regions);
        add(subs, "parent", "parent", this::parent, regions, regions);
        add(subs, "family", "family", this::family, regions);
        add(subs, "tag", "tag", this::tag, regions, s -> List.of("add", "remove", "bulk"));
        add(subs, "template", "template", this::template, regions, templates);
        add(subs, "scope", "scope", this::scope, regions, s -> scopeNames());
        add(subs, "lock", "lock", (s, a) -> lock(s, a, true), regions);
        add(subs, "unlock", "lock", (s, a) -> lock(s, a, false), regions);
        add(subs, "bypass", "bypass", (s, a) -> bypass(s));
        add(subs, "show", "show", this::show, regions);
        add(subs, "hide", "show", (s, a) -> hide(s));
        add(subs, "here", "here", (s, a) -> here(s));
        add(subs, "near", "near", this::near, s -> List.of("16", "32", "64", "128"));
        add(subs, "why", "why", this::why, actions);
        add(subs, "test", "test", this::test, players, actions, regions);
        add(subs, "inspect", "inspect", (s, a) -> inspect(s));
        add(subs, "history", "history", this::history, regions);
        add(subs, "undo", "undo", this::undo);
        add(subs, "log", "log", this::log, players);
        add(subs, "status", "status", (s, a) -> status(s));
        add(subs, "actions", "use", this::actions, actions);
        add(
                subs,
                "import",
                "import",
                this::importCommand,
                s -> List.of("worldguard"),
                s -> List.of("dry-run", "run", "undo"),
                s -> List.of("yaml", "sql"));
        add(subs, "reload", "reload", (s, a) -> {
            reload.run();
            feedback.send(s, GuardMessageKey.RELOADED);
        });
        add(subs, "menu", "use", (s, a) -> withPlayer(s, openMenu));
        return subs;
    }

    @SafeVarargs
    private void add(
            List<Sub> subs,
            String name,
            String node,
            BiConsumer<CommandSender, String[]> handler,
            Function<CommandSender, List<String>>... suggestions) {
        List<Function<CommandSender, List<String>>> list = new ArrayList<>(suggestions.length);
        for (Function<CommandSender, List<String>> suggestion : suggestions) {
            list.add(suggestion);
        }
        subs.add(new Sub(name, BASE + node, handler, List.copyOf(list)));
    }

    private int run(CommandContext<CommandSourceStack> ctx, Sub sub, String raw) {
        String[] args = raw.isBlank() ? new String[0] : raw.strip().split("\\s+", -1);
        sub.handler().accept(ctx.getSource().getSender(), args);
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, Sub sub) {
        String typed = builder.getRemaining();
        String[] parts = typed.split(" ", -1);
        int index = parts.length - 1;
        if (index >= sub.suggestions().size()) {
            return builder.buildFuture();
        }
        String last = parts[index].toLowerCase(Locale.ROOT);
        SuggestionsBuilder at = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        for (String option : sub.suggestions().get(index).apply(ctx.getSource().getSender())) {
            if (option.toLowerCase(Locale.ROOT).startsWith(last)) {
                at.suggest(option);
            }
        }
        return at.buildFuture();
    }

    // ---- regions ------------------------------------------------------------------------------------------------

    private void define(CommandSender sender, String[] args) {
        if (args.length < 1 || !RegionId.valid(args[0])) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_ID);
            return;
        }
        Optional<ShapeIn> shape = shape(sender, Arrays.copyOfRange(args, 1, args.length));
        if (shape.isEmpty()) {
            return;
        }
        RegionKey key = new RegionKey(shape.get().world(), RegionId.of(args[0]));
        answer(
                sender,
                service.create(key, shape.get().shape(), null, ctx(sender)),
                GuardMessageKey.CREATED,
                Map.of("region", key.id().value(), "world", key.world()));
    }

    private void redefine(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty()) {
            return;
        }
        Optional<ShapeIn> shape = shape(sender, Arrays.copyOfRange(args, 1, args.length));
        if (shape.isEmpty()) {
            return;
        }
        edit(
                sender,
                key.get(),
                "redefine",
                r -> r.withShape(shape.get().shape()),
                GuardMessageKey.REDEFINED,
                Map.of("region", key.get().id().value()));
    }

    private void remove(CommandSender sender, String[] args) {
        key(sender, args, 0)
                .ifPresent(key -> answer(
                        sender,
                        service.remove(key, ctx(sender)),
                        GuardMessageKey.REMOVED,
                        Map.of("region", key.id().value())));
    }

    private void restore(CommandSender sender, String[] args) {
        Optional<RegionKey> key = rawKey(sender, args, 0);
        key.ifPresent(k -> answer(
                sender, service.restore(k, ctx(sender)), GuardMessageKey.RESTORED, Map.of("region", k.id().value())));
    }

    private void info(CommandSender sender, String[] args) {
        Optional<Region> region =
                args.length == 0 ? topHere(sender) : key(sender, args, 0).flatMap(service::region);
        if (region.isEmpty()) {
            if (args.length == 0) {
                feedback.send(sender, GuardMessageKey.ERROR_NO_REGION_HERE);
            }
            return;
        }
        Region r = region.get();
        feedback.send(sender, GuardMessageKey.INFO_HEADER, Map.of("region", r.id().value(), "world", r.world()));
        Bounds b = r.shape().bounds();
        line(
                sender,
                GuardMessageKey.INFO_SHAPE,
                r.shape().kind()
                        + (r.shape() instanceof Shape.Global
                                ? ""
                                : " " + b.minX() + "," + b.minY() + "," + b.minZ() + " .. " + b.maxX() + "," + b.maxY()
                                        + "," + b.maxZ()));
        line(sender, GuardMessageKey.INFO_SCOPE, r.scope().value());
        line(sender, GuardMessageKey.INFO_PRIORITY, String.valueOf(r.priority()));
        line(
                sender,
                GuardMessageKey.INFO_PARENT,
                r.parent() == null ? "-" : r.parent().value());
        line(sender, GuardMessageKey.INFO_FAMILY, r.family() == null ? "-" : r.family());
        line(sender, GuardMessageKey.INFO_TEMPLATE, r.template() == null ? "-" : r.template());
        line(sender, GuardMessageKey.INFO_TAGS, r.tags().isEmpty() ? "-" : String.join(", ", r.tags()));
        if (r.locked()) {
            feedback.send(sender, GuardMessageKey.INFO_LOCKED);
        }
        for (Rule rule : r.rules()) {
            line(sender, GuardMessageKey.INFO_RULE, rule.toString());
        }
        r.behaviours().asMap().forEach((k, v) -> line(sender, k.key(), v));
        Map<RoleName, List<String>> byRole = new LinkedHashMap<>();
        r.roles()
                .forEach((id, role) ->
                        byRole.computeIfAbsent(role, x -> new ArrayList<>()).add(name(id)));
        byRole.forEach((role, names) -> line(sender, role.value(), String.join(", ", names)));
    }

    private void list(CommandSender sender, String[] args) {
        String world = args.length > 0 && Bukkit.getWorld(args[0]) != null ? args[0] : worldOf(sender);
        if (world == null) {
            feedback.send(
                    sender, GuardMessageKey.ERROR_UNKNOWN_WORLD, Map.of("world", args.length > 0 ? args[0] : "-"));
            return;
        }
        List<Region> regions = service.regions(world);
        if (regions.isEmpty()) {
            feedback.send(sender, GuardMessageKey.LIST_EMPTY, Map.of("world", world));
            return;
        }
        int page = args.length > 1 ? parseInt(args[1], 1) : 1;
        int per = 10;
        int pages = (regions.size() + per - 1) / per;
        page = Math.max(1, Math.min(page, pages));
        feedback.send(
                sender,
                GuardMessageKey.LIST_HEADER,
                Map.of(
                        "world",
                        world,
                        "count",
                        String.valueOf(regions.size()),
                        "page",
                        String.valueOf(page),
                        "pages",
                        String.valueOf(pages)));
        for (Region region : regions.subList((page - 1) * per, Math.min(regions.size(), page * per))) {
            feedback.send(
                    sender,
                    GuardMessageKey.LIST_ENTRY,
                    Map.of(
                            "region",
                            region.id().value(),
                            "priority",
                            String.valueOf(region.priority()),
                            "shape",
                            region.shape().kind(),
                            "rules",
                            String.valueOf(region.rules().size()),
                            "world",
                            world));
        }
    }

    // ---- selection ----------------------------------------------------------------------------------------------

    private void wand(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            ItemStack wand = GuardWand.create();
            player.getInventory().addItem(wand);
            feedback.send(player, GuardMessageKey.WAND_GIVEN);
        });
    }

    private void corner(CommandSender sender, boolean first) {
        withPlayer(sender, player -> {
            Location at = player.getLocation();
            if (at == null) {
                return;
            }
            if (first) {
                selections.first(player, at.getBlockX(), at.getBlockY(), at.getBlockZ());
            } else {
                selections.second(player, at.getBlockX(), at.getBlockY(), at.getBlockZ());
            }
            feedback.send(player, first ? GuardMessageKey.POS1_SET : GuardMessageKey.POS2_SET, xyz(at));
        });
    }

    private void point(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            Location at = player.getLocation();
            if (at == null) {
                return;
            }
            int n = selections.point(player, at.getBlockX(), at.getBlockY(), at.getBlockZ());
            Map<String, String> values = new LinkedHashMap<>(xyz(at));
            values.put("count", String.valueOf(n));
            feedback.send(player, GuardMessageKey.POINT_ADDED, values);
        });
    }

    private void clearSelection(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            selections.clear(player);
            feedback.send(player, GuardMessageKey.SELECTION_CLEARED);
        });
    }

    private void expand(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            if (args.length < 1) {
                feedback.send(player, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", "-"));
                return;
            }
            boolean ok;
            if (args[0].equalsIgnoreCase("vert")) {
                World world = player.getWorld();
                ok = selections.expand(player, 0, 4096, 0, 0, 4096, 0);
                if (ok) {
                    selections.cuboid(player).ifPresent(sel -> {
                        Bounds b = sel.shape().bounds();
                        selections.first(player, b.minX(), world.getMinHeight(), b.minZ());
                        selections.second(player, b.maxX(), world.getMaxHeight() - 1, b.maxZ());
                    });
                }
            } else {
                int n = parseInt(args[0], 0);
                String dir = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : facing(player);
                ok = switch (dir) {
                    case "up" -> selections.expand(player, 0, 0, 0, 0, n, 0);
                    case "down" -> selections.expand(player, 0, n, 0, 0, 0, 0);
                    case "north" -> selections.expand(player, 0, 0, n, 0, 0, 0);
                    case "south" -> selections.expand(player, 0, 0, 0, 0, 0, n);
                    case "west" -> selections.expand(player, n, 0, 0, 0, 0, 0);
                    case "east" -> selections.expand(player, 0, 0, 0, n, 0, 0);
                    case "all" -> selections.expand(player, n, n, n, n, n, n);
                    default -> false;
                };
            }
            feedback.send(player, ok ? GuardMessageKey.SELECTION_EXPANDED : GuardMessageKey.ERROR_NO_SELECTION);
        });
    }

    // ---- rules, roles, behaviours -------------------------------------------------------------------------------

    private void rule(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty()) {
            return;
        }
        if (args.length < 4) {
            feedback.usage(
                    sender,
                    "guard rule",
                    "<region> <action> <target> <allow|deny|clear>",
                    "Set or clear a protection rule");
            return;
        }
        ActionId action;
        try {
            action = ActionId.of(args[1]);
        } catch (IllegalArgumentException e) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_ACTION, Map.of("action", args[1]));
            return;
        }
        if (!service.tree().contains(action)) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_ACTION, Map.of("action", args[1]));
            return;
        }
        String last = args[args.length - 1];
        String targetText = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
        Optional<Target> target = Target.parse(targetText);
        if (target.isEmpty()) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_TARGET, Map.of("target", targetText));
            return;
        }
        Map<String, String> values = Map.of(
                "region",
                key.get().id().value(),
                "action",
                action.value(),
                "target",
                target.get().text(),
                "effect",
                last.toLowerCase(Locale.ROOT));
        if (last.equalsIgnoreCase("clear") || last.equalsIgnoreCase("temizle")) {
            edit(
                    sender,
                    key.get(),
                    "rule",
                    r -> r.withoutRule(action, target.get()),
                    GuardMessageKey.RULE_CLEARED,
                    values);
            return;
        }
        Optional<Effect> effect = Effect.parse(last);
        if (effect.isEmpty()) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", last));
            return;
        }
        Rule rule = new Rule(action, target.get(), effect.get());
        edit(sender, key.get(), "rule", r -> r.withRule(rule), GuardMessageKey.RULE_SET, values);
    }

    private void role(CommandSender sender, String[] args) {
        roleChange(sender, args, true);
    }

    private void member(CommandSender sender, String[] args) {
        roleChange(sender, args, false);
    }

    private void roleChange(CommandSender sender, String[] args, boolean staff) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty()) {
            return;
        }
        if (args.length < 3) {
            feedback.usage(
                    sender,
                    "guard " + (staff ? "role" : "member"),
                    "<region> <player> <role|clear>",
                    "Give or take a region role");
            return;
        }
        OfflinePlayer target = offline(args[1]);
        if (target == null) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_PLAYER, Map.of("player", args[1]));
            return;
        }
        boolean clear = args[2].equalsIgnoreCase("clear") || args[2].equalsIgnoreCase("temizle");
        RoleName role;
        try {
            role = clear ? null : RoleName.of(args[2]);
        } catch (IllegalArgumentException e) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", args[2]));
            return;
        }
        if (!staff) {
            Optional<Region> region = service.region(key.get());
            boolean manages = sender instanceof Player p
                    && region.flatMap(r -> r.roleOf(p.getUniqueId()))
                            .map(RoleName::manages)
                            .orElse(false);
            boolean allowedRole = role == null || role.equals(RoleName.MEMBER) || role.equals(RoleName.GUEST);
            boolean targetManaged = region.flatMap(r -> r.roleOf(target.getUniqueId()))
                    .map(r -> !r.manages())
                    .orElse(true);
            if (!(sender.hasPermission(BASE + "role") || (manages && allowedRole && targetManaged))) {
                feedback.send(sender, GuardMessageKey.ERROR_NOT_MANAGER);
                return;
            }
        }
        UUID id = target.getUniqueId();
        String name = target.getName() == null ? args[1] : target.getName();
        Map<String, String> values =
                Map.of("region", key.get().id().value(), "player", name, "role", role == null ? "-" : role.value());
        edit(
                sender,
                key.get(),
                "role",
                r -> r.withRole(id, role),
                role == null ? GuardMessageKey.ROLE_CLEARED : GuardMessageKey.ROLE_SET,
                values);
    }

    private void set(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty()) {
            return;
        }
        if (args.length < 3) {
            feedback.usage(sender, "guard set", "<region> <behaviour> <value|clear>", "Set a region behaviour");
            return;
        }
        Optional<BehaviourKey> behaviour = BehaviourKey.parse(args[1]);
        if (behaviour.isEmpty()) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", args[1]));
            return;
        }
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Map<String, String> values =
                Map.of("region", key.get().id().value(), "key", behaviour.get().key(), "value", value);
        if (value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("temizle")) {
            edit(
                    sender,
                    key.get(),
                    "set",
                    r -> r.withBehaviours(r.behaviours().without(behaviour.get())),
                    GuardMessageKey.BEHAVIOUR_CLEARED,
                    values);
            return;
        }
        if (behaviour.get().normalise(value).isEmpty()) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", value));
            return;
        }
        edit(
                sender,
                key.get(),
                "set",
                r -> r.withBehaviours(r.behaviours().with(behaviour.get(), value)),
                GuardMessageKey.BEHAVIOUR_SET,
                values);
    }

    private void priority(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 2) {
            return;
        }
        int priority = parseInt(args[1], Integer.MIN_VALUE);
        if (priority == Integer.MIN_VALUE) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", args[1]));
            return;
        }
        edit(
                sender,
                key.get(),
                "priority",
                r -> r.withPriority(priority),
                GuardMessageKey.PRIORITY_SET,
                Map.of("region", key.get().id().value(), "priority", String.valueOf(priority)));
    }

    private void parent(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 2) {
            return;
        }
        if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("temizle")) {
            edit(
                    sender,
                    key.get(),
                    "parent",
                    r -> r.withParent(null),
                    GuardMessageKey.PARENT_CLEARED,
                    Map.of("region", key.get().id().value()));
            return;
        }
        if (!RegionId.valid(args[1])) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_ID);
            return;
        }
        RegionId parent = RegionId.of(args[1]);
        edit(
                sender,
                key.get(),
                "parent",
                r -> r.withParent(parent),
                GuardMessageKey.PARENT_SET,
                Map.of("region", key.get().id().value(), "parent", parent.value()));
    }

    private void family(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 2) {
            return;
        }
        String family = args[1].equalsIgnoreCase("clear") ? null : args[1].toLowerCase(Locale.ROOT);
        edit(
                sender,
                key.get(),
                "family",
                r -> r.withFamily(family),
                GuardMessageKey.FAMILY_SET,
                Map.of("region", key.get().id().value(), "family", family == null ? "-" : family));
    }

    private void tag(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("bulk")) {
            bulk(sender, args);
            return;
        }
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 3) {
            feedback.usage(
                    sender,
                    "guard tag",
                    "<region> add|remove <tag>  |  bulk <tag> rule|set|template ...",
                    "Tag regions and edit them together");
            return;
        }
        String tag = args[2].toLowerCase(Locale.ROOT);
        boolean add = args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("ekle");
        edit(
                sender,
                key.get(),
                "tag",
                r -> {
                    Set<String> tags = new HashSet<>(r.tags());
                    if (add) {
                        tags.add(tag);
                    } else {
                        tags.remove(tag);
                    }
                    return r.withTags(tags);
                },
                add ? GuardMessageKey.TAG_ADDED : GuardMessageKey.TAG_REMOVED,
                Map.of("region", key.get().id().value(), "tag", tag));
    }

    private void bulk(CommandSender sender, String[] args) {
        String tag = args[1].toLowerCase(Locale.ROOT);
        String what = args[2].toLowerCase(Locale.ROOT);
        UnaryOperator<Region> change;
        if (what.equals("rule") && args.length >= 6) {
            Optional<Target> target = Target.parse(String.join(" ", Arrays.copyOfRange(args, 4, args.length - 1)));
            String last = args[args.length - 1];
            ActionId action;
            try {
                action = ActionId.of(args[3]);
            } catch (IllegalArgumentException e) {
                feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_ACTION, Map.of("action", args[3]));
                return;
            }
            if (target.isEmpty()) {
                feedback.send(sender, GuardMessageKey.ERROR_BAD_TARGET, Map.of("target", args[4]));
                return;
            }
            if (last.equalsIgnoreCase("clear")) {
                change = r -> r.withoutRule(action, target.get());
            } else {
                Optional<Effect> effect = Effect.parse(last);
                if (effect.isEmpty()) {
                    feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", last));
                    return;
                }
                Rule rule = new Rule(action, target.get(), effect.get());
                change = r -> r.withRule(rule);
            }
        } else if (what.equals("set") && args.length >= 5) {
            Optional<BehaviourKey> key = BehaviourKey.parse(args[3]);
            String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            if (key.isEmpty()
                    || (!value.equalsIgnoreCase("clear")
                            && key.get().normalise(value).isEmpty())) {
                feedback.send(sender, GuardMessageKey.ERROR_BAD_VALUE, Map.of("value", value));
                return;
            }
            change = value.equalsIgnoreCase("clear")
                    ? r -> r.withBehaviours(r.behaviours().without(key.get()))
                    : r -> r.withBehaviours(r.behaviours().with(key.get(), value));
        } else if (what.equals("template") && args.length >= 4) {
            String name = args[3].equalsIgnoreCase("clear") ? null : args[3].toLowerCase(Locale.ROOT);
            change = r -> r.withTemplate(name);
        } else {
            feedback.usage(
                    sender,
                    "guard tag bulk",
                    "<tag> rule <action> <target> <effect> | set <key> <value> | " + "template <name>",
                    "Edit every region with a tag");
            return;
        }
        var ignored = service.editTagged(tag, "bulk", change, ctx(sender)).whenComplete((result, error) -> {
            if (error != null || result.isErr()) {
                feedback.send(sender, GuardMessageKey.ERROR_STORE_FAILED);
            } else {
                feedback.send(
                        sender,
                        GuardMessageKey.TAG_BULK,
                        Map.of("tag", tag, "count", String.valueOf(result.orElseThrow())));
            }
        });
    }

    private void template(CommandSender sender, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("liste"))) {
            feedback.send(
                    sender,
                    GuardMessageKey.TEMPLATE_LIST,
                    Map.of(
                            "templates",
                            String.join(", ", service.config().templates().keySet())));
            return;
        }
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 2) {
            return;
        }
        String name = args[1].equalsIgnoreCase("clear") ? null : args[1].toLowerCase(Locale.ROOT);
        edit(
                sender,
                key.get(),
                "template",
                r -> r.withTemplate(name),
                name == null ? GuardMessageKey.TEMPLATE_CLEARED : GuardMessageKey.TEMPLATE_SET,
                Map.of("region", key.get().id().value(), "template", name == null ? "-" : name));
    }

    private void scope(CommandSender sender, String[] args) {
        Optional<RegionKey> key = key(sender, args, 0);
        if (key.isEmpty() || args.length < 2) {
            return;
        }
        GuardConfig config = service.config();
        GuardScope scope;
        if (args[1].equalsIgnoreCase("server")) {
            scope = config.serverScope();
        } else if (config.sharedSets().contains(args[1])) {
            scope = GuardScope.set(args[1]);
        } else {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_SCOPE, Map.of("scope", args[1]));
            return;
        }
        edit(
                sender,
                key.get(),
                "scope",
                r -> r.withScope(scope),
                GuardMessageKey.SCOPE_SET,
                Map.of("region", key.get().id().value(), "scope", scope.value()));
    }

    private void lock(CommandSender sender, String[] args, boolean locked) {
        key(sender, args, 0)
                .ifPresent(key -> edit(
                        sender,
                        key,
                        "lock",
                        r -> r.withLocked(locked),
                        locked ? GuardMessageKey.LOCK_ON : GuardMessageKey.LOCK_OFF,
                        Map.of("region", key.id().value())));
    }

    // ---- staff tools --------------------------------------------------------------------------------------------

    private void bypass(CommandSender sender) {
        withPlayer(sender, player -> {
            Optional<Boolean> on = guard.toggleBypass(player);
            if (on.isEmpty()) {
                feedback.send(player, GuardMessageKey.BYPASS_NO_PERMISSION);
            } else {
                feedback.send(player, on.get() ? GuardMessageKey.BYPASS_ON : GuardMessageKey.BYPASS_OFF);
            }
        });
    }

    private void show(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            List<Shape> shapes = new ArrayList<>();
            if (args.length > 0) {
                key(sender, args, 0).flatMap(service::region).ifPresent(r -> shapes.add(r.shape()));
            } else {
                Location at = player.getLocation();
                if (at != null) {
                    service.near(
                                    player.getWorld().getName(),
                                    at.getBlockX(),
                                    at.getBlockY(),
                                    at.getBlockZ(),
                                    service.config().displayRange())
                            .forEach(r -> shapes.add(r.shape()));
                }
                selections.cuboid(player).ifPresent(sel -> shapes.add(sel.shape()));
            }
            GuardConfig config = service.config();
            onPlayer.accept(
                    player,
                    () -> display.show(
                            player,
                            player.getWorld(),
                            shapes,
                            config.displayMode(),
                            config.displayRange(),
                            Color.fromRGB(0x55, 0xFF, 0xFF)));
            feedback.send(
                    player,
                    GuardMessageKey.SHOW_STARTED,
                    Map.of(
                            "count",
                            String.valueOf(shapes.size()),
                            "seconds",
                            String.valueOf(config.displayDuration().toSeconds())));
            scheduleHide(player, shapes, config);
        });
    }

    private void scheduleHide(Player player, List<Shape> shapes, GuardConfig config) {
        long seconds = config.displayDuration().toSeconds();
        if (config.displayMode().equals("particles")) {
            for (long s = 1; s < seconds; s++) {
                player.getScheduler()
                        .runDelayed(
                                guardPlugin(),
                                task -> display.particles(
                                        player,
                                        player.getWorld(),
                                        shapes,
                                        config.displayRange(),
                                        Color.fromRGB(0x55, 0xFF, 0xFF)),
                                null,
                                s * 20);
            }
            return;
        }
        player.getScheduler().runDelayed(guardPlugin(), task -> display.hide(player), null, seconds * 20);
    }

    private void hide(CommandSender sender) {
        withPlayer(sender, player -> {
            onPlayer.accept(player, () -> display.hide(player));
            feedback.send(player, GuardMessageKey.SHOW_STOPPED);
        });
    }

    private void here(CommandSender sender) {
        withPlayer(sender, player -> {
            Location at = player.getLocation();
            RegionSet set = at == null ? null : guard.at(at);
            if (set == null || set.stored().isEmpty()) {
                feedback.send(player, GuardMessageKey.HERE_NONE);
                return;
            }
            feedback.send(
                    player,
                    GuardMessageKey.HERE_HEADER,
                    Map.of("count", String.valueOf(set.stored().size())));
            for (Region region : set.stored()) {
                feedback.send(
                        player,
                        GuardMessageKey.HERE_ENTRY,
                        Map.of(
                                "region",
                                region.id().value(),
                                "priority",
                                String.valueOf(region.priority()),
                                "role",
                                region.roleOf(player.getUniqueId())
                                        .map(RoleName::value)
                                        .orElse("-")));
            }
        });
    }

    private void near(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            Location at = player.getLocation();
            if (at == null) {
                return;
            }
            int radius = args.length > 0 ? Math.max(1, Math.min(512, parseInt(args[0], 32))) : 32;
            List<Region> near =
                    service.near(player.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), radius);
            if (near.isEmpty()) {
                feedback.send(player, GuardMessageKey.NEAR_NONE, Map.of("radius", String.valueOf(radius)));
                return;
            }
            feedback.send(
                    player,
                    GuardMessageKey.NEAR_HEADER,
                    Map.of("count", String.valueOf(near.size()), "radius", String.valueOf(radius)));
            for (Region region : near) {
                Bounds b = region.shape().bounds();
                feedback.send(
                        player,
                        GuardMessageKey.NEAR_ENTRY,
                        Map.of(
                                "region",
                                region.id().value(),
                                "priority",
                                String.valueOf(region.priority()),
                                "center",
                                ((b.minX() + b.maxX()) / 2) + " " + ((b.minY() + b.maxY()) / 2) + " "
                                        + ((b.minZ() + b.maxZ()) / 2)));
            }
            List<Shape> shapes = near.stream().map(Region::shape).toList();
            GuardConfig config = service.config();
            onPlayer.accept(
                    player,
                    () -> display.show(
                            player,
                            player.getWorld(),
                            shapes,
                            config.displayMode(),
                            config.displayRange(),
                            Color.fromRGB(0xFF, 0xAA, 0x00)));
            scheduleHide(player, shapes, config);
        });
    }

    private void why(CommandSender sender, String[] args) {
        withPlayer(sender, player -> {
            if (args.length < 1) {
                feedback.usage(player, "guard why", "<action>", "Explain a decision where you stand");
                return;
            }
            Location at = player.getLocation();
            if (at == null) {
                return;
            }
            explain(
                    player,
                    new BukkitSubject(player),
                    player.getName(),
                    args[0],
                    player.getWorld().getName(),
                    at.getBlockX(),
                    at.getBlockY(),
                    at.getBlockZ());
        });
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            feedback.usage(sender, "guard test", "<player> <action> [region]", "Check a decision for anyone");
            return;
        }
        OfflinePlayer target = offline(args[0]);
        if (target == null) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_PLAYER, Map.of("player", args[0]));
            return;
        }
        String world;
        int x;
        int y;
        int z;
        if (args.length >= 3) {
            Optional<Region> region = key(sender, args, 2).flatMap(service::region);
            if (region.isEmpty()) {
                return;
            }
            Bounds b = region.get().shape().bounds();
            world = region.get().world();
            x = clampMid(b.minX(), b.maxX());
            y = clampMid(b.minY(), b.maxY());
            z = clampMid(b.minZ(), b.maxZ());
            if (!region.get().shape().contains(x, y, z)) {
                int[] inside = anyInside(region.get().shape());
                x = inside[0];
                y = inside[1];
                z = inside[2];
            }
        } else if (sender instanceof Player player && player.getLocation() != null) {
            Location at = player.getLocation();
            world = player.getWorld().getName();
            x = at.getBlockX();
            y = at.getBlockY();
            z = at.getBlockZ();
        } else {
            feedback.send(sender, GuardMessageKey.ERROR_PLAYER_ONLY);
            return;
        }
        Player online = target.getPlayer();
        GuardSubject subject = online != null ? new BukkitSubject(online) : offlineSubject(target.getUniqueId());
        explain(sender, subject, target.getName() == null ? args[0] : target.getName(), args[1], world, x, y, z);
    }

    private void explain(
            CommandSender sender,
            GuardSubject subject,
            String name,
            String rawAction,
            String world,
            int x,
            int y,
            int z) {
        ActionId action;
        try {
            action = ActionId.of(rawAction);
        } catch (IllegalArgumentException e) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_ACTION, Map.of("action", rawAction));
            return;
        }
        if (!service.tree().contains(action)) {
            feedback.send(sender, GuardMessageKey.ERROR_UNKNOWN_ACTION, Map.of("action", rawAction));
            return;
        }
        Explanation explanation = service.snapshot().explain(world, x, y, z, action, subject);
        if (explanation.lockedWorld()) {
            feedback.send(sender, GuardMessageKey.WHY_LOCKED_WORLD);
            return;
        }
        feedback.send(
                sender,
                GuardMessageKey.WHY_HEADER,
                Map.of("player", name, "action", action.value(), "where", world + " " + x + " " + y + " " + z));
        if (explanation.steps().isEmpty()) {
            feedback.send(sender, GuardMessageKey.WHY_NOTHING);
        }
        int n = 1;
        for (Explanation.Step step : explanation.steps()) {
            GuardMessageKey stepKey =
                    switch (step.origin()) {
                        case "world-defaults" -> GuardMessageKey.WHY_STEP_DEFAULTS;
                        case "inherited" -> GuardMessageKey.WHY_STEP_INHERITED;
                        case "none" -> GuardMessageKey.WHY_STEP_NONE;
                        default -> GuardMessageKey.WHY_STEP;
                    };
            feedback.send(
                    sender,
                    stepKey,
                    Map.of(
                            "n",
                            String.valueOf(n++),
                            "region",
                            step.region().id().value(),
                            "priority",
                            String.valueOf(step.priority()),
                            "role",
                            step.role() == null ? "-" : step.role().value(),
                            "node",
                            step.node() == null ? "-" : step.node().value(),
                            "rule",
                            step.rule() == null
                                    ? "-"
                                    : StyledText.escape(step.rule().toString()),
                            "result",
                            step.result().name().toLowerCase(Locale.ROOT),
                            "mark",
                            step.decisive() ? "*" : ""));
        }
        feedback.send(
                sender,
                GuardMessageKey.WHY_RESULT,
                Map.of("result", explanation.verdict().allowed() ? "allow" : "deny"));
    }

    private void inspect(CommandSender sender) {
        withPlayer(
                sender,
                player -> feedback.send(
                        player,
                        guard.toggleInspect(player) ? GuardMessageKey.INSPECT_ON : GuardMessageKey.INSPECT_OFF));
    }

    private void history(CommandSender sender, String[] args) {
        Optional<RegionKey> key = args.length > 0 && !isNumber(args[0]) ? rawKey(sender, args, 0) : Optional.empty();
        if (args.length > 0 && !isNumber(args[0]) && key.isEmpty()) {
            return;
        }
        int page = args.length > 0 && isNumber(args[args.length - 1]) ? parseInt(args[args.length - 1], 1) : 1;
        int per = 10;
        var ignored = service.history(key.orElse(null), (page - 1) * per, per).whenComplete((entries, error) -> {
            if (error != null) {
                feedback.send(sender, GuardMessageKey.ERROR_STORE_FAILED);
                return;
            }
            if (entries.isEmpty()) {
                feedback.send(sender, GuardMessageKey.HISTORY_EMPTY);
                return;
            }
            feedback.send(
                    sender,
                    GuardMessageKey.HISTORY_HEADER,
                    Map.of(
                            "page",
                            String.valueOf(page),
                            "region",
                            key.map(k -> k.id().value()).orElse("*")));
            for (HistoryEntry entry : entries) {
                feedback.send(
                        sender,
                        GuardMessageKey.HISTORY_ENTRY,
                        Map.of(
                                "id",
                                String.valueOf(entry.id()),
                                "time",
                                TIME.format(entry.at()),
                                "actor",
                                entry.actor(),
                                "change",
                                entry.change(),
                                "region",
                                entry.key().id().value(),
                                "world",
                                entry.key().world()));
            }
        });
    }

    private void undo(CommandSender sender, String[] args) {
        if (args.length < 1 || !isNumber(args[0])) {
            feedback.usage(sender, "guard undo", "<history id>", "Undo one change");
            return;
        }
        long id = Long.parseLong(args[0]);
        answer(sender, service.undo(id, ctx(sender)), GuardMessageKey.UNDONE, Map.of("id", args[0]));
    }

    private void log(CommandSender sender, String[] args) {
        if (!service.config().denialLog().enabled()) {
            feedback.send(sender, GuardMessageKey.LOG_DISABLED);
            return;
        }
        String player = args.length > 0 && !isNumber(args[0]) && !args[0].startsWith("region:") ? args[0] : null;
        String region = args.length > 0 && args[0].startsWith("region:") ? args[0].substring(7) : null;
        int page = args.length > 0 && isNumber(args[args.length - 1]) ? parseInt(args[args.length - 1], 1) : 1;
        var ignored = guard.denials(player, region, (page - 1) * 10, 10).whenComplete((rows, error) -> {
            if (error != null) {
                feedback.send(sender, GuardMessageKey.ERROR_STORE_FAILED);
                return;
            }
            if (rows.isEmpty()) {
                feedback.send(sender, GuardMessageKey.LOG_EMPTY);
                return;
            }
            feedback.send(sender, GuardMessageKey.LOG_HEADER, Map.of("page", String.valueOf(page)));
            rows.forEach(d -> feedback.send(
                    sender,
                    GuardMessageKey.LOG_ENTRY,
                    Map.of(
                            "time",
                            TIME.format(d.at()),
                            "player",
                            d.player(),
                            "action",
                            d.action(),
                            "region",
                            d.region(),
                            "where",
                            d.world() + " " + d.x() + " " + d.y() + " " + d.z())));
        });
    }

    private void status(CommandSender sender) {
        GuardStats stats = guard.stats();
        int regions = 0;
        int columns = 0;
        int sets = 0;
        for (WorldIndex index : service.snapshot().worlds().values()) {
            regions += index.size();
            columns += index.columnCount();
            sets += index.setCount();
        }
        long checks =
                stats.families().stream().mapToLong(GuardStats.Family::checks).sum();
        feedback.send(sender, GuardMessageKey.STATUS_HEADER);
        feedback.send(
                sender,
                GuardMessageKey.STATUS_LINE,
                Map.of(
                        "regions", String.valueOf(regions),
                        "worlds", String.valueOf(service.snapshot().worlds().size()),
                        "columns", String.valueOf(columns),
                        "sets", String.valueOf(sets),
                        "checks", String.valueOf(checks),
                        "rate", String.format(Locale.ROOT, "%.1f", checks / stats.uptimeSeconds()),
                        "generation", String.valueOf(service.snapshot().generation()),
                        "locked", String.valueOf(service.loadFailed())));
        stats.families().stream()
                .filter(f -> f.checks() > 0)
                .limit(5)
                .forEach(f -> feedback.send(
                        sender,
                        GuardMessageKey.STATUS_FAMILY,
                        Map.of(
                                "family",
                                f.name(),
                                "checks",
                                String.valueOf(f.checks()),
                                "denials",
                                String.valueOf(f.denials()),
                                "average",
                                String.valueOf(f.averageNanos()))));
    }

    private void actions(CommandSender sender, String[] args) {
        String prefix = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        List<String> ids = service.tree().ids().stream()
                .map(ActionId::value)
                .filter(id -> prefix.isEmpty() ? !id.contains(".") : id.startsWith(prefix))
                .toList();
        feedback.send(sender, GuardMessageKey.ACTIONS_HEADER, Map.of("count", String.valueOf(ids.size())));
        feedback.send(sender, GuardMessageKey.ACTIONS_ENTRY, Map.of("actions", String.join(", ", ids)));
    }

    private void importCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("worldguard")) {
            feedback.usage(
                    sender, "guard import", "worldguard <dry-run|run|undo> [yaml|sql]", "Import WorldGuard regions");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        String source = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "yaml";
        var ignored = imports.run(mode, source, ctx(sender)).whenComplete((report, error) -> {
            if (error != null) {
                feedback.send(
                        sender, GuardMessageKey.IMPORT_FAILED, Map.of("error", String.valueOf(error.getMessage())));
            } else {
                Map<String, String> values = Map.of(
                        "regions", String.valueOf(report.regions()),
                        "rules", String.valueOf(report.rules()),
                        "behaviours", String.valueOf(report.behaviours()),
                        "notes", String.valueOf(report.notes()),
                        "skipped", String.valueOf(report.skipped()));
                feedback.send(
                        sender,
                        switch (report.kind()) {
                            case NOTHING_TO_UNDO -> GuardMessageKey.IMPORT_NOTHING;
                            case UNDONE -> GuardMessageKey.IMPORT_UNDONE;
                            case DRY_RUN -> GuardMessageKey.IMPORT_REPORT;
                            case IMPORTED -> GuardMessageKey.IMPORT_DONE;
                        },
                        values);
            }
        });
    }

    private void help(CommandSender sender) {
        feedback.send(sender, GuardMessageKey.HELP_HEADER);
        Map<String, String> nodes = new java.util.HashMap<>();
        for (Sub sub : subs()) {
            nodes.put(sub.name(), sub.permission());
        }
        for (String line : List.of(
                "menu",
                "here",
                "why <action>",
                "member <region> <player> <member|guest|clear>",
                "define <id> [shape]",
                "rule <region> <action> <target> <allow|deny|clear>",
                "role <region> <player> <role|clear>",
                "set <region> <behaviour> <value|clear>",
                "template <region> <template|clear>",
                "info [region]",
                "list [world]",
                "near [radius]",
                "test <player> <action> [region]",
                "show [region]",
                "history [region]",
                "undo <id>",
                "bypass",
                "inspect",
                "status",
                "actions [prefix]",
                "import worldguard <dry-run|run|undo>")) {
            String node = nodes.get(line.split(" ", 2)[0]);
            if (node != null && sender.hasPermission(node)) {
                feedback.send(sender, GuardMessageKey.HELP_LINE, Map.of("usage", StyledText.escape("/guard " + line)));
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** True when {@code player} owns or manages a region where they stand, so a bare /guard opens their window. */
    private boolean managesHere(Player player) {
        Location at = player.getLocation();
        RegionSet set = at == null ? null : guard.at(at);
        if (set == null) {
            return false;
        }
        for (Region region : set.stored()) {
            if (region.roleOf(player.getUniqueId()).map(RoleName::manages).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private record ShapeIn(String world, Shape shape) {}

    private Optional<ShapeIn> shape(CommandSender sender, String[] args) {
        String kind = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "cuboid";
        if (!(sender instanceof Player player)) {
            feedback.send(sender, GuardMessageKey.ERROR_PLAYER_ONLY);
            return Optional.empty();
        }
        World world = player.getWorld();
        Location at = player.getLocation();
        if (at == null) {
            return Optional.empty();
        }
        Optional<ShapeIn> made =
                switch (kind) {
                    case "cuboid", "kup" -> selections.cuboid(player).map(s -> new ShapeIn(s.world(), s.shape()));
                    case "polygon", "poligon" ->
                        selections
                                .polygon(
                                        player,
                                        args.length > 1
                                                ? parseInt(args[1], world.getMinHeight())
                                                : world.getMinHeight(),
                                        args.length > 2
                                                ? parseInt(args[2], world.getMaxHeight() - 1)
                                                : world.getMaxHeight() - 1)
                                .map(s -> new ShapeIn(s.world(), s.shape()));
                    case "global", "dunya" -> Optional.of(new ShapeIn(world.getName(), new Shape.Global()));
                    case "cylinder", "silindir" -> {
                        int r = args.length > 1 ? parseInt(args[1], -1) : -1;
                        if (r < 0) {
                            yield Optional.empty();
                        }
                        int height = args.length > 2 ? parseInt(args[2], -1) : -1;
                        int low = height < 0 ? world.getMinHeight() : at.getBlockY();
                        int high = height < 0 ? world.getMaxHeight() - 1 : at.getBlockY() + height - 1;
                        yield Optional.of(new ShapeIn(
                                world.getName(), new Shape.Cylinder(at.getBlockX(), at.getBlockZ(), r, r, low, high)));
                    }
                    case "sphere", "kure" -> {
                        int r = args.length > 1 ? parseInt(args[1], -1) : -1;
                        yield r < 0
                                ? Optional.empty()
                                : Optional.of(new ShapeIn(
                                        world.getName(),
                                        new Shape.Sphere(at.getBlockX(), at.getBlockY(), at.getBlockZ(), r)));
                    }
                    case "chunks", "chunk" -> {
                        int r = args.length > 1 ? Math.max(0, Math.min(32, parseInt(args[1], 0))) : 0;
                        Set<Long> keys = new HashSet<>();
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                keys.add(Shape.chunkKey((at.getBlockX() >> 4) + dx, (at.getBlockZ() >> 4) + dz));
                            }
                        }
                        yield Optional.of(new ShapeIn(
                                world.getName(),
                                Shape.ChunkSet.of(keys, world.getMinHeight(), world.getMaxHeight() - 1)));
                    }
                    default -> Optional.empty();
                };
        if (made.isEmpty()) {
            feedback.send(
                    sender,
                    kind.equals("cuboid") || kind.equals("polygon")
                            ? GuardMessageKey.ERROR_NO_SELECTION
                            : GuardMessageKey.ERROR_BAD_SHAPE,
                    Map.of("shape", kind));
        }
        return made;
    }

    /** A region key from {@code [world:]id} at {@code index}, that must exist. */
    private Optional<RegionKey> key(CommandSender sender, String[] args, int index) {
        Optional<RegionKey> key = rawKey(sender, args, index);
        if (key.isPresent() && service.region(key.get()).isEmpty()) {
            feedback.send(
                    sender,
                    GuardMessageKey.ERROR_NO_SUCH_REGION,
                    Map.of("region", key.get().id().value(), "world", key.get().world()));
            return Optional.empty();
        }
        return key;
    }

    private Optional<RegionKey> rawKey(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            feedback.send(sender, GuardMessageKey.ERROR_BAD_ID);
            return Optional.empty();
        }
        String raw = args[index];
        String world;
        String id;
        int colon = raw.indexOf(':');
        if (colon > 0) {
            world = raw.substring(0, colon);
            id = raw.substring(colon + 1);
        } else {
            world = worldOf(sender);
            id = raw;
        }
        if (world == null || !RegionId.valid(id)) {
            feedback.send(sender, world == null ? GuardMessageKey.ERROR_PLAYER_ONLY : GuardMessageKey.ERROR_BAD_ID);
            return Optional.empty();
        }
        return Optional.of(new RegionKey(world, RegionId.of(id)));
    }

    private Optional<Region> topHere(CommandSender sender) {
        if (!(sender instanceof Player player) || player.getLocation() == null) {
            return Optional.empty();
        }
        RegionSet set = guard.at(player.getLocation());
        return set == null ? Optional.empty() : set.top().map(c -> c.region());
    }

    private void edit(
            CommandSender sender,
            RegionKey key,
            String change,
            UnaryOperator<Region> edit,
            MessageKey success,
            Map<String, String> values) {
        answer(sender, service.edit(key, change, edit, ctx(sender)), success, values);
    }

    private void answer(
            CommandSender sender,
            CompletableFuture<Result<Region, GuardError>> future,
            MessageKey success,
            Map<String, String> values) {
        answer(sender, future, success, values, () -> {});
    }

    private void answer(
            CommandSender sender,
            CompletableFuture<Result<Region, GuardError>> future,
            MessageKey success,
            Map<String, String> values,
            Runnable after) {
        var ignored = future.whenComplete((result, error) -> {
            if (error != null) {
                feedback.send(sender, GuardMessageKey.ERROR_STORE_FAILED);
                return;
            }
            if (result.isOk()) {
                feedback.send(sender, success, values);
                if (sender instanceof Player player) {
                    onPlayer.accept(player, () -> {
                        tracks.refresh(player);
                        after.run();
                    });
                }
            } else {
                feedback.send(sender, errorKey(result.errorOrThrow()), values);
                if (sender instanceof Player player) {
                    onPlayer.accept(player, after);
                }
            }
        });
    }

    /**
     * Answer an edit made from a menu exactly as the command answers its own: the success or error line, the
     * behaviour refresh, then {@code after} (usually reopening the menu) on the player's thread.
     */
    public void answerMenu(
            Player player,
            CompletableFuture<Result<Region, GuardError>> future,
            MessageKey success,
            Map<String, String> values,
            Runnable after) {
        answer(player, future, success, values, after);
    }

    /** Who is editing, as the service records it, for a menu edit. */
    public EditContext editContext(Player player) {
        return ctx(player);
    }

    private static GuardMessageKey errorKey(GuardError error) {
        return switch (error) {
            case UNKNOWN_WORLD -> GuardMessageKey.ERROR_UNKNOWN_WORLD;
            case NO_SUCH_REGION -> GuardMessageKey.ERROR_NO_SUCH_REGION;
            case ALREADY_EXISTS -> GuardMessageKey.ERROR_ALREADY_EXISTS;
            case INVALID_PARENT -> GuardMessageKey.ERROR_INVALID_PARENT;
            case REGION_LOCKED -> GuardMessageKey.ERROR_REGION_LOCKED;
            case WORLD_LOCKED -> GuardMessageKey.ERROR_WORLD_LOCKED;
            case UNKNOWN_TEMPLATE -> GuardMessageKey.ERROR_UNKNOWN_TEMPLATE;
            case UNKNOWN_ACTION -> GuardMessageKey.ERROR_UNKNOWN_ACTION;
            case NOTHING_TO_UNDO -> GuardMessageKey.ERROR_NOTHING_TO_UNDO;
            case NOT_STORED, STORE_FAILED -> GuardMessageKey.ERROR_STORE_FAILED;
        };
    }

    private EditContext ctx(CommandSender sender) {
        return new EditContext(
                sender instanceof Player p ? p.getName() : "console",
                !(sender instanceof Player) || sender.hasPermission(GuardRuntime.LOCK_PERMISSION));
    }

    private void withPlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            feedback.send(sender, GuardMessageKey.ERROR_PLAYER_ONLY);
        }
    }

    private void line(CommandSender sender, String label, String value) {
        feedback.send(
                sender,
                GuardMessageKey.INFO_LINE,
                Map.of("label", StyledText.escape(label), "value", StyledText.escape(value)));
    }

    private void line(CommandSender sender, GuardMessageKey key, String value) {
        feedback.send(sender, key, Map.of("value", StyledText.escape(value)));
    }

    private List<String> regionIds(CommandSender sender) {
        String world = worldOf(sender);
        if (world == null) {
            return service.all().stream().map(r -> r.world() + ":" + r.id()).toList();
        }
        return service.regions(world).stream().map(r -> r.id().value()).toList();
    }

    private List<String> scopeNames() {
        List<String> names = new ArrayList<>();
        names.add("server");
        names.addAll(service.config().sharedSets());
        return names;
    }

    private static List<String> worldNames() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    private static @Nullable String worldOf(CommandSender sender) {
        return sender instanceof Player player ? player.getWorld().getName() : null;
    }

    private static Map<String, String> xyz(Location at) {
        return Map.of(
                "x",
                String.valueOf(at.getBlockX()),
                "y",
                String.valueOf(at.getBlockY()),
                "z",
                String.valueOf(at.getBlockZ()));
    }

    private static String facing(Player player) {
        Location at = player.getLocation();
        if (at == null) {
            return "all";
        }
        if (at.getPitch() > 60) {
            return "down";
        }
        if (at.getPitch() < -60) {
            return "up";
        }
        float yaw = (at.getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) {
            return "west";
        }
        if (yaw >= 135 && yaw < 225) {
            return "north";
        }
        if (yaw >= 225 && yaw < 315) {
            return "east";
        }
        return "south";
    }

    private static @Nullable OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    private static String name(UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() == null ? id.toString().substring(0, 8) : player.getName();
    }

    private static GuardSubject offlineSubject(UUID id) {
        return new GuardSubject() {
            @Override
            public UUID id() {
                return id;
            }

            @Override
            public boolean has(String node) {
                return false;
            }

            @Override
            public boolean meets(String expression) {
                return false;
            }
        };
    }

    private static int clampMid(int a, int b) {
        return (int) (((long) a + b) / 2);
    }

    private static int[] anyInside(Shape shape) {
        Bounds b = shape.bounds();
        for (int x = b.minX(); x <= b.maxX(); x += Math.max(1, (b.maxX() - b.minX()) / 16)) {
            for (int z = b.minZ(); z <= b.maxZ(); z += Math.max(1, (b.maxZ() - b.minZ()) / 16)) {
                int y = clampMid(b.minY(), b.maxY());
                if (shape.contains(x, y, z)) {
                    return new int[] {x, y, z};
                }
            }
        }
        return new int[] {b.minX(), b.minY(), b.minZ()};
    }

    private static boolean isNumber(String raw) {
        return !raw.isEmpty() && raw.chars().allMatch(Character::isDigit);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private org.bukkit.plugin.Plugin guardPlugin() {
        return guard.plugin();
    }
}

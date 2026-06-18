package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;

/** The {@code /worlds} command: create/import/load/unload/unregister/delete/list/info. */
@NullMarked
public final class WorldCommand extends WorldCommandSupport implements CommandRegistration {

    private static final String USE = "uxmessentials.world.use";
    private static final String CREATE = "uxmessentials.world.create";
    private static final String IMPORT = "uxmessentials.world.import";
    private static final String LOAD = "uxmessentials.world.load";
    private static final String UNLOAD = "uxmessentials.world.unload";
    private static final String UNREGISTER = "uxmessentials.world.unregister";
    private static final String DELETE = "uxmessentials.world.delete";
    private static final String LIST = "uxmessentials.world.list";
    private static final String INFO = "uxmessentials.world.info";

    public WorldCommand(WorldsServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("worlds")
                .requires(src -> src.getSender().hasPermission(USE))
                .then(Commands.literal("list").requires(p(LIST)).executes(this::runList))
                .then(Commands.literal("info").requires(p(INFO)).then(nameArg().executes(this::runInfo)))
                .then(Commands.literal("create")
                        .requires(p(CREATE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::runCreate)
                                .then(envArg().executes(this::runCreate)
                                        .then(typeArg().executes(this::runCreate)))))
                .then(Commands.literal("import")
                        .requires(p(IMPORT))
                        .then(folderArg()
                                .then(envArg().executes(this::runImport)
                                        .then(Commands.argument("generator", StringArgumentType.greedyString())
                                                .executes(this::runImport)))))
                .then(Commands.literal("load").requires(p(LOAD)).then(nameArg().executes(this::runLoad)))
                .then(Commands.literal("unload")
                        .requires(p(UNLOAD))
                        .then(nameArg().executes(this::runUnload)))
                .then(Commands.literal("unregister")
                        .requires(p(UNREGISTER))
                        .then(nameArg().executes(this::runUnregister)))
                .then(Commands.literal("delete")
                        .requires(p(DELETE))
                        .then(nameArg().executes(this::runDelete)))
                .build();
    }

    @Override
    public String description() {
        return "Manage worlds: create, import, load, unload, unregister, delete, list, info.";
    }

    private static Predicate<CommandSourceStack> p(String node) {
        return src -> src.getSender().hasPermission(node);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
        return Commands.argument("name", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() -> services.repository().all().stream()
                        .map(w -> w.name().value())
                        .toList()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> folderArg() {
        services.refreshImportableFolders(); // fire-and-forget async rescan
        return Commands.argument("folder", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(services::importableFolders));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> envArg() {
        return Commands.argument("environment", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() ->
                        Arrays.stream(WorldEnvironment.values()).map(Enum::name).toList()));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> typeArg() {
        return Commands.argument("type", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() ->
                        Arrays.stream(WorldGenType.values()).map(Enum::name).toList()));
    }

    private int runCreate(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        WorldSpec spec = new WorldSpec(
                arg(ctx, "environment", WorldEnvironment.class, WorldEnvironment.NORMAL),
                arg(ctx, "type", WorldGenType.class, WorldGenType.NORMAL),
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
        PlayerRef who = ref(sender);
        feedback.send(sender, WorldsMessageKey.WORLD_CREATING, Map.of("world", name.value()));
        onGlobal(() -> services.createWorld().create(who, name, spec, true));
        return Command.SINGLE_SUCCESS;
    }

    private int runImport(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("folder", String.class));
        if (name == null) {
            return 0;
        }
        WorldEnvironment env = arg(ctx, "environment", WorldEnvironment.class, WorldEnvironment.NORMAL);
        Optional<GeneratorRef> gen = optionalString(ctx, "generator").map(GeneratorRef::of);
        PlayerRef who = ref(sender);
        feedback.send(sender, WorldsMessageKey.WORLD_IMPORTING, Map.of("world", name.value()));
        onGlobal(() -> services.importWorld().importWorld(who, name, env, gen));
        return Command.SINGLE_SUCCESS;
    }

    private int runLoad(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.loadWorld().load(who, name));
    }

    private int runUnload(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.unloadWorld().unload(who, name, true));
    }

    private int runUnregister(CommandContext<CommandSourceStack> ctx) {
        return mutate(ctx, (who, name) -> services.unregisterWorld().unregister(who, name));
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        services.deleteWorld().request(ref(sender), name); // inline: validation + staging, no I/O
        return Command.SINGLE_SUCCESS;
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var entries = services.listWorlds().all();
        if (entries.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_LIST_EMPTY, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(sender, WorldsMessageKey.WORLD_LIST_HEADER, Map.of("count", Integer.toString(entries.size())));
        for (ListWorlds.WorldListEntry entry : entries) {
            feedback.send(
                    sender,
                    WorldsMessageKey.WORLD_LIST_ENTRY,
                    Map.of(
                            "world", entry.name().value(),
                            "loaded", Boolean.toString(entry.loaded()),
                            "environment", entry.environment().name()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        Optional<ManagedWorld> found = services.worldInfo().find(name);
        if (found.isEmpty()) {
            feedback.send(sender, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", name.value()));
            return 0;
        }
        ManagedWorld w = found.orElseThrow();
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_HEADER,
                Map.of("world", w.name().value()));
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_ENVIRONMENT,
                Map.of("value", w.spec().environment().name()));
        feedback.send(
                sender,
                WorldsMessageKey.WORLD_INFO_TYPE,
                Map.of("value", w.spec().worldType().name()));
        feedback.send(sender, WorldsMessageKey.WORLD_INFO_AUTOLOAD, Map.of("value", Boolean.toString(w.autoLoad())));
        return Command.SINGLE_SUCCESS;
    }

    private int mutate(CommandContext<CommandSourceStack> ctx, Mutation mutation) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WorldName name = parseName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        onGlobal(() -> mutation.run(who, name));
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface Mutation {
        void run(PlayerRef who, WorldName name);
    }

    private static <E extends Enum<E>> E arg(
            CommandContext<CommandSourceStack> ctx, String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, ctx.getArgument(key, String.class).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException missingOrInvalid) {
            return fallback;
        }
    }

    private static Optional<String> optionalString(CommandContext<CommandSourceStack> ctx, String key) {
        try {
            return Optional.of(ctx.getArgument(key, String.class));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }
}

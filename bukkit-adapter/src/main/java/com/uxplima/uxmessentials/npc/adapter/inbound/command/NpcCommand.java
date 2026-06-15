package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /npc <create|delete|list|movehere|skin|command|lookatplayer>}: the single operator command for
 * fake-player NPCs. Each subcommand maps its arguments to one use-case call; {@code create} and {@code movehere}
 * read the operator's current position, {@code create} defaults the skin to the operator's own skin (lifted from
 * their Bukkit profile, or skinless when unavailable), {@code skin} accepts {@code texture:<value>[:<sig>]} or
 * {@code player:<online-name>}, and {@code lookatplayer} toggles whether the NPC turns to face nearby players.
 * The base {@code uxmessentials.npc.admin} node guards the whole command.
 */
@NullMarked
public final class NpcCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.npc.admin";
    private static final String PLAYER_PREFIX = "player:";
    private static final String TEXTURE_PREFIX = "texture:";

    private final NpcServices services;
    private final CommandFeedback feedback;

    public NpcCommand(NpcServices services, Messages messages) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("npc")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(name("create", this::create))
                .then(name("delete", this::delete))
                .then(Commands.literal("list").executes(this::list))
                .then(name("movehere", this::move))
                .then(greedy("skin", this::skin))
                .then(greedy("command", this::command))
                .then(lookAtPlayerNode())
                .build();
    }

    @Override
    public String description() {
        return "Create and manage fake-player NPCs.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> name(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word()).executes(action));
    }

    private LiteralArgumentBuilder<CommandSourceStack> greedy(String literal, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(action)));
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcSkin skin = BukkitNpcSkins.of(sender).orElse(null);
        services.create().create(ref(sender), nameArg(ctx), position(sender), skin);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.delete().delete(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.list().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.move().move(ref(sender), nameArg(ctx), position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int skin(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcSkin skin = resolveSkin(sender, value(ctx));
        if (skin == null) {
            return 0; // the unresolvable-skin feedback was already sent
        }
        services.skin().setSkin(ref(sender), nameArg(ctx), skin);
        return Command.SINGLE_SUCCESS;
    }

    private int command(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.command().setCommand(ref(sender), nameArg(ctx), value(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> lookAtPlayerNode() {
        return Commands.literal("lookatplayer")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(this::lookAtPlayer)));
    }

    private int lookAtPlayer(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.look().setLookAtPlayer(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Parse the skin spec: {@code player:<online-name>} copies that online player's skin (feedback when they are
     * offline or carry no skin), {@code texture:<value>[:<signature>]} (or a bare value) uses the raw strings.
     * Returns {@code null} after sending feedback when the spec cannot be resolved.
     */
    private @Nullable NpcSkin resolveSkin(Player sender, String spec) {
        if (spec.regionMatches(true, 0, PLAYER_PREFIX, 0, PLAYER_PREFIX.length())) {
            return skinFromPlayer(sender, spec.substring(PLAYER_PREFIX.length()).strip());
        }
        String raw = spec.regionMatches(true, 0, TEXTURE_PREFIX, 0, TEXTURE_PREFIX.length())
                ? spec.substring(TEXTURE_PREFIX.length())
                : spec;
        return skinFromTexture(sender, raw.strip());
    }

    private @Nullable NpcSkin skinFromPlayer(Player sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_PLAYER_OFFLINE, Map.of("player", targetName));
            return null;
        }
        Optional<NpcSkin> skin = BukkitNpcSkins.of(target);
        if (skin.isEmpty()) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_UNAVAILABLE, Map.of("player", targetName));
        }
        return skin.orElse(null);
    }

    private @Nullable NpcSkin skinFromTexture(Player sender, String raw) {
        int separator = raw.indexOf(':');
        String texture = separator < 0 ? raw : raw.substring(0, separator);
        String signature = separator < 0 ? null : raw.substring(separator + 1);
        if (texture.isBlank()) {
            feedback.send(sender, NpcMessageKey.NPC_SKIN_UNAVAILABLE, Map.of("player", raw));
            return null;
        }
        return new NpcSkin(texture, signature == null || signature.isBlank() ? null : signature);
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, NpcMessageKey.NPC_PLAYERS_ONLY, Map.of());
        return null;
    }

    private static NpcName nameArg(CommandContext<CommandSourceStack> ctx) {
        return NpcName.of(ctx.getArgument("name", String.class));
    }

    private static String value(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("value", String.class);
    }

    private static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    private static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }
}

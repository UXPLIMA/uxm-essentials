package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.ClickTrigger;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code /npc action <add|list|remove|clear>} subcommands: edit the ordered click-action chain. {@code add}
 * takes a trigger word (left/right/any), a type word, and the rest of the line as the value — the type word is
 * one of the effects ({@code console}, {@code player}, {@code message}, {@code actionbar}, {@code title},
 * {@code sound}, {@code connect}, {@code give}), the sequencer's {@code delay} or {@code random}, or a gate
 * ({@code chance}, {@code permission}, {@code condition}, {@code cost}); the cheap value shapes are validated at
 * add time, while the gates and text effects accept any value. {@code give hand} is a special value: instead of a
 * material it captures the sender's currently-held item (with its NBT) as a serialized token. Collected here so
 * the root {@code /npc} command stays focused while keeping the single literal intact.
 */
@NullMarked
final class NpcActionCommands extends NpcCommandSupport {

    /** The accepted {@code action add} trigger words, also the tab suggestions. */
    private static final List<String> TRIGGER_WORDS = List.of("left", "right", "any");
    /** The accepted {@code action add} type words, also the tab suggestions. */
    private static final List<String> TYPE_WORDS = List.of(
            "console",
            "player",
            "message",
            "actionbar",
            "title",
            "sound",
            "connect",
            "delay",
            "random",
            "chance",
            "permission",
            "condition",
            "cost",
            "give");

    /** The {@code give} value word that captures the sender's currently-held item instead of naming a material. */
    private static final String HAND_KEYWORD = "hand";

    NpcActionCommands(NpcServices services, Messages messages) {
        super(services, messages);
    }

    /** The {@code action} subcommand node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("action")
                .then(actionAddNode())
                .then(Commands.literal("list")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::actionList)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(this::actionRemove))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::actionClear)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> actionAddNode() {
        return Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("trigger", StringArgumentType.word())
                                .suggests(this::suggestTriggers)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestTypes)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(this::actionAdd)))));
    }

    private int actionAdd(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        ClickTrigger trigger = parseTrigger(ctx.getArgument("trigger", String.class));
        if (trigger == null) {
            feedback.send(
                    sender,
                    NpcMessageKey.NPC_INVALID_TRIGGER,
                    Map.of("trigger", ctx.getArgument("trigger", String.class)));
            return 0;
        }
        String typeWord = ctx.getArgument("type", String.class);
        NpcActionType type = NpcActionValueCheck.parseType(typeWord).orElse(null);
        if (type == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_ACTION_TYPE, Map.of("type", typeWord));
            return 0;
        }
        String value = resolveValue(sender, type, value(ctx));
        if (value == null) {
            return 0; // the capture failed (empty hand) and its feedback was already sent
        }
        NpcActionValueCheck.Result check = NpcActionValueCheck.check(type, value);
        if (check instanceof NpcActionValueCheck.Result.Invalid invalid) {
            feedback.send(
                    sender,
                    NpcMessageKey.NPC_INVALID_ACTION_VALUE,
                    Map.of("value", value, "type", typeWord.toLowerCase(Locale.ROOT), "hint", invalid.hint()));
            return 0;
        }
        services.addAction().add(ref(sender), nameArg(ctx), new NpcAction(trigger, type, value));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve the value to store: for a {@code give hand} the sender's held item is captured as a serialized
     * token (a single-quantity clone with all its NBT), failing with feedback on an empty hand; every other case
     * stores the raw value as typed. Returns {@code null} when the capture failed, signalling the handler to stop.
     */
    private @Nullable String resolveValue(Player sender, NpcActionType type, String rawValue) {
        if (type != NpcActionType.GIVE || !rawValue.strip().equalsIgnoreCase(HAND_KEYWORD)) {
            return rawValue;
        }
        ItemStack hand = sender.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            feedback.send(sender, NpcMessageKey.NPC_GIVE_EMPTY_HAND, Map.of());
            return null;
        }
        return EquipmentPayloads.serialize(hand);
    }

    private int actionList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listActions().list(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int actionRemove(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.removeAction().remove(ref(sender), nameArg(ctx), ctx.getArgument("index", Integer.class));
        return Command.SINGLE_SUCCESS;
    }

    private int actionClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.clearActions().clear(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestTriggers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, TRIGGER_WORDS);
    }

    private CompletableFuture<Suggestions> suggestTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, TYPE_WORDS);
    }

    /** Map the operator's trigger word (left/right/any) to a {@link ClickTrigger}, or {@code null} when unknown. */
    private static @Nullable ClickTrigger parseTrigger(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "left" -> ClickTrigger.LEFT_CLICK;
            case "right" -> ClickTrigger.RIGHT_CLICK;
            case "any" -> ClickTrigger.ANY;
            default -> null;
        };
    }
}

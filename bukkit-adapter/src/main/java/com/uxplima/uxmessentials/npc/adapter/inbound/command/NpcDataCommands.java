package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.DyeColor;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcTypeData;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /npc data <set|clear|list>} subcommands: edit the per-type metadata an NPC carries
 * (baby/size/charged/villager), validated against {@link NpcTypeData}. A known key with a valid value is stored,
 * a clear drops it, and a list prints the stored data. Collected here so the appearance handler stays focused
 * while keeping the single {@code /npc} literal intact.
 */
@NullMarked
final class NpcDataCommands extends NpcCommandSupport {

    /** The accepted {@code data set}/{@code data clear} keys, also the tab suggestions. */
    private static final List<String> DATA_KEYS = List.of(
            "baby",
            "size",
            "charged",
            "villager_type",
            "villager_profession",
            "villager_level",
            "horse_color",
            "horse_markings",
            "horse_style",
            "llama_variant",
            "sheep_color",
            "wolf_collar",
            "shulker_color",
            "shulker_peek",
            "panda_gene",
            "parrot_variant",
            "axolotl_variant",
            "fox_type",
            "rabbit_type",
            "goat_screaming",
            "allay_dancing",
            "piglin_dancing",
            "camel_dash",
            "cat_variant",
            "frog_variant");
    /** The boolean keys' suggested values, offered as tab completions for {@code baby}/{@code charged}. */
    private static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    /** The cat-variant names, offered as tab completions for {@code cat_variant}. */
    private static final List<String> CAT_VARIANT_VALUES = List.of(
            "tabby",
            "black",
            "red",
            "siamese",
            "british_shorthair",
            "calico",
            "persian",
            "ragdoll",
            "white",
            "jellie",
            "all_black");
    /** The frog-variant names, offered as tab completions for {@code frog_variant}. */
    private static final List<String> FROG_VARIANT_VALUES = List.of("temperate", "warm", "cold");
    /** The horse coat-colour ids (0–6), offered as tab completions for {@code horse_color}. */
    private static final List<String> HORSE_COLOR_VALUES = List.of("0", "1", "2", "3", "4", "5", "6");
    /** The horse marking ids (0–4), offered as tab completions for {@code horse_markings}. */
    private static final List<String> HORSE_MARKINGS_VALUES = List.of("0", "1", "2", "3", "4");
    /** The horse style names (the named alias of the markings), offered as tab completions for {@code horse_style}. */
    private static final List<String> HORSE_STYLE_VALUES = Arrays.stream(Horse.Style.values())
            .map(style -> style.name().toLowerCase(Locale.ROOT))
            .toList();
    /** The llama coat-variant ids (0–3), offered as tab completions for {@code llama_variant}. */
    private static final List<String> LLAMA_VARIANT_VALUES = List.of("0", "1", "2", "3");
    /** The sheep wool-colour names, offered as tab completions for {@code sheep_color} (a 0–15 id is also accepted). */
    private static final List<String> SHEEP_COLOR_VALUES = Arrays.stream(DyeColor.values())
            .map(color -> color.name().toLowerCase(Locale.ROOT))
            .toList();
    /** The shulker peek stops (0 closed … 100 open), offered as tab completions for {@code shulker_peek}. */
    private static final List<String> SHULKER_PEEK_VALUES = List.of("0", "25", "50", "75", "100");
    /** The panda gene ids (0–6), offered as tab completions for {@code panda_gene}. */
    private static final List<String> PANDA_GENE_VALUES = List.of("0", "1", "2", "3", "4", "5", "6");

    NpcDataCommands(
            NpcServices services,
            java.util.function.Supplier<? extends java.util.Collection<String>> npcNames,
            Messages messages) {
        super(services, npcNames, messages);
    }

    /** The {@code data} subcommand node the {@code /npc} literal attaches. */
    LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("data")
                .then(Commands.literal("set")
                        .then(nameArgument()
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestDataKeys)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .suggests(this::suggestDataValues)
                                                .executes(this::dataSet)))))
                .then(Commands.literal("clear")
                        .then(nameArgument()
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(this::suggestDataKeys)
                                        .executes(this::dataClear))))
                .then(Commands.literal("list").then(nameArgument().executes(this::dataList)));
    }

    private int dataSet(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        String value = value(ctx);
        if (!NpcTypeData.isKnownKey(key) || !NpcTypeData.isValidValue(key, value)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DATA, Map.of("key", key, "value", value));
            return 0;
        }
        services.setData().set(ref(sender), nameArg(ctx), key, value);
        return Command.SINGLE_SUCCESS;
    }

    private int dataClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        if (!NpcTypeData.isKnownKey(key)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_DATA, Map.of("key", key, "value", ""));
            return 0;
        }
        services.setData().set(ref(sender), nameArg(ctx), key, null);
        return Command.SINGLE_SUCCESS;
    }

    private int dataList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listData().list(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestDataKeys(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(builder, DATA_KEYS);
    }

    /** Suggest the values for a key whose set is fixed ({@code baby}/{@code charged}, cat/frog); others take a free value. */
    private CompletableFuture<Suggestions> suggestDataValues(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String key = ctx.getArgument("key", String.class).toLowerCase(Locale.ROOT);
        return switch (key) {
            case "baby", "charged", "goat_screaming", "allay_dancing", "piglin_dancing", "camel_dash" -> suggest(
                    builder, BOOLEAN_VALUES);
            case "cat_variant" -> suggest(builder, CAT_VARIANT_VALUES);
            case "frog_variant" -> suggest(builder, FROG_VARIANT_VALUES);
            case "horse_color" -> suggest(builder, HORSE_COLOR_VALUES);
            case "horse_markings" -> suggest(builder, HORSE_MARKINGS_VALUES);
            case "horse_style" -> suggest(builder, HORSE_STYLE_VALUES);
            case "llama_variant" -> suggest(builder, LLAMA_VARIANT_VALUES);
            case "sheep_color", "wolf_collar", "shulker_color" -> suggest(builder, SHEEP_COLOR_VALUES);
            case "shulker_peek" -> suggest(builder, SHULKER_PEEK_VALUES);
            case "panda_gene" -> suggest(builder, PANDA_GENE_VALUES);
            default -> builder.buildFuture();
        };
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /recipe [item]}: show an item's crafting recipe as text — the held item when called with no
 * argument, or a named item resolved against the registry. Read-only, so no audit and no region hop is
 * needed; the first shaped or shapeless crafting recipe is rendered through
 * {@link ItemworldMessageKey#RECIPE_SHAPED} / {@link ItemworldMessageKey#RECIPE_SHAPELESS}, and an item with
 * no crafting recipe replies {@link ItemworldMessageKey#RECIPE_NONE}. An unknown named item replies
 * {@link ItemworldMessageKey#UNKNOWN_ITEM}; an empty hand with no argument replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class RecipeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.recipe.use";

    public RecipeCommand(ItemworldServices services) {
        super(services, "recipe", SubFeatureGroup.ITEM_UTILS, "Show an item's crafting recipe.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "item")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> named) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = named.isPresent() ? namedItem(ctx, named.get()) : heldMaterial(ctx);
        material.ifPresent(found -> report(ctx, found));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Material> namedItem(CommandContext<CommandSourceStack> ctx, String raw) {
        Optional<Material> material = ItemQuery.parse(raw).flatMap(BukkitItemResolver::material);
        if (material.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_ITEM, Map.of("item", raw));
        }
        return material;
    }

    private Optional<Material> heldMaterial(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return Optional.empty();
        }
        return heldItem(ctx, player).map(ItemStack::getType);
    }

    private void report(CommandContext<CommandSourceStack> ctx, Material material) {
        String item = material.getKey().toString();
        Optional<Recipe> crafting = firstCraftingRecipe(material);
        if (crafting.isEmpty()) {
            reply(ctx, ItemworldMessageKey.RECIPE_NONE, Map.of("item", item));
            return;
        }
        Recipe recipe = crafting.get();
        if (recipe instanceof ShapedRecipe shaped) {
            reply(ctx, ItemworldMessageKey.RECIPE_SHAPED, Map.of("item", item, "grid", grid(shaped)));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            reply(
                    ctx,
                    ItemworldMessageKey.RECIPE_SHAPELESS,
                    Map.of("item", item, "ingredients", ingredients(shapeless)));
        }
    }

    /** The first shaped or shapeless crafting recipe yielding {@code material}, ignoring non-crafting ones. */
    private static Optional<Recipe> firstCraftingRecipe(Material material) {
        return Bukkit.getServer().getRecipesFor(new ItemStack(material)).stream()
                .filter(recipe -> recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                .findFirst();
    }

    /** The up-to-three shape rows, each char mapped to a short material name, joined by a slash. */
    private static String grid(ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        return java.util.Arrays.stream(recipe.getShape())
                .map(row -> renderRow(row, choices))
                .collect(Collectors.joining(" / "));
    }

    private static String renderRow(String row, Map<Character, RecipeChoice> choices) {
        return row.chars().mapToObj(c -> choiceName(choices.get((char) c))).collect(Collectors.joining(", "));
    }

    /** The comma-joined ingredient material names of a shapeless recipe, in declaration order. */
    private static String ingredients(ShapelessRecipe recipe) {
        return recipe.getChoiceList().stream().map(RecipeCommand::choiceName).collect(Collectors.joining(", "));
    }

    /** A short material name for a slot's choice; {@code air} for an empty slot or an unrenderable choice. */
    private static String choiceName(@Nullable RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials
                && !materials.getChoices().isEmpty()) {
            return materials.getChoices().get(0).getKey().getKey();
        }
        if (choice instanceof RecipeChoice.ExactChoice exact
                && !exact.getChoices().isEmpty()) {
            return exact.getChoices().get(0).getType().getKey().getKey();
        }
        return Material.AIR.getKey().getKey();
    }
}

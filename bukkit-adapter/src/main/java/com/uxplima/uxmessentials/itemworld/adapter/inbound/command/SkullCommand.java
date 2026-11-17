package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HeadTextures;
import com.uxplima.uxmessentials.shared.application.permission.ItemworldPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /skull [player]}: get a player-head skull. The named player's head, or the sender's own when no name
 * is given. The head wears the skin its owner wears here (see {@link HeadTextures}), resolved off the region thread;
 * the skull is added to the player's inventory on their region thread and reported through
 * {@link ItemworldMessageKey#SKULL_GIVEN}.
 */
@NullMarked
public final class SkullCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = ItemworldPermissions.SKULL_USE;

    public SkullCommand(ItemworldServices services) {
        super(services, "skull", SubFeatureGroup.ITEM_UTILS, "Get a player-head skull.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(Gate.permission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> name) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String owner = name.orElse(player.getName());
        give(ctx, player, owner);
        return Command.SINGLE_SUCCESS;
    }

    private void give(CommandContext<CommandSourceStack> ctx, Player player, String owner) {
        // The name is resolved through the plugin's own index, so an offline-mode or Bedrock account is found under
        // the name it joined with. The stored skin and Mojang are a database read and a web call: both on the async
        // lane. An owner online here is read off their own profile, on their own thread.
        services.kernel().scheduler().async(() -> {
            Optional<PlayerRef> known = services.kernel().playerLookup().findByName(owner);
            if (known.isEmpty()) {
                hand(ctx, player, owner, services.heads().unknown(owner));
                return;
            }
            PlayerRef account = known.get();
            if (services.kernel().playerLookup().isOnline(account.uuid())) {
                services.kernel().scheduler().onEntity(account, () -> handWorn(ctx, player, account));
                return;
            }
            hand(ctx, player, account.name(), services.heads().of(account));
        });
    }

    /** On the owner's own thread: the skin on their live profile, or the stored one if they left meanwhile. */
    private void handWorn(CommandContext<CommandSourceStack> ctx, Player player, PlayerRef account) {
        Player online = player.getServer().getPlayer(account.uuid());
        if (online == null) {
            services.kernel()
                    .scheduler()
                    .async(() ->
                            hand(ctx, player, account.name(), services.heads().of(account)));
            return;
        }
        hand(ctx, player, account.name(), HeadTextures.worn(online.getPlayerProfile()));
    }

    /** Put the head on the player's own thread: drawn from {@code texture}, or the plain head when there is none. */
    private void hand(
            CommandContext<CommandSourceStack> ctx, Player player, String owner, Optional<SkinTexture> texture) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemBuilder head = ItemBuilder.of(Material.PLAYER_HEAD);
            if (texture.isPresent()) {
                head = head.skull(SkullData.ofTexture(texture.get().value()));
            }
            player.getInventory().addItem(head.build());
            reply(ctx, ItemworldMessageKey.SKULL_GIVEN, Map.of("player", owner));
        });
    }
}

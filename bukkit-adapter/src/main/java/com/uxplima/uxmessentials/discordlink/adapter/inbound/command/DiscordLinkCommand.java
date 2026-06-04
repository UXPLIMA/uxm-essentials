package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /discordlink}: issue a one-time link code and tell the player how to redeem it in Discord; the
 * {@code status} subcommand reports the player's current binding instead. Issuing runs the {@code BeginLink} use
 * case and the status path the {@code LinkStatus} use case; the actual redemption happens in Discord via the
 * bridge's {@code /link} slash command, not here. The {@code uxmessentials.discord.link} node guards both paths.
 */
@NullMarked
public final class DiscordLinkCommand extends DiscordLinkCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.discord.link";

    public DiscordLinkCommand(DiscordLinkServices services) {
        super(services);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("discordlink")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("status").executes(this::status))
                .executes(this::begin)
                .build();
    }

    @Override
    public String description() {
        return "Generate a code to link your account to Discord.";
    }

    private int begin(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        Optional<ConfirmedLink> existing = services.linkStatus().status(ref);
        if (existing.isPresent()) {
            services.notifier()
                    .send(
                            ref,
                            DiscordlinkMessageKey.DISCORD_LINK_ALREADY,
                            Map.of("discord", existing.get().discordId().value()));
            return Command.SINGLE_SUCCESS;
        }
        LinkCode code = services.beginLink().begin(ref);
        services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_CODE, Map.of("code", code.value()));
        services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_HOWTO, Map.of("code", code.value()));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        services.linkStatus()
                .status(ref)
                .ifPresentOrElse(
                        link -> services.notifier()
                                .send(
                                        ref,
                                        DiscordlinkMessageKey.DISCORD_LINK_STATUS_LINKED,
                                        Map.of("discord", link.discordId().value())),
                        () -> services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_STATUS_UNLINKED));
        return Command.SINGLE_SUCCESS;
    }
}

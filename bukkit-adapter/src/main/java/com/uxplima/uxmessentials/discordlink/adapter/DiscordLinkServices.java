package com.uxplima.uxmessentials.discordlink.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.application.DiscordLinkNotifier;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed discord-link use cases and notifier the Brigadier commands share, built once per module start
 * by {@code DiscordlinkWiring}. {@link #confirmLink()} is also handed to the {@code ServicesManager} seam impl
 * so the Discord bridge redeems a {@code /link} code through the same use case. The context keeps no other
 * adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param beginLink {@code /discordlink}
 * @param confirmLink the seam target the Discord bridge redeems a code through
 * @param unlink {@code /discordunlink}
 * @param linkStatus {@code /discordlink status}
 * @param notifier resolves and delivers the player-facing replies
 * @param bridge whether the Discord bridge is connected to redeem a code, so issuing paths can decline early
 */
@NullMarked
public record DiscordLinkServices(
        BeginLink beginLink,
        ConfirmLink confirmLink,
        Unlink unlink,
        LinkStatus linkStatus,
        DiscordLinkNotifier notifier,
        DiscordBridge bridge) {

    public DiscordLinkServices {
        Objects.requireNonNull(beginLink, "beginLink");
        Objects.requireNonNull(confirmLink, "confirmLink");
        Objects.requireNonNull(unlink, "unlink");
        Objects.requireNonNull(linkStatus, "linkStatus");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(bridge, "bridge");
    }
}

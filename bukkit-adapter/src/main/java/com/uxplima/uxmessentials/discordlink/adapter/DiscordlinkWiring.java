package com.uxplima.uxmessentials.discordlink.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.discordlink.adapter.inbound.command.DiscordLinkCommands;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.ConfirmLinkService;
import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.application.DiscordLinkNotifier;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.persistence.discordlink.DiscordLinkStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the discord-link context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces both the Brigadier command list the plugin registers and the
 * {@link DiscordLinkConfirmation} seam implementation the plugin exposes through the {@code ServicesManager} so
 * the optional Discord bridge can redeem a {@code /link} code. This is the one place the discord-link context is
 * wired — nothing else news up its classes.
 *
 * <p>The store is the un-cached jOOQ adapter (linking is low-traffic). The one-time code's lifetime is the
 * module's {@code code-ttl-seconds} config value; codes are drawn from a {@link SecureRandom}-seeded generator
 * so a code is not guessable from a previous one.
 */
@NullMarked
public final class DiscordlinkWiring {

    private static final int DEFAULT_TTL_SECONDS = 600;

    private DiscordlinkWiring() {}

    /** Build the discord-link adapters and use cases over the kernel ports and the persistence DSL. */
    public static Wired wire(ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        KernelPorts kernel = ctx.kernel();
        DiscordLinkStore store = DiscordLinkStores.jooq(persistence);
        Clock clock = Clock.systemUTC();
        BeginLink beginLink = new BeginLink(store, clock, rng(), ttl(ctx));
        ConfirmLink confirmLink = new ConfirmLink(store, clock);
        DiscordLinkNotifier notifier = new DiscordLinkNotifier(kernel.messages(), kernel.messageSink());
        DiscordLinkServices services =
                new DiscordLinkServices(beginLink, confirmLink, new Unlink(store), new LinkStatus(store), notifier);
        DiscordLinkConfirmation confirmation = new ConfirmLinkService(confirmLink, kernel.playerLookup());
        return new Wired(DiscordLinkCommands.all(services), confirmation);
    }

    private static Duration ttl(ModuleContext ctx) {
        return Duration.ofSeconds(Math.max(1, ctx.config().getInt("code-ttl-seconds", DEFAULT_TTL_SECONDS)));
    }

    private static RandomGenerator rng() {
        // A SecureRandom-class generator so a fresh code is not predictable from a leaked previous one.
        return RandomGeneratorFactory.of("SecureRandom").create();
    }

    /**
     * Everything the discord-link module contributes once wired: the Brigadier commands and the seam
     * implementation the plugin registers into the {@code ServicesManager}. The context holds no repeating
     * scheduled work and no in-memory store, so there is nothing to drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param confirmation the {@code /link} confirmation seam the Discord bridge consumes
     */
    public record Wired(List<CommandRegistration> commands, DiscordLinkConfirmation confirmation) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(confirmation, "confirmation");
        }
    }
}

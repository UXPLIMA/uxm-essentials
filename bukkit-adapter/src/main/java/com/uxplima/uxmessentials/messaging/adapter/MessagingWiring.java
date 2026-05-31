package com.uxplima.uxmessentials.messaging.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.adapter.inbound.command.MessagingCommands;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience;
import com.uxplima.uxmessentials.messaging.adapter.outbound.CanSeeVanishVisibility;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.MailExpirySweep;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PdcMessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.MessagingNotifier;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.persistence.messaging.MessagingStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the messaging context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces everything the plugin must register: the Brigadier command list and the
 * self-rescheduling mail-expiry sweep. This is the one place the messaging context is wired — nothing else
 * news up its classes.
 *
 * <p>Two cross-context gates are soft-coupled here: the mute gate is bound to {@link MutePolicy#NEVER} until
 * the moderation context lands (the caller may hand a real policy through {@code wire}), and vanish-aware
 * visibility is the {@code canSee}-based adapter that degrades to "fully visible" when presence is disabled.
 * The mail repository is the plain jOOQ adapter; the ignore store is the Caffeine-cached jOOQ adapter; the
 * reply, socialspy, and toggle stores are in-memory/PDC (transient session state).
 */
@NullMarked
public final class MessagingWiring {

    private MessagingWiring() {}

    /** Build the messaging adapters and use cases from {@code ctx}, the {@code persistence} DSL, and a mute gate. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, Optional<MutePolicy> mute) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(mute, "mute");
        KernelPorts kernel = ctx.kernel();
        MessagingSettings settings = new MessagingSettings(ctx.config());
        AtomicBoolean running = new AtomicBoolean(true);
        Stores stores = stores(plugin, persistence);
        MessagingServices services =
                assemble(kernel, settings, stores, mute.orElse(MutePolicy.NEVER), Clock.systemUTC());
        MailExpirySweep sweep = new MailExpirySweep(
                kernel.scheduler(),
                stores.mail(),
                settings.sweepInterval(),
                settings.mailRetention(),
                running::get,
                kernel.log(),
                Clock.systemUTC());
        List<CommandRegistration> commands = MessagingCommands.all(services, kernel.messages(), kernel.messageSink());
        return new Wired(commands, sweep, stores, running);
    }

    private static MessagingServices assemble(
            KernelPorts kernel, MessagingSettings settings, Stores stores, MutePolicy mute, Clock clock) {
        MessagingNotifier notifier = new MessagingNotifier(kernel.messages(), kernel.messageSink());
        MessageDelivery delivery = new BukkitMessageDelivery(kernel.messages(), kernel.messageSink());
        VanishVisibility vanish = new CanSeeVanishVisibility();
        SendMessage sendMessage = new SendMessage(
                delivery,
                stores.ignores(),
                stores.conversations(),
                stores.toggles(),
                stores.socialSpy(),
                mute,
                notifier,
                kernel.events(),
                clock);
        return new MessagingServices(
                sendMessage,
                new Reply(
                        sendMessage,
                        stores.conversations(),
                        kernel.playerLookup(),
                        vanish,
                        notifier,
                        settings.replyTtl(),
                        clock),
                new SendMail(stores.mail(), stores.ignores(), delivery, mute, notifier, kernel.events(), clock),
                new ReadMail(stores.mail(), delivery, notifier),
                new ClearMail(stores.mail(), notifier),
                new MsgToggle(stores.toggles(), notifier),
                new Ignore(stores.ignores(), notifier),
                new Unignore(stores.ignores(), notifier),
                new SocialSpy(stores.socialSpy(), notifier),
                new HelpOp(new BukkitStaffAudience(), delivery, mute, notifier, kernel.events(), clock),
                kernel.playerLookup(),
                vanish);
    }

    private static Stores stores(Plugin plugin, Persistence persistence) {
        return new Stores(
                MessagingStores.mail(persistence),
                MessagingStores.ignores(persistence),
                new InMemoryConversationStore(),
                new PdcMessageToggleStore(plugin),
                new InMemorySocialSpyStore());
    }

    /** The constructed stores, held so the wiring's stop hook can drain the transient ones. */
    record Stores(
            MailRepository mail,
            IgnoreStore ignores,
            InMemoryConversationStore conversations,
            PdcMessageToggleStore toggles,
            InMemorySocialSpyStore socialSpy) {}

    /**
     * Everything the messaging module contributes once wired: the Brigadier commands, the self-rescheduling
     * mail-expiry sweep, the transient stores (cleared on stop), and the {@code running} flag the sweep
     * observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param expirySweep the self-rescheduling mail-expiry sweep, armed by the caller
     * @param stores the constructed stores, for the stop-time drain of the transient ones
     * @param running the flag flipped false on stop so the sweep exits
     */
    public record Wired(
            List<CommandRegistration> commands, MailExpirySweep expirySweep, Stores stores, AtomicBoolean running) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(stores, "stores");
            Objects.requireNonNull(running, "running");
        }

        /** Arm the mail-expiry sweep. */
        public void startBackgroundWork() {
            expirySweep.start();
        }

        /** Stop the sweep and drop the transient session stores. */
        public void stop() {
            running.set(false);
            stores.conversations().clear();
            stores.socialSpy().clear();
        }
    }
}

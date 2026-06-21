package com.uxplima.uxmessentials.messaging.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.adapter.inbound.command.MessagingCommands;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience;
import com.uxplima.uxmessentials.messaging.adapter.outbound.CanSeeVanishVisibility;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.MailExpirySweep;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PdcMessageToggleStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PdcReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.ListIgnores;
import com.uxplima.uxmessentials.messaging.application.MessagingNotifier;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.ReplyToggle;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMailToAll;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.persistence.messaging.MessagingStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoresMessagingPlaceholders;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the messaging context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces everything the plugin must register: the Brigadier command list and the
 * self-rescheduling mail-expiry sweep. This is the one place the messaging context is wired — nothing else
 * news up its classes.
 *
 * <p>Three cross-context gates are soft-coupled here: the mute gate is bound to {@link MutePolicy#NEVER} until
 * the moderation context lands (the caller may hand a real policy through {@code wire}); vanish-aware
 * visibility is the {@code canSee}-based adapter that degrades to "fully visible" when presence is disabled;
 * and the AFK status is a {@link MutableAfkStatus} bound to {@code AfkStatus.NEVER} until the presence context
 * lands and rebinds it (presence wires after messaging, so the binding arrives through the rebindable holder).
 * The mail repository is the plain jOOQ adapter; the ignore store is the Caffeine-cached jOOQ adapter; the
 * reply, socialspy, and toggle stores are in-memory/PDC (transient session state).
 */
@NullMarked
public final class MessagingWiring {

    private MessagingWiring() {}

    /** Build the messaging adapters and use cases from {@code ctx}, the {@code persistence} DSL, and a mute gate. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Optional<MutePolicy> mute,
            GuiText guiText,
            GuiLayouts guiLayouts,
            AnvilInput anvil) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(anvil, "anvil");
        KernelPorts kernel = ctx.kernel();
        MessagingSettings settings = new MessagingSettings(ctx.config());
        AtomicBoolean running = new AtomicBoolean(true);
        Stores stores = stores(plugin, persistence);
        // The mute gate forwards to NEVER until the moderation context lands and rebinds it (soft couple). If
        // a real policy is already available it is bound up front; otherwise moderation binds it on wire.
        MutableMutePolicy mutePolicy = new MutableMutePolicy();
        mute.ifPresent(mutePolicy::bind);
        // The AFK status starts on AfkStatus.NEVER; presence rebinds it through this holder when it wires
        // (presence is wired after messaging), and stays NEVER when presence is disabled (soft couple).
        MutableAfkStatus afkStatus = new MutableAfkStatus();
        MessagingServices services = assemble(kernel, settings, stores, mutePolicy, afkStatus, Clock.systemUTC());
        MailExpirySweep sweep = new MailExpirySweep(
                kernel.scheduler(),
                stores.mail(),
                settings.sweepInterval(),
                settings.mailRetention(),
                running::get,
                kernel.log(),
                Clock.systemUTC());
        // The GUIs read the raw stores (so the rendered state matches in-game state) and write through the same
        // use cases the commands hold (so notifiers and persistence stay consistent). The four stores are threaded
        // in individually rather than as the package-private Stores record, keeping the views decoupled from it.
        MessagingGuiViews views = MessagingGuiViews.create(
                guiText,
                kernel.scheduler(),
                kernel.messages(),
                kernel.permissions(),
                services,
                stores.toggles(),
                stores.socialSpy(),
                stores.ignores(),
                stores.mail(),
                kernel.playerLookup(),
                anvil,
                guiLayouts);
        List<CommandRegistration> commands =
                MessagingCommands.all(services, kernel.messages(), kernel.messageSink(), views);
        return new Wired(commands, sweep, stores, mutePolicy, afkStatus, running, views);
    }

    private static MessagingServices assemble(
            KernelPorts kernel,
            MessagingSettings settings,
            Stores stores,
            MutePolicy mute,
            AfkStatus afk,
            Clock clock) {
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
                afk,
                stores.mail(),
                settings.offlineToMail(),
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
                        stores.replyRouting(),
                        notifier,
                        settings.replyTtl(),
                        clock),
                new SendMail(stores.mail(), stores.ignores(), delivery, mute, notifier, kernel.events(), clock),
                new SendMailToAll(stores.mail(), clock),
                new ReadMail(stores.mail(), delivery, notifier),
                new ClearMail(stores.mail(), notifier),
                new MsgToggle(stores.toggles(), notifier),
                new ReplyToggle(stores.replyRouting(), notifier),
                new Ignore(stores.ignores(), notifier),
                new Unignore(stores.ignores(), notifier),
                new ListIgnores(stores.ignores(), notifier),
                new SocialSpy(stores.socialSpy(), notifier),
                new HelpOp(new BukkitStaffAudience(), delivery, mute, notifier, kernel.events(), clock),
                kernel.playerLookup(),
                vanish,
                kernel.scheduler());
    }

    private static Stores stores(Plugin plugin, Persistence persistence) {
        return new Stores(
                MessagingStores.mail(persistence),
                MessagingStores.ignores(persistence),
                new InMemoryConversationStore(),
                new PdcMessageToggleStore(plugin),
                new PdcReplyRoutingStore(plugin),
                new InMemorySocialSpyStore());
    }

    /** The constructed stores, held so the wiring's stop hook can drain the transient ones. */
    record Stores(
            MailRepository mail,
            IgnoreStore ignores,
            InMemoryConversationStore conversations,
            PdcMessageToggleStore toggles,
            PdcReplyRoutingStore replyRouting,
            InMemorySocialSpyStore socialSpy) {}

    /**
     * Everything the messaging module contributes once wired: the Brigadier commands, the self-rescheduling
     * mail-expiry sweep, the transient stores (cleared on stop), and the {@code running} flag the sweep
     * observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param expirySweep the self-rescheduling mail-expiry sweep, armed by the caller
     * @param stores the constructed stores, for the stop-time drain of the transient ones
     * @param mutePolicy the rebindable mute gate the moderation context fills in when it wires
     * @param afkStatus the rebindable AFK status the presence context fills in when it wires
     * @param running the flag flipped false on stop so the sweep exits
     * @param guiViews the player-facing GUIs (settings, ignore-list, mailbox), for the command and hub openers
     */
    public record Wired(
            List<CommandRegistration> commands,
            MailExpirySweep expirySweep,
            Stores stores,
            MutableMutePolicy mutePolicy,
            MutableAfkStatus afkStatus,
            AtomicBoolean running,
            MessagingGuiViews guiViews) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(stores, "stores");
            Objects.requireNonNull(mutePolicy, "mutePolicy");
            Objects.requireNonNull(afkStatus, "afkStatus");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(guiViews, "guiViews");
        }

        /** Arm the mail-expiry sweep. */
        public void startBackgroundWork() {
            expirySweep.start();
        }

        /**
         * The PAPI read seam over this module's mail, conversation, message-toggle, social-spy, and ignore
         * stores — the same stores the {@code /mail}, {@code /reply}, {@code /msgtoggle}, {@code /socialspy},
         * and {@code /ignore} use cases hold, so a placeholder matches the player's in-game state.
         */
        public StoresMessagingPlaceholders placeholders() {
            return new StoresMessagingPlaceholders(
                    stores.mail(), stores.conversations(), stores.toggles(), stores.socialSpy(), stores.ignores());
        }

        /** Stop the sweep and drop the transient session stores. */
        public void stop() {
            running.set(false);
            stores.conversations().clear();
            stores.socialSpy().clear();
        }
    }
}

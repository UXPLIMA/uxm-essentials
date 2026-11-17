package com.uxplima.uxmessentials.messaging.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.adapter.inbound.command.MessagingCommands;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.messaging.adapter.inbound.listener.UnreadMailListener;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BusMessagingNetwork;
import com.uxplima.uxmessentials.messaging.adapter.outbound.MailExpirySweep;
import com.uxplima.uxmessentials.messaging.adapter.outbound.api.MessagingApiWrites;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.ListIgnores;
import com.uxplima.uxmessentials.messaging.application.MessagingSwitches;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.RelayPrivateMessage;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.ReplyToggle;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMailToAll;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SharedConversations;
import com.uxplima.uxmessentials.messaging.application.SharedSocialSpy;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.UnreadMailNotice;
import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.persistence.messaging.CachedIgnoreStore;
import com.uxplima.uxmessentials.persistence.messaging.MessagingStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.settings.SettingsStores;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.settings.HeldSettingsListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.settings.LegacySwitches;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.IgnoreSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.PlayerSettingsSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.chat.UxmChatStepBack;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoresMessagingPlaceholders;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.application.settings.SettingsSessions;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the messaging context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces everything the plugin must register: the Brigadier command list and the
 * self-rescheduling mail-expiry sweep. This is the one place the messaging context is wired: nothing else
 * news up its classes.
 *
 * <p>Three cross-context gates are soft-coupled here: the mute gate is bound to {@link MutePolicy#NEVER} until
 * the moderation context lands (the caller may hand a real policy through {@code wire}); vanish-aware
 * visibility is the {@code vanish} gate handed in by bootstrap. The authority-reading adapter over the vanish
 * store, or {@link VanishVisibility#ALWAYS_VISIBLE} when the vanish module is disabled ("no one is hidden");
 * and the AFK status is a {@link MutableAfkStatus} bound to {@code AfkStatus.NEVER} until the presence context
 * lands and rebinds it (presence wires after messaging, so the binding arrives through the rebindable holder).
 * The mail repository is the plain jOOQ adapter; the ignore store is the Caffeine-cached jOOQ adapter; the
 * reply partner, socialspy and toggle stores hold the settings every backend shares for the players online here,
 * read at login and announced on the bus when they change.
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
            VanishVisibility vanish,
            Bus bus,
            NetworkPresence presence,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(vanish, "vanish");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        MessagingSettings settings = new MessagingSettings(ctx.config());
        AtomicBoolean running = new AtomicBoolean(true);
        Stores stores = stores(kernel, persistence, bus);
        // The mute gate forwards to NEVER until the moderation context lands and rebinds it (soft couple). If
        // a real policy is already available it is bound up front; otherwise moderation binds it on wire.
        MutableMutePolicy mutePolicy = new MutableMutePolicy();
        mute.ifPresent(mutePolicy::bind);
        // The AFK status starts on AfkStatus.NEVER; presence rebinds it through this holder when it wires
        // (presence is wired after messaging), and stays NEVER when presence is disabled (soft couple).
        MutableAfkStatus afkStatus = new MutableAfkStatus();
        // Private messages to players on other backends and help requests for their staff ride the shared bus; with
        // the network off the publisher discards them and presence knows only this backend, so nothing changes.
        BusMessagingNetwork network = new BusMessagingNetwork(bus.publisher(), kernel.log());
        // Where uxmChat runs private messages, its ignore lists and toggles decide; a message from another backend is
        // then answered as offline here rather than judged by this module's own, unused stores.
        UxmChatStepBack chat = new UxmChatStepBack(plugin.getServer());
        MessagingServices services = assemble(
                kernel, settings, stores, mutePolicy, afkStatus, vanish, network, presence, chat, Clock.systemUTC());
        HeldSettingsListener heldSettings = new HeldSettingsListener(
                new SettingsSessions(stores.held(), kernel.scheduler()), kernel.playerLookup());
        heldSettings.adoptOnline(plugin.getServer().getOnlinePlayers());
        // An earlier version kept these two switches in the player's data on each server; they move to the shared
        // switches as each player next joins.
        List<Listener> listeners = List.of(
                heldSettings,
                new LegacySwitches(
                        plugin,
                        stores.switches(),
                        Map.of(
                                "msg-toggle-off", MessagingSwitches.MESSAGES,
                                "reply-toggle-off", MessagingSwitches.REPLIES)),
                // Mail left while a player was away, on any backend, is announced as they join; the count is read
                // off the tick.
                new UnreadMailListener(
                        new UnreadMailNotice(
                                stores.mail(),
                                new BukkitMessageDelivery(kernel.messages(), kernel.messageSink()),
                                kernel.log(),
                                presence),
                        kernel.scheduler()));
        network.bind(services.relay(), services.helpOp(), services.sendMail());
        bus.registry().register(network::onFrame);
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
                new PaperScheduler(plugin),
                kernel.messages(),
                kernel.permissions(),
                services,
                stores.toggles(),
                stores.socialSpy(),
                stores.ignores(),
                stores.mail(),
                kernel.playerLookup(),
                textInput,
                guiLayouts,
                menus,
                menuBindings,
                plugin.getDataFolder().toPath(),
                kernel.log());
        List<CommandRegistration> commands = MessagingCommands.all(
                services,
                kernel.messages(),
                kernel.messageSink(),
                views,
                () -> presence.online().stream()
                        .filter(online -> !online.server().equals(presence.localServer()))
                        .map(OnlinePlayer::player)
                        .toList());
        return new Wired(
                commands,
                listeners,
                sweep,
                stores,
                mutePolicy,
                afkStatus,
                running,
                views,
                MessagingApiWrites.of(services));
    }

    private static MessagingServices assemble(
            KernelPorts kernel,
            MessagingSettings settings,
            Stores stores,
            MutePolicy mute,
            AfkStatus afk,
            VanishVisibility vanish,
            BusMessagingNetwork network,
            NetworkPresence presence,
            UxmChatStepBack chat,
            Clock clock) {
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        MessageDelivery delivery = new BukkitMessageDelivery(kernel.messages(), kernel.messageSink());
        SendMail sendMail = new SendMail(
                stores.mail(), stores.ignores(), delivery, network, mute, notifier, kernel.events(), clock);
        SendMessage sendMessage = new SendMessage(
                delivery,
                stores.ignores(),
                stores.conversations(),
                stores.toggles(),
                stores.socialSpy(),
                network,
                mute,
                afk,
                sendMail,
                settings.offlineToMail(),
                notifier,
                kernel.events(),
                clock);
        RelayPrivateMessage relay = new RelayPrivateMessage(
                sendMessage,
                stores.replyRouting(),
                vanish,
                presence,
                network,
                notifier,
                kernel.scheduler(),
                () -> chat.covers(UxmChatStepBack.PRIVATE_MESSAGES));
        return new MessagingServices(
                sendMessage,
                relay,
                new Reply(
                        sendMessage,
                        stores.conversations(),
                        kernel.playerLookup(),
                        vanish,
                        stores.replyRouting(),
                        notifier,
                        settings.replyTtl(),
                        clock,
                        relay,
                        kernel.scheduler()),
                sendMail,
                new SendMailToAll(stores.mail(), clock),
                new ReadMail(stores.mail(), delivery, notifier),
                new ClearMail(stores.mail(), notifier),
                new MsgToggle(stores.toggles(), notifier),
                new ReplyToggle(stores.replyRouting(), notifier),
                new Ignore(stores.ignores(), notifier),
                new Unignore(stores.ignores(), notifier),
                new ListIgnores(stores.ignores(), notifier),
                new SocialSpy(stores.socialSpy(), notifier),
                new HelpOp(
                        new BukkitStaffAudience(),
                        delivery,
                        mute,
                        notifier,
                        kernel.events(),
                        clock,
                        network,
                        presence,
                        kernel.scheduler()),
                kernel.playerLookup(),
                vanish,
                kernel.scheduler());
    }

    private static Stores stores(KernelPorts kernel, Persistence persistence, Bus bus) {
        // The concrete cache is what the cross-server listener invalidates per owner; the broadcasting decorator
        // wraps that same cache so a local /ignore or /unignore announces it to peers (the player-warps seam,
        // copied for the ignore list). The /msg delivery path reads this same cache, so the loop closes.
        CachedIgnoreStore cachedIgnores = MessagingStores.cachedIgnores(persistence);
        bus.registry().register(IgnoreSync.listener(cachedIgnores));
        bus.registry().registerPlayerCacheDrop(cachedIgnores::invalidateAll);
        IgnoreStore ignores = IgnoreSync.store(cachedIgnores, bus.publisher());
        // The switches, the watched players and the reply partner are shared through the database, held for the
        // players online here, and announced when a switch or a watched player changes.
        Consumer<UUID> announce = PlayerSettingsSync.announcer(bus.publisher());
        PlayerSwitches switches = new PlayerSwitches(
                MessagingSwitches.NAMES,
                SettingsStores.switches(persistence),
                Clock.systemUTC(),
                kernel.scheduler(),
                kernel.log(),
                announce);
        MessagingSwitches named = new MessagingSwitches(switches);
        SharedSocialSpy socialSpy = new SharedSocialSpy(
                switches,
                MessagingStores.spyTargets(persistence),
                kernel.playerLookup(),
                kernel.permissions(),
                kernel.scheduler(),
                kernel.log(),
                announce);
        SharedConversations conversations = new SharedConversations(
                MessagingStores.replyPartners(persistence), kernel.scheduler(), kernel.log(), announce);
        HeldSettings held = HeldSettings.of(switches, socialSpy, conversations);
        PlayerSettingsSync.register(bus.registry(), held);
        return new Stores(
                MessagingStores.mail(persistence),
                ignores,
                conversations,
                named.messages(),
                named.replies(),
                socialSpy,
                switches,
                held);
    }

    /**
     * The constructed stores, held so bootstrap can build the published messaging query over the very same mail,
     * ignore and toggle stores the commands use, and so the login listener reads the shared settings into them.
     */
    public record Stores(
            MailRepository mail,
            IgnoreStore ignores,
            SharedConversations conversations,
            MessageToggleStore toggles,
            ReplyRoutingStore replyRouting,
            SharedSocialSpy socialSpy,
            PlayerSwitches switches,
            HeldSettings held) {}

    /**
     * Everything the messaging module contributes once wired: the Brigadier commands, the self-rescheduling
     * mail-expiry sweep, the listeners that read the shared settings at login, the stores, and the {@code running}
     * flag the sweep observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the login and join listeners that bring in each player's shared settings, and the one
     *     that tells a player about mail they have not read
     * @param expirySweep the self-rescheduling mail-expiry sweep, armed by the caller
     * @param stores the constructed stores, for the published query and placeholders
     * @param mutePolicy the rebindable mute gate the moderation context fills in when it wires
     * @param afkStatus the rebindable AFK status the presence context fills in when it wires
     * @param running the flag flipped false on stop so the sweep exits
     * @param guiViews the player-facing GUIs (settings, ignore-list, mailbox), for the command and hub openers
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            MailExpirySweep expirySweep,
            Stores stores,
            MutableMutePolicy mutePolicy,
            MutableAfkStatus afkStatus,
            AtomicBoolean running,
            MessagingGuiViews guiViews,
            MessagingApiWrites apiWrites) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(stores, "stores");
            Objects.requireNonNull(mutePolicy, "mutePolicy");
            Objects.requireNonNull(afkStatus, "afkStatus");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(guiViews, "guiViews");
            Objects.requireNonNull(apiWrites, "apiWrites");
        }

        /** Arm the mail-expiry sweep. */
        public void startBackgroundWork() {
            expirySweep.start();
        }

        /**
         * The PAPI read seam over this module's mail, conversation, message-toggle, social-spy, and ignore
         * stores. The same stores the {@code /mail}, {@code /reply}, {@code /msgtoggle}, {@code /socialspy},
         * and {@code /ignore} use cases hold, so a placeholder matches the player's in-game state.
         */
        public StoresMessagingPlaceholders placeholders() {
            return new StoresMessagingPlaceholders(
                    stores.mail(), stores.conversations(), stores.toggles(), stores.socialSpy(), stores.ignores());
        }

        /** Stop the sweep. */
        public void stop() {
            running.set(false);
        }
    }
}

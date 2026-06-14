package com.uxplima.uxmessentials.staff.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.staff.StaffStores;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffChatCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffListCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffModeCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffListView;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffNavigatorView;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffGadgetActions;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffJoinListener;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffAlerts;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffChannel;
import com.uxplima.uxmessentials.staff.adapter.outbound.ModerationStaffFreeze;
import com.uxplima.uxmessentials.staff.adapter.outbound.PlayerstateStaffInspector;
import com.uxplima.uxmessentials.staff.adapter.outbound.PresenceStaffVanish;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.adapter.outbound.TeleportStaffTeleport;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.StaffNotifier;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the staff context's adapters and use cases over the injected kernel ports, the persistence DSL, and
 * the soft-coupled seams handed in from the presence, playerstate, and messaging modules (captured during their
 * wiring, since staff wires last). This is the one place the staff context is wired.
 *
 * <p>The five soft couplings ride rebindable holders ({@link MutableStaffVanish}, {@link MutableStaffInspector},
 * {@link MutableStaffChannel}, {@link MutableStaffFreeze}, {@link MutableStaffTeleport}), each starting on its
 * port's {@code NONE} and bound to the real presence/playerstate/messaging/moderation/teleport impl only when
 * that module is enabled — so a disabled source module degrades the matching gadget or staff chat to a no-op
 * rather than failing (mirroring messaging's {@code MutableMutePolicy}).
 *
 * <p>The loadout is DB-backed through the jOOQ {@code StaffLoadoutRepository} (built via {@link StaffStores})
 * so it survives a restart (the item-loss-safe net). On stop the wiring exits every staff member still in staff
 * mode, restoring their real loadout, so a disable or reload never strands anyone in the gadget hotbar.
 */
@NullMarked
public final class StaffWiring {

    private StaffWiring() {}

    /**
     * Build the staff adapters from {@code plugin}, {@code ctx}, the {@code persistence} DSL, the {@code seams},
     * and the in-process event bus. The bus is the concrete {@link InProcessDomainEventPublisher} so the
     * enter/exit roster-alert subscriber can be registered here and unsubscribed on stop — the kernel port
     * exposes only {@code publish}.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            StaffSeams seams,
            InProcessDomainEventPublisher events) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(seams, "seams");
        Objects.requireNonNull(events, "events");
        KernelPorts kernel = ctx.kernel();
        StaffSettings settings = new StaffSettings(ctx.config(), kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        StaffGadgetItems gadgetItems = new StaffGadgetItems(plugin);
        StaffModeStoreImpl store = new StaffModeStoreImpl();
        StaffLoadoutRepository repository = StaffStores.loadouts(persistence, Clock.systemUTC());
        StaffNotifier notifier = new StaffNotifier(kernel.messages(), kernel.messageSink());

        // vanish is built (and the seams bound) before the capture, because the capture reads the pre-mode
        // vanish state through it so the player's vanish flag is part of the captured loadout.
        MutableStaffVanish vanish = new MutableStaffVanish();
        MutableStaffInspector inspector = new MutableStaffInspector();
        MutableStaffChannel channel = new MutableStaffChannel();
        MutableStaffFreeze freeze = new MutableStaffFreeze();
        MutableStaffTeleport teleport = new MutableStaffTeleport();
        bindSeams(seams, plugin, kernel, settings, vanish, inspector, channel, freeze, teleport);

        BukkitStaffLoadoutCapture capture = new BukkitStaffLoadoutCapture(settings, gadgetItems, vanish);
        RecoverStaffLoadout recover = new RecoverStaffLoadout(store, repository, capture, vanish, notifier);
        EnterStaffMode enter = new EnterStaffMode(
                store,
                repository,
                capture,
                vanish,
                notifier,
                kernel.events(),
                recover,
                StaffSettings.DEFAULT_MODE,
                settings.vanishOnEnter());
        ExitStaffMode exit = new ExitStaffMode(store, repository, capture, vanish, notifier, kernel.events());
        SendStaffChat staffChat = new SendStaffChat(channel, kernel.events());
        StaffServices services = new StaffServices(enter, exit, recover, staffChat, inspector, store);

        StaffFollowService followService = new StaffFollowService(
                plugin.getServer(),
                kernel.scheduler(),
                notifier,
                kernel.log(),
                staffId -> store.activePlayers().contains(staffId),
                settings.followIntervalTicks());
        StaffExamineView examineView =
                new StaffExamineView(kernel.messages(), kernel.messageSink(), kernel.scheduler(), services);
        StaffNavigatorView navigatorView = new StaffNavigatorView(
                plugin.getServer(), kernel.messages(), kernel.messageSink(), kernel.scheduler(), teleport);
        StaffListView listView = new StaffListView(
                plugin.getServer(), kernel.messages(), kernel.messageSink(), kernel.scheduler(), teleport);
        StaffGadgetActions actions =
                new StaffGadgetActions(vanish, freeze, teleport, followService, examineView, navigatorView, notifier);

        List<CommandRegistration> commands = List.of(
                new StaffModeCommand(
                        services, kernel.messages(), kernel.scheduler(), kernel.playerLookup(), running::get),
                new StaffChatCommand(services, kernel.messages()),
                new StaffListCommand(services, kernel.messages(), listView));
        List<Listener> listeners = List.of(
                new StaffModeListener(services, gadgetItems, followService, actions),
                new StaffJoinListener(services, repository, kernel.scheduler()));

        // Roster alerts ride the messaging staff-chat audience, so they are only wired when messaging is enabled.
        // EnterStaffMode/ExitStaffMode publish the toggle events; RecoverStaffLoadout publishes neither, so a
        // crash recovery on join never broadcasts an alert. The subscriber is dropped on stop to avoid a leak.
        @Nullable Consumer<DomainEvent> alertSubscriber = subscribeAlerts(seams, settings, kernel, events);
        return new Wired(
                commands, listeners, services, followService, kernel.scheduler(), running, events, alertSubscriber);
    }

    private static @Nullable Consumer<DomainEvent> subscribeAlerts(
            StaffSeams seams, StaffSettings settings, KernelPorts kernel, InProcessDomainEventPublisher events) {
        if (seams.staffAudience().isEmpty()) {
            return null;
        }
        MessagingStaffAlerts alerts = new MessagingStaffAlerts(
                seams.staffAudience().get(), kernel.messageSink(), kernel.messages(), settings.staffChatNode());
        Consumer<DomainEvent> subscriber = event -> {
            if (event instanceof StaffModeEntered entered) {
                alerts.announceEnter(entered.staff());
            } else if (event instanceof StaffModeExited exited) {
                alerts.announceExit(exited.staff());
            }
        };
        events.subscribe(subscriber);
        return subscriber;
    }

    private static void bindSeams(
            StaffSeams seams,
            Plugin plugin,
            KernelPorts kernel,
            StaffSettings settings,
            MutableStaffVanish vanish,
            MutableStaffInspector inspector,
            MutableStaffChannel channel,
            MutableStaffFreeze freeze,
            MutableStaffTeleport teleport) {
        seams.presenceVanish().ifPresent(p -> vanish.bind(new PresenceStaffVanish(p.toggleVanish(), p.store())));
        seams.openContainer().ifPresent(open -> inspector.bind(new PlayerstateStaffInspector(open)));
        seams.staffAudience()
                .ifPresent(audience -> channel.bind(new MessagingStaffChannel(
                        audience, kernel.messages(), kernel.messageSink(), settings.staffChatNode())));
        seams.moderationFreeze().ifPresent(m -> freeze.bind(new ModerationStaffFreeze(m.freeze(), m.sanctions())));
        seams.teleport().ifPresent(t -> teleport.bind(new TeleportStaffTeleport(t.engine(), plugin.getServer())));
    }

    /**
     * The soft-coupled seams staff binds when their source modules are enabled. Each is optional: an absent seam
     * leaves the matching holder on {@code NONE}, so the gadget or staff chat degrades to a no-op.
     *
     * @param presenceVanish the presence toggle + store backing the VANISH gadget and vanish-on-enter
     * @param openContainer the playerstate inventory-open use case backing the EXAMINE gadget
     * @param staffAudience the messaging staff-audience resolver backing staff chat
     * @param moderationFreeze the moderation freeze use case + sanction read backing the FREEZE gadget
     * @param teleport the teleport engine backing the COMPASS gadget and {@code /stafflist}
     */
    public record StaffSeams(
            Optional<PresenceVanishSeam> presenceVanish,
            Optional<OpenContainer> openContainer,
            Optional<StaffAudience> staffAudience,
            Optional<ModerationFreezeSeam> moderationFreeze,
            Optional<TeleportSeam> teleport) {

        public StaffSeams {
            Objects.requireNonNull(presenceVanish, "presenceVanish");
            Objects.requireNonNull(openContainer, "openContainer");
            Objects.requireNonNull(staffAudience, "staffAudience");
            Objects.requireNonNull(moderationFreeze, "moderationFreeze");
            Objects.requireNonNull(teleport, "teleport");
        }

        /** The seam set with nothing bound — every soft couple degrades to a no-op. */
        public static StaffSeams none() {
            return new StaffSeams(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /** The presence handles staff needs to set a vanish state absolutely (toggle + a read of the current flag). */
    public record PresenceVanishSeam(ToggleVanish toggleVanish, PresenceStore store) {
        public PresenceVanishSeam {
            Objects.requireNonNull(toggleVanish, "toggleVanish");
            Objects.requireNonNull(store, "store");
        }
    }

    /** The moderation handles the FREEZE gadget needs: the audited freeze use case and the live freeze-state read. */
    public record ModerationFreezeSeam(Freeze freeze, Sanctions sanctions) {
        public ModerationFreezeSeam {
            Objects.requireNonNull(freeze, "freeze");
            Objects.requireNonNull(sanctions, "sanctions");
        }
    }

    /** The teleport handle the COMPASS gadget and {@code /stafflist} need: the admin-teleport engine. */
    public record TeleportSeam(TeleportEngine engine) {
        public TeleportSeam {
            Objects.requireNonNull(engine, "engine");
        }
    }

    /**
     * Everything the staff module contributes once wired: the Brigadier commands, the gadget/connection
     * listener, and the FOLLOW gadget's repeating task. {@link #stop()} exits every staff member still in staff
     * mode — restoring their real loadout — and then cancels the follow task, in that order so a follow-shutdown
     * failure never aborts the loadout restore and strands anyone in the gadget hotbar or leaves a follow running.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the gadget interaction and quit listener to register
     * @param services the constructed use cases and the active-staff store, used to exit everyone on stop
     * @param followService the FOLLOW gadget runtime, shut down on stop to cancel its repeating task
     * @param scheduler the Scheduler port, used to run each exit on the player's entity thread on stop
     * @param running the flag flipped false on stop so the toggle command stops accepting new entries
     * @param eventBus the in-process event bus the roster-alert subscriber is registered on
     * @param alertSubscriber the enter/exit alert consumer to unsubscribe on stop (null when messaging is off)
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            StaffServices services,
            StaffFollowService followService,
            com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler,
            AtomicBoolean running,
            InProcessDomainEventPublisher eventBus,
            @Nullable Consumer<DomainEvent> alertSubscriber) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(followService, "followService");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(eventBus, "eventBus");
        }

        /** Exit every online staff member still in staff mode, restoring their real loadout on their entity thread. */
        public void stop() {
            running.set(false);
            if (alertSubscriber != null) {
                eventBus.unsubscribe(alertSubscriber);
            }
            // Restore every staff member's real loadout first, then stop the follow runtime: a follow-shutdown
            // failure must never abort the loadout restore and strand staff in the gadget hotbar on disable.
            for (UUID id : services.store().activePlayers()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                scheduler.onEntity(who, () -> services.exit().exit(who));
            }
            followService.shutdown();
        }
    }
}

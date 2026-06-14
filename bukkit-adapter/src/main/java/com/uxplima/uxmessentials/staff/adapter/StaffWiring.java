package com.uxplima.uxmessentials.staff.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.staff.StaffStores;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffChatCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffModeCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffJoinListener;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffChannel;
import com.uxplima.uxmessentials.staff.adapter.outbound.PlayerstateStaffInspector;
import com.uxplima.uxmessentials.staff.adapter.outbound.PresenceStaffVanish;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.StaffNotifier;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the staff context's adapters and use cases over the injected kernel ports, the persistence DSL, and
 * the soft-coupled seams handed in from the presence, playerstate, and messaging modules (captured during their
 * wiring, since staff wires last). This is the one place the staff context is wired.
 *
 * <p>The three soft couplings ride rebindable holders ({@link MutableStaffVanish}, {@link MutableStaffInspector},
 * {@link MutableStaffChannel}), each starting on its port's {@code NONE} and bound to the real presence/
 * playerstate/messaging impl only when that module is enabled — so a disabled source module degrades the
 * matching gadget or staff chat to a no-op rather than failing (mirroring messaging's {@code MutableMutePolicy}).
 *
 * <p>The loadout is DB-backed through the jOOQ {@code StaffLoadoutRepository} (built via {@link StaffStores})
 * so it survives a restart (the item-loss-safe net). On stop the wiring exits every staff member still in staff
 * mode, restoring their real loadout, so a disable or reload never strands anyone in the gadget hotbar.
 */
@NullMarked
public final class StaffWiring {

    private StaffWiring() {}

    /** Build the staff adapters from {@code plugin}, {@code ctx}, the {@code persistence} DSL, and the {@code seams}. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, StaffSeams seams) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(seams, "seams");
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
        bindSeams(seams, kernel, settings, vanish, inspector, channel);

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

        StaffExamineView examineView =
                new StaffExamineView(kernel.messages(), kernel.messageSink(), kernel.scheduler(), services);
        List<CommandRegistration> commands = List.of(
                new StaffModeCommand(
                        services, kernel.messages(), kernel.scheduler(), kernel.playerLookup(), running::get),
                new StaffChatCommand(services, kernel.messages()));
        List<Listener> listeners = List.of(
                new StaffModeListener(services, gadgetItems, vanish, examineView),
                new StaffJoinListener(services, repository, kernel.scheduler()));
        return new Wired(commands, listeners, services, kernel.scheduler(), running);
    }

    private static void bindSeams(
            StaffSeams seams,
            KernelPorts kernel,
            StaffSettings settings,
            MutableStaffVanish vanish,
            MutableStaffInspector inspector,
            MutableStaffChannel channel) {
        seams.presenceVanish().ifPresent(p -> vanish.bind(new PresenceStaffVanish(p.toggleVanish(), p.store())));
        seams.openContainer().ifPresent(open -> inspector.bind(new PlayerstateStaffInspector(open)));
        seams.staffAudience()
                .ifPresent(audience -> channel.bind(new MessagingStaffChannel(
                        audience, kernel.messages(), kernel.messageSink(), settings.staffChatNode())));
    }

    /**
     * The soft-coupled seams staff binds when their source modules are enabled. Each is optional: an absent seam
     * leaves the matching holder on {@code NONE}, so the gadget or staff chat degrades to a no-op.
     *
     * @param presenceVanish the presence toggle + store backing the VANISH gadget and vanish-on-enter
     * @param openContainer the playerstate inventory-open use case backing the EXAMINE gadget
     * @param staffAudience the messaging staff-audience resolver backing staff chat
     */
    public record StaffSeams(
            Optional<PresenceVanishSeam> presenceVanish,
            Optional<OpenContainer> openContainer,
            Optional<StaffAudience> staffAudience) {

        public StaffSeams {
            Objects.requireNonNull(presenceVanish, "presenceVanish");
            Objects.requireNonNull(openContainer, "openContainer");
            Objects.requireNonNull(staffAudience, "staffAudience");
        }

        /** The seam set with nothing bound — every soft couple degrades to a no-op. */
        public static StaffSeams none() {
            return new StaffSeams(Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /** The presence handles staff needs to set a vanish state absolutely (toggle + a read of the current flag). */
    public record PresenceVanishSeam(ToggleVanish toggleVanish, PresenceStore store) {
        public PresenceVanishSeam {
            Objects.requireNonNull(toggleVanish, "toggleVanish");
            Objects.requireNonNull(store, "store");
        }
    }

    /**
     * Everything the staff module contributes once wired: the Brigadier commands and the gadget/connection
     * listener. There is no repeating scheduled work. {@link #stop()} exits every staff member still in staff
     * mode, restoring their real loadout so a disable/reload never strands anyone in the gadget hotbar.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the gadget interaction and quit listener to register
     * @param services the constructed use cases and the active-staff store, used to exit everyone on stop
     * @param scheduler the Scheduler port, used to run each exit on the player's entity thread on stop
     * @param running the flag flipped false on stop so the toggle command stops accepting new entries
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            StaffServices services,
            com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler,
            AtomicBoolean running) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(running, "running");
        }

        /** Exit every online staff member still in staff mode, restoring their real loadout on their entity thread. */
        public void stop() {
            running.set(false);
            for (UUID id : services.store().activePlayers()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                scheduler.onEntity(who, () -> services.exit().exit(who));
            }
        }
    }
}

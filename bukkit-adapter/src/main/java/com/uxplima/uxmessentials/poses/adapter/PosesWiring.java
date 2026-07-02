package com.uxplima.uxmessentials.poses.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.poses.adapter.inbound.command.PosesCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.SitCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PlayerSitInteractListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCleanupListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.AllowAllRegionGate;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesConfig;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the poses context's adapters and use cases over the injected kernel ports, and produces the {@code
 * /sit} and {@code /poses} commands plus the listeners the plugin registers. The seat port is a real, tagged,
 * non-persistent seat entity ({@link BukkitSeatPort}); the region gate is the permissive Phase-1
 * {@link AllowAllRegionGate} (the Phase-5 claim/WorldGuard gate slots in here with no reach into the use cases); the
 * player-sit opt-out is PDC-backed ({@link PdcPlayerSitPreferences}). The context persists nothing — a pose is
 * transient state in {@link PoseSessions} — so there is no repository or migration.
 *
 * <p>On enable the caller runs {@link BukkitSeatPort#sweepOrphans()} to reap any seat a previous run's crash left
 * behind, and on stop {@link Wired#stop()} removes every live seat and clears the registry, so a disable or reload
 * leaves zero residual state and no ghost entity.
 */
@NullMarked
public final class PosesWiring {

    private PosesWiring() {}

    /** Build the poses adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        PosesConfig config = PosesConfig.from(ctx.config());
        PoseSessions sessions = new PoseSessions();
        SittableBlocks sittableBlocks = new SittableBlocks(config.sittableMaterials());
        BukkitSeatPort seats = new BukkitSeatPort(plugin, kernel.scheduler(), kernel.log());
        PoseRegionGate regionGate = new AllowAllRegionGate();
        BukkitPoseReturn poseReturn = new BukkitPoseReturn(plugin, kernel.scheduler());
        PlayerSitPreferences playerSitPreferences = new PdcPlayerSitPreferences();

        StartSit startSit = new StartSit(
                sessions,
                seats,
                regionGate,
                kernel.playerLocator(),
                kernel.events(),
                Clock.systemUTC(),
                config.features().sit(),
                config.returnToStart());
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                playerSitPreferences,
                kernel.playerLocator(),
                kernel.events(),
                Clock.systemUTC(),
                config.features().playerSit());
        TogglePlayerSit togglePlayerSit = new TogglePlayerSit(playerSitPreferences);
        StopPose stopPose = new StopPose(sessions, seats, poseReturn, kernel.events(), config.returnToStart());

        List<CommandRegistration> commands = List.of(
                new SitCommand(startSit, sittableBlocks, kernel.messages(), config.sitOnBlocks(), config.maxDistance()),
                new PosesCommand(togglePlayerSit, kernel.messages()));
        List<Listener> listeners = List.of(
                new SeatInteractListener(
                        startSit, seats, sittableBlocks, kernel.messages(), config.sitOnBlocks(), config.maxDistance()),
                new PlayerSitInteractListener(startPlayerSit, kernel.messages()),
                new PoseCancelListener(stopPose, sessions),
                new PoseCleanupListener(seats));
        return new Wired(commands, listeners, seats, sessions, playerSitPreferences);
    }

    /**
     * Everything the poses module contributes once wired: the {@code /sit} command, the interact / cancel / cleanup
     * listeners, the seat port (swept on enable, drained on stop), and the session registry (the placeholder seam's
     * read source, cleared on stop).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param seats the seat port, so the caller can sweep orphans on enable and drain on stop
     * @param sessions the session registry, exposed for the {@code poses_sitting} placeholder seam
     * @param playerSitPreferences the player-sit opt-out store, exposed for the {@code poses_toggle} placeholder seam
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            BukkitSeatPort seats,
            PoseSessions sessions,
            PlayerSitPreferences playerSitPreferences) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(seats, "seats");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(playerSitPreferences, "playerSitPreferences");
        }

        /** Remove every live seat and drop every session, so a disable or reload leaves no ghost and no state. */
        public void stop() {
            seats.removeAll();
            sessions.clear();
        }
    }
}

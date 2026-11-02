package com.uxplima.uxmessentials.commandcontrol.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.ChannelHideConnectionListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.ChannelHideListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandNormalizationListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandSpamListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandVisibilityListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSources;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.ReflectiveChannelRegistrationRewriter;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the command-control context's listeners over the injected kernel ports and the module's scoped config.
 * The world-scoped rule set and hide policy, the language words and the command-spam rate limiter live in one
 * {@link CommandControlState} that {@code /uxmess reload} swaps whole, so an edited list applies without a restart; the
 * group source is chosen by probing the server for LuckPerms (an empty fallback otherwise). The listeners
 * contributed:
 *
 * <ul>
 *   <li>{@link CommandNormalizationListener} - lowercases the base command label first ({@code auto-lowercase-base-commands});
 *   <li>{@link CommandSpamListener} - rate-limits commands per player when {@code command-spam.enabled};
 *   <li>{@link CommandGateListener} - blocks a disallowed command's execution, per the player's current world;
 *   <li>{@link CommandVisibilityListener} - keeps disallowed / hidden commands out of the client command list, tab
 *       completion, and the scrub-help output, recomputing on a world change;
 *   <li>{@link ChannelHideConnectionListener} - when {@code plugin-channel-hide.enabled}, splices the packet
 *       interceptor that strips non-allowed plugin-messaging channels from the client's channel-registration list.
 * </ul>
 *
 * <p>The listeners are always registered and check the live switches on each event; only the channel hider is wired
 * from the config read at start, because a client is told its channels once, as it connects. The context persists
 * nothing and holds no runtime state that must drain on stop; unregistering the listeners is enough.
 */
@NullMarked
public final class CommandControlWiring {

    /** The node that exempts a holder from the command gate: always allowed through, mirroring the other bypasses. */
    public static final String BYPASS_PERMISSION = "uxmessentials.commandcontrol.bypass";

    /** The node that reveals the plugin-listing / help commands the plugin-hide otherwise removes and blocks. */
    public static final String VIEW_PERMISSION = "uxmessentials.commandcontrol.viewplugins";

    /** The node that exempts a holder from the command-spam rate limiter (never counted, never actioned). */
    public static final String SPAM_BYPASS_PERMISSION = "uxmessentials.commandcontrol.spam.bypass";

    /** The node that exempts a holder from the plugin-channel hider (their channel list is never stripped). */
    public static final String CHANNELHIDE_BYPASS_PERMISSION = "uxmessentials.commandcontrol.channelhide.bypass";

    private CommandControlWiring() {}

    /** Build the command-control listeners from {@code server} and {@code ctx}, ready to register. */
    public static Wired wire(Server server, ModuleContext ctx) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        CommandControlConfig config = CommandControlConfig.from(ctx.config());
        CommandControlState state = CommandControlState.of(config);
        PlayerGroupSource groups = PlayerGroupSources.create(server);
        CommandPermissionView permissions = CommandPermissionView.backedBy(server.getCommandMap());

        // Every listener is registered whatever the config says and reads the live state on each event, so a reload
        // that switches a feature on or off is honoured without registering anything new.
        List<Listener> listeners = new ArrayList<>();
        listeners.add(new CommandNormalizationListener(state));
        listeners.add(new CommandSpamListener(
                () -> state.current().rateLimiter(), SPAM_BYPASS_PERMISSION, kernel.messages(), kernel.messageSink()));
        listeners.add(new CommandGateListener(state, groups, kernel.messages(), kernel.messageSink()));
        listeners.add(new CommandVisibilityListener(
                state,
                groups,
                permissions,
                BYPASS_PERMISSION,
                kernel.messages(),
                kernel.messageSink(),
                HudText::languageOf));
        // The channel list is advertised once, as a player connects, so the hider is the one part a reload cannot
        // change for anybody already online; it is wired from the config read at start.
        ChannelHidePolicy channelHide = config.toChannelHidePolicy();
        if (channelHide.isEnabled()) {
            listeners.add(channelHideConnection(channelHide, kernel));
        }
        Runnable reload = () -> {
            state.replace(CommandControlConfig.from(ctx.config()));
            resendCommandLists(server, kernel.scheduler());
        };
        return new Wired(List.copyOf(listeners), state, groups, reload);
    }

    /**
     * Send every online player their command list again, so a reload that changed what a player may see reaches the
     * tab list they already have. The resend for each player runs on that player's own thread.
     */
    private static void resendCommandLists(Server server, Scheduler scheduler) {
        scheduler.onGlobal(() -> {
            for (Player player : server.getOnlinePlayers()) {
                scheduler.onEntity(BukkitRefs.toRef(player), player::updateCommands);
            }
        });
    }

    /**
     * Everything the command-control module contributes once wired. The listeners are what gets registered; the rest
     * is what the published check needs to answer with the rules that are actually in force, which is why the same
     * state is handed out here rather than rebuilt from config a second time.
     *
     * @param listeners the gate, visibility, normalisation, spam and channel-hide listeners to register
     * @param state the rules in force, swapped whole on reload
     * @param groups the primary-group source the rules read, LuckPerms-backed when it is installed
     * @param reload re-reads the module's config into {@code state} and resends every online player's command list
     */
    public record Wired(
            List<Listener> listeners, CommandControlState state, PlayerGroupSource groups, Runnable reload) {

        public Wired {
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(groups, "groups");
            Objects.requireNonNull(reload, "reload");
        }
    }

    /**
     * Build the plugin-channel hider: a namespaced packet pipeline whose single shared listener strips non-allowed
     * channels from the outbound register/unregister advertisement, and the join listener that splices it per
     * connection. Mirrors the tablist-suppression pipeline wiring so it never collides with the nametag/npc/tablist
     * packet stacks, and listener faults are logged off the I/O thread through the kernel log.
     */
    private static Listener channelHideConnection(ChannelHidePolicy policy, KernelPorts kernel) {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        PacketPipeline pipeline = new PacketPipeline(
                new ChannelResolver(),
                registry,
                "uxmessentials:commandcontrol-channelhide",
                fault -> kernel.log().error("commandcontrol channel-hide listener fault", fault));
        registry.register(
                new ChannelHideListener(new ReflectiveChannelRegistrationRewriter(kernel.log()), policy, kernel.log()));
        return new ChannelHideConnectionListener(pipeline, CHANNELHIDE_BYPASS_PERMISSION);
    }
}

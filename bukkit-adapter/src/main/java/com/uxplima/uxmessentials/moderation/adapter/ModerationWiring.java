package com.uxplima.uxmessentials.moderation.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.inbound.command.ModerationCommands;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.FreezeMoveListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.ModerationJoinListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.ModerationLoginListener;
import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.MutedCommandListener;
import com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitSanctions;
import com.uxplima.uxmessentials.moderation.adapter.outbound.BukkitTargetResolver;
import com.uxplima.uxmessentials.moderation.adapter.outbound.ConfigJailDirectory;
import com.uxplima.uxmessentials.moderation.adapter.outbound.LoggingModerationAudit;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.ClearWarns;
import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.JailCountdown;
import com.uxplima.uxmessentials.moderation.application.Kick;
import com.uxplima.uxmessentials.moderation.application.KickAll;
import com.uxplima.uxmessentials.moderation.application.ListBans;
import com.uxplima.uxmessentials.moderation.application.ListJailed;
import com.uxplima.uxmessentials.moderation.application.ListJails;
import com.uxplima.uxmessentials.moderation.application.ListMutes;
import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.ModerationNotifier;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.MutedCommandPolicy;
import com.uxplima.uxmessentials.moderation.application.RepositoryJailGate;
import com.uxplima.uxmessentials.moderation.application.RepositoryMutePolicy;
import com.uxplima.uxmessentials.moderation.application.ReviewWarns;
import com.uxplima.uxmessentials.moderation.application.Seen;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.TempBanIp;
import com.uxplima.uxmessentials.moderation.application.TempWarn;
import com.uxplima.uxmessentials.moderation.application.Unban;
import com.uxplima.uxmessentials.moderation.application.UnbanIp;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.Unmute;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.persistence.moderation.ModerationStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/**
 * Constructs the moderation context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces everything the plugin must register: the Brigadier command list and the
 * login/join/freeze listeners. This is the one place the moderation context is wired — nothing else news up
 * its classes.
 *
 * <p>The audit trail goes to the dedicated {@code com.uxplima.uxmessentials.audit} SLF4J channel (not the
 * plugin log), so an operator routes it to a retained file per docs/09-deployment. The two cross-context
 * gates moderation <em>provides</em> are bound here onto the rebindable holders the messaging
 * ({@code MutableMutePolicy}) and teleport ({@code MutableJailGate}) contexts already hold: a muted player
 * stops being able to {@code /msg} and a jailed player stops being able to {@code /home}/{@code /tpa} the
 * moment this module wires. When moderation is disabled this wiring never runs, so both holders stay on their
 * {@code NEVER} default and the other contexts degrade gracefully.
 */
@NullMarked
public final class ModerationWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private ModerationWiring() {}

    /** Build the moderation adapters and use cases from {@code ctx}, the {@code persistence} DSL, and the gate sinks. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, GateSinks gates) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(gates, "gates");
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        ModerationSettings settings = new ModerationSettings(ctx.config());
        ModerationRepository repository = ModerationStores.repository(persistence);
        BukkitSanctions sanctions = new BukkitSanctions(plugin.getServer(), kernel.scheduler(), settings);
        ModerationGuard guard = new ModerationGuard(kernel.permissions());
        ModerationServices services = assemble(plugin, kernel, settings, repository, sanctions, guard, clock);
        RepositoryMutePolicy mutePolicy = new RepositoryMutePolicy(repository, clock);
        RepositoryJailGate jailGate = new RepositoryJailGate(repository, clock);
        gates.bindMute(mutePolicy);
        gates.bindJail(jailGate);
        return new Wired(
                ModerationCommands.all(services, kernel.messages(), kernel.messageSink(), kernel.scheduler()),
                listeners(services, sanctions, repository, kernel, settings, guard, clock),
                sanctions,
                mutePolicy,
                jailGate);
    }

    private static ModerationServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            ModerationSettings settings,
            ModerationRepository repository,
            BukkitSanctions sanctions,
            ModerationGuard guard,
            Clock clock) {
        ModerationNotifier notifier = new ModerationNotifier(kernel.messages(), kernel.messageSink());
        ModerationAudit audit = new LoggingModerationAudit(auditLogger());
        ConfigJailDirectory jails = new ConfigJailDirectory(settings);
        Sanctions sanctionPort = sanctions;
        return new ModerationServices.Builder()
                .mute(new Mute(repository, guard, notifier, audit, kernel.events(), clock))
                .unmute(new Unmute(repository, notifier, audit, kernel.events(), clock))
                .jail(new Jail(repository, jails, sanctionPort, guard, notifier, audit, kernel.events(), clock))
                .unjail(new Unjail(repository, sanctionPort, notifier, audit, kernel.events(), clock))
                .tempBan(new TempBan(repository, sanctionPort, guard, notifier, audit, kernel.events(), clock))
                .ban(new Ban(repository, sanctionPort, guard, notifier, audit, kernel.events(), clock))
                .unban(new Unban(repository, notifier, audit))
                .kick(new Kick(sanctionPort, guard, notifier, audit))
                .kickAll(new KickAll(sanctionPort, guard, notifier, audit))
                .warn(new IssueWarn(repository, guard, notifier, audit, kernel.events(), clock))
                .tempWarn(new TempWarn(repository, guard, notifier, audit, kernel.events(), clock))
                .reviewWarns(new ReviewWarns(repository, notifier, clock))
                .clearWarns(new ClearWarns(repository, notifier, audit))
                .listJails(new ListJails(jails, notifier))
                .listJailed(new ListJailed(repository, kernel.playerLookup(), notifier, clock))
                .listBans(new ListBans(repository, kernel.playerLookup(), notifier, clock))
                .listMutes(new ListMutes(repository, kernel.playerLookup(), notifier, clock))
                .banIp(new BanIp(repository, notifier, audit, kernel.events(), clock))
                .tempBanIp(new TempBanIp(repository, notifier, audit, kernel.events(), clock))
                .unbanIp(new UnbanIp(repository, notifier, audit))
                .freeze(new Freeze(sanctionPort, guard, notifier, audit))
                .seen(new Seen(repository, kernel.playerLookup(), notifier, clock))
                .jailCountdown(new JailCountdown(repository, sanctionPort, audit, kernel.events(), clock))
                .loginEnforcement(new LoginEnforcement(repository, notifier, audit, clock))
                .repository(repository)
                .players(kernel.playerLookup())
                .targets(new BukkitTargetResolver(plugin.getServer()))
                .build();
    }

    private static List<Listener> listeners(
            ModerationServices services,
            BukkitSanctions sanctions,
            ModerationRepository repository,
            KernelPorts kernel,
            ModerationSettings settings,
            ModerationGuard guard,
            Clock clock) {
        MutedCommandPolicy mutedCommands = new MutedCommandPolicy(settings.mutedBlockedCommands());
        return List.of(
                new ModerationLoginListener(services.loginEnforcement()),
                new ModerationJoinListener(services.jailCountdown(), repository, clock),
                new FreezeMoveListener(sanctions),
                new MutedCommandListener(
                        repository, mutedCommands, guard, kernel.messages(), kernel.messageSink(), clock));
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * The cross-context gate sinks moderation rebinds when it wires: the messaging mute holder and the
     * teleport jail holder. Passing them as a narrow callback pair keeps {@code ModerationWiring} from
     * importing the other contexts' adapter types directly — bootstrap supplies the binders.
     *
     * @param bindMute rebinds the messaging mute gate to the supplied policy
     * @param bindJail rebinds the teleport jail gate to the supplied gate
     */
    public record GateSinks(
            java.util.function.Consumer<com.uxplima.uxmessentials.messaging.application.port.MutePolicy> bindMute,
            java.util.function.Consumer<com.uxplima.uxmessentials.teleport.application.port.JailGate> bindJail) {

        public GateSinks {
            Objects.requireNonNull(bindMute, "bindMute");
            Objects.requireNonNull(bindJail, "bindJail");
        }

        void bindMute(com.uxplima.uxmessentials.messaging.application.port.MutePolicy policy) {
            bindMute.accept(policy);
        }

        void bindJail(com.uxplima.uxmessentials.teleport.application.port.JailGate gate) {
            bindJail.accept(gate);
        }
    }

    /**
     * Everything the moderation module contributes once wired: the Brigadier commands, the
     * login/join/freeze listeners, and the {@link BukkitSanctions} adapter (held so stop drains its freeze
     * set).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the login/join/freeze listeners to register
     * @param sanctions the live-player sanction adapter, for the stop-time freeze drain
     * @param mutePolicy the mute read side the {@code muted} placeholder queries
     * @param jailGate the jail read side the {@code jailed} placeholder queries
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            BukkitSanctions sanctions,
            RepositoryMutePolicy mutePolicy,
            RepositoryJailGate jailGate) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(sanctions, "sanctions");
            Objects.requireNonNull(mutePolicy, "mutePolicy");
            Objects.requireNonNull(jailGate, "jailGate");
        }

        /** Drop the session-scoped freeze set. Called on module stop. */
        public void stop() {
            sanctions.clear();
        }
    }
}

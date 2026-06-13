package com.uxplima.uxmessentials.vote.adapter;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vote.VoteRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VoteCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VotePartyCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.gui.VoteSitesGuiView;
import com.uxplima.uxmessentials.vote.adapter.inbound.listener.VoteJoinListener;
import com.uxplima.uxmessentials.vote.adapter.inbound.listener.VotifierListener;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitRewardApplier;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitRewardDispatcher;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteAudience;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteBroadcaster;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteContext;
import com.uxplima.uxmessentials.vote.adapter.outbound.InMemoryBroadcastThrottle;
import com.uxplima.uxmessentials.vote.adapter.outbound.PdcBroadcastVisibility;
import com.uxplima.uxmessentials.vote.adapter.outbound.PdcReminderPreferences;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.application.ForceParty;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.ResetVoterTotals;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.application.SetPartyCount;
import com.uxplima.uxmessentials.vote.application.ShowLastVote;
import com.uxplima.uxmessentials.vote.application.ShowNextVote;
import com.uxplima.uxmessentials.vote.application.ShowVoteStreak;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the vote context's adapters and use cases over the injected kernel ports, the persistence DSL,
 * and the operator config under {@code modules/vote/config.conf}, and produces everything the plugin must
 * register: the {@code /vote} and {@code /voteparty} Brigadier commands, the join handler that pays out an
 * offline voter's queued rewards (and optionally sends a login reminder), and the reflective Votifier vote
 * listener. This is the one place the vote context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a thin party-counter cache (write-through at the database).
 * The reward engine resolves the structured {@code rewards} catalog (per-vote / per-site / first-vote /
 * milestone specs) parsed from the module config by {@link RewardCatalogLoader}; the {@link BukkitRewardApplier}
 * applies each resolved grant — console commands, MiniMessage messages and broadcasts, and item grants for an
 * online voter, queued commands for an offline one — and the {@link BukkitVoteContext} supplies the world,
 * permission, online, and chance-roll seams the engine reads. The party reward is a full {@link RewardSpec}
 * with configurable threshold, escalation, reset schedule, and mid-run announcements. The
 * {@link BukkitRewardDispatcher} is kept for the offline-drain path ({@code ApplyQueuedRewards}).
 * The Votifier listener self-registers behind a plugin-present guard in {@link Wired#startBackgroundWork()} and
 * is dropped in {@link Wired#stop()}, so the module runs unchanged whether or not Votifier is installed.
 * A subscriber on the in-process event bus plays the configured sound and particle on every online player's
 * entity thread when {@link VotePartyTriggered} fires. When reminders are enabled a repeating global task
 * pings eligible opted-in players on a configurable interval; the task handle is cancelled on stop.
 */
@NullMarked
public final class VoteWiring {

    private static final int DEFAULT_THRESHOLD = 25;

    private VoteWiring() {}

    /**
     * Build the vote adapters and use cases over the kernel ports, the persistence DSL, and the
     * in-process event bus. The event bus is the concrete {@link InProcessDomainEventPublisher} so
     * the party sound/particle subscriber can be registered and later unregistered on stop.
     */
    public static Wired wire(
            Plugin plugin, ModuleContext ctx, Persistence persistence, InProcessDomainEventPublisher events) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(events, "events");
        KernelPorts kernel = ctx.kernel();
        VoteRepository repository = VoteRepositories.cached(persistence);
        VoteNotifier notifier = new VoteNotifier(kernel.messages(), kernel.messageSink());
        BukkitRewardDispatcher dispatcher = new BukkitRewardDispatcher(kernel.scheduler());
        VoteAudience audience = new BukkitVoteAudience();
        RewardApplier applier = new BukkitRewardApplier(repository, dispatcher, kernel.scheduler(), kernel.log());
        VoteContext context = new BukkitVoteContext(kernel.permissions());
        RewardEngine engine = new RewardEngine(loadCatalog(plugin, kernel));
        PartyConfig party = partyConfig(ctx.config());
        int streakGraceDays = Math.max(0, ctx.config().getInt("streak.grace-days", 0));
        List<String> voteLinks = ctx.config().getStringList("vote-links", List.of());
        VoteSiteCatalog siteCatalog = loadSiteCatalog(plugin, kernel);
        PdcReminderPreferences reminderPrefs = new PdcReminderPreferences(plugin);

        BroadcastSettingsLoader.Loaded broadcastConfig = loadBroadcastSettings(plugin, kernel);
        BroadcastVisibility broadcastVisibility = new PdcBroadcastVisibility(plugin);
        BroadcastThrottle broadcastThrottle = new InMemoryBroadcastThrottle();
        VoteBroadcaster broadcaster = new BukkitVoteBroadcaster(
                kernel.scheduler(), kernel.messages(), broadcastVisibility, broadcastConfig.display());

        VoteSitesGuiView.GuiConfig guiCfg = loadGuiConfig(ctx.config());
        VoteSitesGuiView sitesGuiView =
                new VoteSitesGuiView(siteCatalog, repository, kernel.scheduler(), kernel.messages(), guiCfg);

        VoteServices services = assemble(
                kernel,
                repository,
                applier,
                context,
                engine,
                audience,
                notifier,
                broadcaster,
                broadcastConfig.settings(),
                broadcastThrottle,
                broadcastVisibility,
                party,
                streakGraceDays,
                voteLinks,
                siteCatalog,
                reminderPrefs,
                sitesGuiView);

        // Subscribe the party sound/particle handler to the in-process bus.
        @Nullable Sound sound = BukkitRegistryKeys.resolveSound(ctx.config().getString("voteparty.sound", ""));
        @Nullable Particle particle = BukkitRegistryKeys.resolveParticle(ctx.config().getString("voteparty.particle", ""));
        Consumer<DomainEvent> partyEffects = buildPartyEffectsHandler(sound, particle, kernel);
        events.subscribe(partyEffects);

        // Reminder interval task (only when reminders.enabled && interval-minutes > 0).
        boolean remindersEnabled = ctx.config().getBoolean("reminders.enabled", false);
        int intervalMinutes = ctx.config().getInt("reminders.interval-minutes", 0);
        @Nullable AutoCloseable reminderTask = null;
        if (remindersEnabled && intervalMinutes > 0) {
            VoteReminderEligibility eligibility = new VoteReminderEligibility(repository, siteCatalog);
            Duration period = Duration.ofMinutes(intervalMinutes);
            reminderTask = kernel.scheduler()
                    .repeatGlobal(
                            () -> sendIntervalReminders(kernel.scheduler(), eligibility, reminderPrefs, notifier),
                            period,
                            period);
        }

        // Build the join listener with reminder support when enabled.
        boolean loginNag = ctx.config().getBoolean("reminders.login", true);
        int loginDelaySecs = Math.max(0, ctx.config().getInt("reminders.login-delay-seconds", 5));
        VoteJoinListener joinListener;
        if (remindersEnabled && loginNag) {
            VoteReminderEligibility eligibility = new VoteReminderEligibility(repository, siteCatalog);
            joinListener = new VoteJoinListener(
                    services.applyQueuedRewards(),
                    repository,
                    kernel.scheduler(),
                    true,
                    Duration.ofSeconds(loginDelaySecs),
                    eligibility,
                    reminderPrefs,
                    notifier);
        } else {
            joinListener = new VoteJoinListener(
                    services.applyQueuedRewards(),
                    repository,
                    kernel.scheduler(),
                    false,
                    Duration.ZERO,
                    null,
                    null,
                    null);
        }

        VotifierListener votifier = new VotifierListener(plugin, services, kernel.playerLookup(), kernel.log());
        List<CommandRegistration> commands = List.of(
                new VoteCommand(services, () -> ListDisplayMode.from(ctx.config())), new VotePartyCommand(services));
        List<Listener> listeners = List.of(votifier, joinListener);
        return new Wired(
                commands, listeners, votifier, repository, party.baseThreshold(), events, partyEffects, reminderTask);
    }

    private static void sendIntervalReminders(
            Scheduler scheduler,
            VoteReminderEligibility eligibility,
            PdcReminderPreferences reminderPrefs,
            VoteNotifier notifier) {
        // This body runs on the global region tick. Only collect online player refs here — the PDC read
        // (entity thread) and the DB eligibility check (off-tick) must not happen on the global tick.
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerRef who = new PlayerRef(online.getUniqueId(), online.getName());
            remindIfEligible(scheduler, eligibility, reminderPrefs, notifier, who);
        }
    }

    /**
     * The shared async→onEntity reminder dance used by both the interval task and the login nag: the DB
     * eligibility check runs off-tick, then the PDC opt-in read and the message send happen on the
     * player's entity thread, guarded so a player who logged off in between is silently skipped.
     */
    private static void remindIfEligible(
            Scheduler scheduler,
            VoteReminderEligibility eligibility,
            PdcReminderPreferences reminderPrefs,
            VoteNotifier notifier,
            PlayerRef who) {
        scheduler.async(() -> {
            if (!eligibility.canVoteSomewhere(who, java.time.Instant.now())) {
                return;
            }
            scheduler.onEntity(who, () -> {
                if (Bukkit.getPlayer(who.uuid()) == null) {
                    return; // logged off between the eligibility check and the entity hop
                }
                if (reminderPrefs.wantsReminders(who)) {
                    notifier.send(who, VoteMessageKey.VOTE_REMINDER);
                }
            });
        });
    }

    private static VoteServices assemble(
            KernelPorts kernel,
            VoteRepository repository,
            RewardApplier applier,
            VoteContext context,
            RewardEngine engine,
            VoteAudience audience,
            VoteNotifier notifier,
            VoteBroadcaster broadcaster,
            BroadcastSettings broadcastSettings,
            BroadcastThrottle broadcastThrottle,
            BroadcastVisibility broadcastVisibility,
            PartyConfig party,
            int streakGraceDays,
            List<String> voteLinks,
            VoteSiteCatalog siteCatalog,
            PdcReminderPreferences reminderPrefs,
            VoteSitesGuiView sitesGuiView) {
        Set<BroadcastChannel> channels = broadcastSettings.channels();
        HandleVote handleVote = new HandleVote(
                repository,
                engine,
                applier,
                context,
                audience,
                broadcastSettings,
                broadcaster,
                broadcastThrottle,
                kernel.events(),
                party,
                streakGraceDays,
                ZoneId.systemDefault());
        ApplyQueuedRewards applyQueuedRewards =
                new ApplyQueuedRewards(repository, new BukkitRewardDispatcher(kernel.scheduler()));
        VoteLinks links = new VoteLinks(voteLinks, notifier);
        VotePartyStatus status = new VotePartyStatus(repository, notifier, party.baseThreshold());
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        ShowVoteStreak showVoteStreak = new ShowVoteStreak(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        ShowNextVote showNextVote = new ShowNextVote(repository, siteCatalog, notifier);
        ShowLastVote showLastVote = new ShowLastVote(repository, siteCatalog, notifier);
        VoteReminderEligibility reminderEligibility = new VoteReminderEligibility(repository, siteCatalog);
        ForceParty forceParty =
                new ForceParty(repository, applier, audience, notifier, broadcaster, channels, kernel.events(), party);
        SetPartyCount setPartyCount = new SetPartyCount(repository, notifier);
        AddPartyCount addPartyCount = new AddPartyCount(
                repository, applier, audience, notifier, broadcaster, channels, kernel.events(), party);
        GiveVote giveVote = new GiveVote(handleVote, notifier);
        ResetVoterTotals resetVoterTotals = new ResetVoterTotals(repository, notifier);
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                links,
                sitesGuiView,
                status,
                showVoteTotals,
                showVoteStreak,
                topVoters,
                showNextVote,
                showLastVote,
                reminderEligibility,
                reminderPrefs,
                broadcastVisibility,
                forceParty,
                setPartyCount,
                addPartyCount,
                giveVote,
                resetVoterTotals,
                kernel.playerLookup(),
                kernel.scheduler(),
                kernel.messages());
    }

    private static RewardCatalog loadCatalog(Plugin plugin, KernelPorts kernel) {
        Path moduleConfig = moduleConfigPath(plugin);
        return RewardCatalogLoader.loadFrom(moduleConfig, kernel.log());
    }

    private static VoteSiteCatalog loadSiteCatalog(Plugin plugin, KernelPorts kernel) {
        Path moduleConfig = moduleConfigPath(plugin);
        return VoteSiteCatalogLoader.loadFrom(moduleConfig, kernel.log());
    }

    private static BroadcastSettingsLoader.Loaded loadBroadcastSettings(Plugin plugin, KernelPorts kernel) {
        Path moduleConfig = moduleConfigPath(plugin);
        return BroadcastSettingsLoader.loadFrom(moduleConfig, kernel.log());
    }

    private static Path moduleConfigPath(Plugin plugin) {
        return plugin.getDataFolder()
                .toPath()
                .resolve("modules")
                .resolve("vote")
                .resolve("config.conf");
    }

    private static VoteSitesGuiView.GuiConfig loadGuiConfig(ConfigStore config) {
        boolean enabled = !"chat"
                .equalsIgnoreCase(config.getString("gui.list-display", "gui").strip());
        int rows = Math.max(1, Math.min(6, config.getInt("gui.rows", 3)));
        Material votable = VoteSitesGuiView.GuiConfig.parseMaterial(
                config.getString("gui.votable-material", "PAPER"), Material.PAPER);
        Material cooldown = VoteSitesGuiView.GuiConfig.parseMaterial(
                config.getString("gui.cooldown-material", "CLOCK"), Material.CLOCK);
        return new VoteSitesGuiView.GuiConfig(enabled, rows, votable, cooldown);
    }

    /**
     * Parse the {@code voteparty} block into a {@link PartyConfig}. The {@code reward} sub-node is
     * parsed as a single {@link RewardSpec} using the same node parser as {@link RewardCatalogLoader}.
     * If absent or malformed, a no-op (empty commands, 100 % chance) spec is used.
     */
    private static PartyConfig partyConfig(ConfigStore config) {
        RewardSpec reward = loadPartyRewardSpec(config);
        int threshold = Math.max(1, config.getInt("voteparty.threshold", DEFAULT_THRESHOLD));
        boolean onlyVoters = config.getBoolean("voteparty.only-voters", false);
        int escalateBy = Math.max(0, config.getInt("voteparty.escalate-by", 0));
        PartyResetSchedule resetSchedule = parseResetSchedule(config.getString("voteparty.reset", "none"));
        List<Integer> announceAt = parseAnnounceAt(config.getStringList("voteparty.announce-at", List.of()));
        return new PartyConfig(reward, threshold, onlyVoters, escalateBy, resetSchedule, announceAt);
    }

    private static RewardSpec loadPartyRewardSpec(ConfigStore config) {
        List<String> legacyCommands = config.getStringList("voteparty.rewards", List.of());
        if (legacyCommands.isEmpty()) {
            return new RewardSpec(
                    100, java.util.Optional.empty(), List.of(), List.of(), List.of(), List.of(), java.util.Set.of());
        }
        return new RewardSpec(
                100, java.util.Optional.empty(), legacyCommands, List.of(), List.of(), List.of(), java.util.Set.of());
    }

    private static PartyResetSchedule parseResetSchedule(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT).strip()) {
            case "daily" -> PartyResetSchedule.DAILY;
            case "weekly" -> PartyResetSchedule.WEEKLY;
            default -> PartyResetSchedule.NONE;
        };
    }

    private static List<Integer> parseAnnounceAt(List<String> raw) {
        List<Integer> result = new java.util.ArrayList<>();
        for (String s : raw) {
            try {
                result.add(Integer.parseInt(s.strip()));
            } catch (NumberFormatException ignored) {
                // tolerate malformed entries
            }
        }
        return List.copyOf(result);
    }

    /**
     * Build the domain-event consumer that plays the party sound and particle to every online player
     * when {@link VotePartyTriggered} fires. Both effects are optional — a blank or unresolvable
     * config name simply skips that effect without logging so a default-unconfigured server is silent.
     */
    private static Consumer<DomainEvent> buildPartyEffectsHandler(
            @Nullable Sound sound, @Nullable Particle particle, KernelPorts kernel) {
        return event -> {
            if (!(event instanceof VotePartyTriggered)) {
                return;
            }
            if (sound == null && particle == null) {
                return;
            }
            // VotePartyTriggered is published off-tick from the vote handler, so enumerate the live online
            // view on the global region thread before hopping per-recipient.
            kernel.scheduler().onGlobal(() -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    Sound s = sound;
                    Particle p = particle;
                    kernel.scheduler()
                            .onEntity(
                                    new com.uxplima.uxmessentials.shared.domain.PlayerRef(
                                            online.getUniqueId(), online.getName()),
                                    () -> {
                                        @Nullable Location loc = online.getLocation();
                                        if (loc == null) {
                                            return;
                                        }
                                        if (s != null) {
                                            online.playSound(loc, s, 1.0f, 1.0f);
                                        }
                                        if (p != null) {
                                            online.spawnParticle(p, loc, 30, 0.5, 0.5, 0.5, 0.1);
                                        }
                                    });
                }
            });
        };
    }

    /**
     * Everything the vote module contributes once wired: the Brigadier commands, the join + Votifier
     * listeners, the Votifier listener handle so {@link #startBackgroundWork()} can self-register it and
     * {@link #stop()} can drop it, and the repository + party threshold so bootstrap can wire the
     * placeholder seam. The {@code reminderTask} is non-null only when {@code reminders.enabled = true}
     * and {@code interval-minutes > 0}; it is closed on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join + Votifier listeners to register
     * @param votifier the Votifier listener, self-registered on start and dropped on stop
     * @param repository the jOOQ vote repository, exposed for the placeholder seam
     * @param partyThreshold the configured party threshold, exposed for the placeholder seam
     * @param eventBus the in-process event bus the party effects consumer is registered on
     * @param partyEffects the sound/particle consumer to unregister on stop
     * @param reminderTask the repeating reminder task handle (may be null when interval reminders are off)
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            VotifierListener votifier,
            VoteRepository repository,
            int partyThreshold,
            InProcessDomainEventPublisher eventBus,
            Consumer<DomainEvent> partyEffects,
            @Nullable AutoCloseable reminderTask) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(votifier, "votifier");
            Objects.requireNonNull(repository, "repository");
            if (partyThreshold < 1) {
                throw new IllegalArgumentException("partyThreshold must be at least one: " + partyThreshold);
            }
            Objects.requireNonNull(eventBus, "eventBus");
            Objects.requireNonNull(partyEffects, "partyEffects");
        }

        /** Self-register the reflective Votifier handler behind its plugin-present guard. */
        public void startBackgroundWork() {
            votifier.registerIfPresent();
        }

        /** Drop the Votifier handler, the party effects subscriber, and the reminder task on disable/reload. */
        public void stop() {
            votifier.unregister();
            eventBus.unsubscribe(partyEffects);
            if (reminderTask != null) {
                try {
                    reminderTask.close();
                } catch (Exception ignored) {
                    // ScheduledTask.cancel() does not throw; AutoCloseable.close() may declare it
                }
            }
        }
    }
}

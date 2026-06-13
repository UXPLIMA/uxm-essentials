package com.uxplima.uxmessentials.vote.adapter;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vote.VoteRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VoteCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VotePartyCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.listener.VoteJoinListener;
import com.uxplima.uxmessentials.vote.adapter.inbound.listener.VotifierListener;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitRewardApplier;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitRewardDispatcher;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteAudience;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteContext;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.HandleVote.PartyReward;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the vote context's adapters and use cases over the injected kernel ports, the persistence DSL,
 * and the operator config under {@code modules/vote/config.conf}, and produces everything the plugin must
 * register: the {@code /vote} and {@code /voteparty} Brigadier commands, the join handler that pays out an
 * offline voter's queued rewards, and the reflective Votifier vote listener. This is the one place the vote
 * context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a thin party-counter cache (write-through at the database).
 * The reward engine resolves the structured {@code rewards} catalog (per-vote / per-site / first-vote /
 * milestone specs) parsed from the module config by {@link RewardCatalogLoader}; the {@link BukkitRewardApplier}
 * applies each resolved grant — console commands, MiniMessage messages and broadcasts, and item grants for an
 * online voter, queued commands for an offline one — and the {@link BukkitVoteContext} supplies the world,
 * permission, online, and chance-roll seams the engine reads. The party reward stays a flat command list with
 * its threshold. The {@link BukkitRewardDispatcher} is kept for the offline-drain path ({@code ApplyQueuedRewards}).
 * The Votifier listener self-registers behind a plugin-present guard in {@link Wired#startBackgroundWork()} and
 * is dropped in {@link Wired#stop()}, so the module runs unchanged whether or not Votifier is installed.
 */
@NullMarked
public final class VoteWiring {

    private static final int DEFAULT_THRESHOLD = 25;

    private VoteWiring() {}

    /** Build the vote adapters and use cases over the kernel ports and the persistence DSL. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        KernelPorts kernel = ctx.kernel();
        VoteRepository repository = VoteRepositories.cached(persistence);
        VoteNotifier notifier = new VoteNotifier(kernel.messages(), kernel.messageSink());
        BukkitRewardDispatcher dispatcher = new BukkitRewardDispatcher(kernel.scheduler());
        VoteAudience audience = new BukkitVoteAudience();
        RewardApplier applier = new BukkitRewardApplier(repository, dispatcher, kernel.scheduler(), kernel.log());
        VoteContext context = new BukkitVoteContext(kernel.permissions());
        RewardEngine engine = new RewardEngine(loadCatalog(plugin, kernel));
        PartyReward party = party(ctx.config());
        List<String> voteLinks = ctx.config().getStringList("vote-links", List.of());

        VoteServices services =
                assemble(kernel, repository, applier, context, engine, audience, notifier, party, voteLinks);
        VotifierListener votifier = new VotifierListener(plugin, services, kernel.playerLookup(), kernel.log());
        List<CommandRegistration> commands = List.of(new VoteCommand(services), new VotePartyCommand(services));
        List<Listener> listeners =
                List.of(votifier, new VoteJoinListener(services.applyQueuedRewards(), kernel.scheduler()));
        return new Wired(commands, listeners, votifier, repository, party.threshold());
    }

    private static VoteServices assemble(
            KernelPorts kernel,
            VoteRepository repository,
            RewardApplier applier,
            VoteContext context,
            RewardEngine engine,
            VoteAudience audience,
            VoteNotifier notifier,
            PartyReward party,
            List<String> voteLinks) {
        HandleVote handleVote = new HandleVote(
                repository,
                engine,
                applier,
                context,
                audience,
                notifier,
                kernel.events(),
                party,
                ZoneId.systemDefault());
        ApplyQueuedRewards applyQueuedRewards =
                new ApplyQueuedRewards(repository, new BukkitRewardDispatcher(kernel.scheduler()));
        VoteLinks links = new VoteLinks(voteLinks, notifier);
        VotePartyStatus status = new VotePartyStatus(repository, notifier, party.threshold());
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                links,
                status,
                showVoteTotals,
                topVoters,
                kernel.playerLookup(),
                kernel.scheduler(),
                kernel.messages());
    }

    private static RewardCatalog loadCatalog(Plugin plugin, KernelPorts kernel) {
        Path moduleConfig = plugin.getDataFolder()
                .toPath()
                .resolve("modules")
                .resolve("vote")
                .resolve("config.conf");
        return RewardCatalogLoader.loadFrom(moduleConfig, kernel.log());
    }

    private static PartyReward party(ConfigStore config) {
        List<String> commands = config.getStringList("voteparty.rewards", List.of());
        int threshold = Math.max(1, config.getInt("voteparty.threshold", DEFAULT_THRESHOLD));
        return new PartyReward(commands, threshold);
    }

    /**
     * Everything the vote module contributes once wired: the Brigadier commands, the join + Votifier listeners,
     * the Votifier listener handle so {@link #startBackgroundWork()} can self-register it and {@link #stop()}
     * can drop it, and the repository + party threshold so bootstrap can wire the placeholder seam.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join + Votifier listeners to register
     * @param votifier the Votifier listener, self-registered on start and dropped on stop
     * @param repository the jOOQ vote repository, exposed for the placeholder seam
     * @param partyThreshold the configured party threshold, exposed for the placeholder seam
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            VotifierListener votifier,
            VoteRepository repository,
            int partyThreshold) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(votifier, "votifier");
            Objects.requireNonNull(repository, "repository");
            if (partyThreshold < 1) {
                throw new IllegalArgumentException("partyThreshold must be at least one: " + partyThreshold);
            }
        }

        /** Self-register the reflective Votifier handler behind its plugin-present guard. */
        public void startBackgroundWork() {
            votifier.registerIfPresent();
        }

        /** Drop the dynamic Votifier handler so a disable/reload leaves no orphaned registration. */
        public void stop() {
            votifier.unregister();
        }
    }
}

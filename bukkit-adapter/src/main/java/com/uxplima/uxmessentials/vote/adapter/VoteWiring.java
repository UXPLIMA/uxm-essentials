package com.uxplima.uxmessentials.vote.adapter;

import java.time.Clock;
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
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitRewardDispatcher;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteAudience;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.HandleVote.VoteRewards;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the vote context's adapters and use cases over the injected kernel ports, the persistence DSL,
 * and the operator config under {@code modules/vote/config.conf}, and produces everything the plugin must
 * register: the {@code /vote} and {@code /voteparty} Brigadier commands, the join handler that pays out an
 * offline voter's queued rewards, and the reflective Votifier vote listener. This is the one place the vote
 * context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a thin party-counter cache (write-through at the database).
 * The reward dispatcher runs configured console commands on the global region thread with the
 * {@code {player}} substitution; the audience snapshots the online players for the party rewards and the
 * thank-you broadcast. The reward and vote-link lists are operator config content (empty out of the box, so
 * the module ships inert). The Votifier listener self-registers behind a plugin-present guard in
 * {@link Wired#startBackgroundWork()} and is dropped in {@link Wired#stop()}, so the module runs unchanged
 * whether or not Votifier is installed.
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
        RewardDispatcher dispatcher = new BukkitRewardDispatcher(kernel.scheduler());
        VoteAudience audience = new BukkitVoteAudience();
        VoteRewards rewards = rewards(ctx.config());
        List<String> voteLinks = ctx.config().getStringList("vote-links", List.of());

        VoteServices services = assemble(kernel, repository, dispatcher, audience, notifier, rewards, voteLinks);
        VotifierListener votifier = new VotifierListener(plugin, services, kernel.playerLookup(), kernel.log());
        List<CommandRegistration> commands = List.of(new VoteCommand(services), new VotePartyCommand(services));
        List<Listener> listeners =
                List.of(votifier, new VoteJoinListener(services.applyQueuedRewards(), kernel.scheduler()));
        return new Wired(commands, listeners, votifier);
    }

    private static VoteServices assemble(
            KernelPorts kernel,
            VoteRepository repository,
            RewardDispatcher dispatcher,
            VoteAudience audience,
            VoteNotifier notifier,
            VoteRewards rewards,
            List<String> voteLinks) {
        HandleVote handleVote = new HandleVote(
                repository,
                dispatcher,
                audience,
                notifier,
                kernel.events(),
                rewards,
                kernel.playerLookup(),
                Clock.systemUTC(),
                ZoneId.systemDefault());
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, dispatcher);
        VoteLinks links = new VoteLinks(voteLinks, notifier);
        VotePartyStatus status = new VotePartyStatus(repository, notifier, rewards.partyThreshold());
        return new VoteServices(handleVote, applyQueuedRewards, links, status, kernel.scheduler(), kernel.messages());
    }

    private static VoteRewards rewards(ConfigStore config) {
        List<String> perVote = config.getStringList("rewards", List.of());
        List<String> party = config.getStringList("voteparty.rewards", List.of());
        int threshold = Math.max(1, config.getInt("voteparty.threshold", DEFAULT_THRESHOLD));
        return new VoteRewards(perVote, party, threshold);
    }

    /**
     * Everything the vote module contributes once wired: the Brigadier commands, the join + Votifier listeners,
     * and the Votifier listener handle so {@link #startBackgroundWork()} can self-register it and {@link #stop()}
     * can drop it. The context holds no repeating scheduled work; the only teardown is unregistering the dynamic
     * Votifier handler so a disable/reload leaves no orphaned registration.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join + Votifier listeners to register
     * @param votifier the Votifier listener, self-registered on start and dropped on stop
     */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners, VotifierListener votifier) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(votifier, "votifier");
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

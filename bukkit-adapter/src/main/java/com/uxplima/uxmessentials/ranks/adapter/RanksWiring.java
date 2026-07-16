package com.uxplima.uxmessentials.ranks.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.playerstate.PlaytimeRepositories;
import com.uxplima.uxmessentials.persistence.ranks.PlayerRankRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.RanksCommand;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.RankupCommand;
import com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankActionRunner;
import com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.RankLadders;
import com.uxplima.uxmessentials.ranks.application.RanksConfig;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BlockedCommands;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.FilteredClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the ranks context's use cases over the injected kernel ports and the shared persistence DSL. Phase 2
 * builds the rankup pipeline: the parsed ladder, the DB-backed {@link PlayerRankRepository}, the
 * {@link CurrentRank} read, the {@link RankRequirementEvaluator} that resolves a rank's typed requirements against
 * the economy / playtime / permission / stored-rank / placeholder / inventory seams, and the
 * {@link RankActionRunner} that runs a rank's configured actions through the shared click-action engine. Those
 * assemble the {@link Rankup} and {@link SetRank} use cases, published as the {@code /rankup} and
 * {@code /ranks setrank} commands.
 *
 * <p>The rank cost is charged through the {@link RankEconomy} seam bridged in the economy context and handed in
 * here as an {@link Optional} — empty when economy is disabled, in which case a priced rank is free. The rank
 * action runner is wired with no click-action economy of its own: a rank's paywall is its {@code cost}, so its
 * actions are effects and commands, never a second charge.
 */
@NullMarked
public final class RanksWiring {

    private RanksWiring() {}

    /** Build the ranks use cases and commands from {@code ctx}, the shared {@code persistence} handle and economy. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, Optional<RankEconomy> economy) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(economy, "economy");
        KernelPorts kernel = ctx.kernel();
        RanksConfig config = RanksConfig.from(ctx.config());
        RankLadder ladder = RankLadders.from(ctx.config());
        PlayerRankRepository repository = PlayerRankRepositories.jooq(persistence);
        CurrentRank currentRank = new CurrentRank(repository, ladder);
        Rankup rankup = new Rankup(
                currentRank,
                repository,
                ladder,
                requirementEvaluator(plugin, kernel, persistence, currentRank, economy),
                actionRunner(plugin, kernel),
                economy);
        SetRank setRank = new SetRank(repository, ladder);
        List<CommandRegistration> commands = List.of(
                new RankupCommand(rankup, kernel.messages()), new RanksCommand(setRank, ladder, kernel.messages()));
        return new Wired(commands, config, currentRank);
    }

    private static RankRequirementEvaluator requirementEvaluator(
            Plugin plugin,
            KernelPorts kernel,
            Persistence persistence,
            CurrentRank currentRank,
            Optional<RankEconomy> economy) {
        PlaytimeRepository playtime = PlaytimeRepositories.jooq(persistence);
        return new BukkitRankRequirementEvaluator(
                plugin.getServer(), kernel.permissions(), playtime, currentRank, economy, kernel.log());
    }

    /** The rank action runner over the same shared click-action engine the npc and hologram contexts use. */
    private static RankActionRunner actionRunner(Plugin plugin, KernelPorts kernel) {
        ClickCommandRunner commandRunner = new FilteredClickCommandRunner(
                new BukkitClickCommandRunner(), BlockedCommands.of(List.of()), kernel.log());
        ClickActionRunner clickRunner = new BukkitClickActionRunner(
                commandRunner,
                new BukkitServerConnector(plugin, kernel.log()),
                kernel.scheduler(),
                kernel.permissions(),
                Optional.empty(),
                kernel.messages(),
                RanksMessageKey.RANKS_RANKUP_COST,
                SerializedItems::decode,
                kernel.log());
        return new BukkitRankActionRunner(plugin.getServer(), clickRunner, kernel.log());
    }

    /**
     * Everything the ranks module contributes once wired: the Brigadier command registrations to publish, the
     * typed {@link RanksConfig} snapshot, and the {@link CurrentRank} read use case the later phases (prestige,
     * autorank, GUI, placeholders) build over.
     *
     * @param commands the Brigadier command registrations to publish
     * @param config the typed ranks config snapshot
     * @param currentRank the read use case resolving a player's rank standing against the ladder
     */
    public record Wired(List<CommandRegistration> commands, RanksConfig config, CurrentRank currentRank) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(currentRank, "currentRank");
        }
    }
}

package com.uxplima.uxmessentials.ranks.adapter;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.ranks.PlayerRankRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.RankLadders;
import com.uxplima.uxmessentials.ranks.application.RanksConfig;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the ranks context's use cases over the injected kernel ports and the shared persistence DSL. Phase 1
 * parses the ladder from {@code ranks.conf}, wires the DB-backed {@link PlayerRankRepository} through the
 * persistence factory (never naming a jOOQ type here), and builds the {@link CurrentRank} read use case over the
 * two. It publishes no command and registers no listener yet — the {@code /rankup}, {@code /prestige} and
 * {@code /ranks} verbs, their requirement/action evaluation and the autorank scan land as the later phases do —
 * so {@link Wired#commands()} is empty for now; the parsed config, ladder and read use case are exposed on
 * {@link Wired} so those phases build over them.
 */
@NullMarked
public final class RanksWiring {

    private RanksWiring() {}

    /** Build the ranks use cases from {@code ctx} and the shared {@code persistence} handle, ready to register. */
    public static Wired wire(ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        RanksConfig config = RanksConfig.from(ctx.config());
        RankLadder ladder = RankLadders.from(ctx.config());
        PlayerRankRepository repository = PlayerRankRepositories.jooq(persistence);
        CurrentRank currentRank = new CurrentRank(repository, ladder);
        return new Wired(List.of(), config, currentRank);
    }

    /**
     * Everything the ranks module contributes once wired. Phase 1 contributes no command — the list is empty —
     * but the parsed {@link RanksConfig} and the {@link CurrentRank} read use case are carried so the later
     * behaviour phases (rankup, prestige, autorank, GUI) build their verbs over them.
     *
     * @param commands the Brigadier command registrations to publish (empty in Phase 1)
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

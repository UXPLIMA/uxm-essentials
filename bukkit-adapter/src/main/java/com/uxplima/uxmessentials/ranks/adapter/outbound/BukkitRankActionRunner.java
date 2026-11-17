package com.uxplima.uxmessentials.ranks.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the ranks context's {@link RankActionRunner} port to the shared {@link ClickActionRunner} the npc and
 * hologram contexts already run their interaction chains through. A rank's raw action line is
 * {@code <type> <value…>}. The same {@code type value} shape those contexts use, minus the click trigger, since
 * a rankup is not a click, so each line is parsed into a {@link ClickAction} carrying {@link ClickTrigger#ANY}
 * and the chain is run once as if the player had triggered it. A line whose type word is unknown, or that carries
 * no value, is logged and skipped so one operator typo never aborts the rest of the rank's actions.
 *
 * <p>{@code {uuid}} in a line is the player's account id. It is filled in here, before the shared runner fills the
 * name, because a command that takes either should be given the id: a name the runner cannot vouch for (a Bedrock
 * name with a prefix it does not know, or one longer than an account name) makes it skip every line that names the
 * player, and a permission plugin may not accept a prefixed name at all. An id is always safe to put in a line.
 *
 * <p>Rankup runs on the player's own region thread (the {@code /rankup} command thread), which is where the
 * shared runner's Bukkit effects are safe, so the chain runs inline with no region hop. When the player is not on
 * this server there is no viewer to act on, so nothing runs and the caller keeps the lines for their next join.
 */
@NullMarked
public final class BukkitRankActionRunner implements RankActionRunner {

    /** The token a line names the player's account id with. */
    private static final String UUID_TOKEN = "{uuid}";

    private final Server server;
    private final ClickActionRunner runner;
    private final Logger log;

    public BukkitRankActionRunner(Server server, ClickActionRunner runner, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean run(PlayerRef who, List<String> actionLines) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(actionLines, "actionLines");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        List<ClickAction> actions = parse(actionLines, who.uuid().toString());
        if (!actions.isEmpty()) {
            runner.run(player, actions, true);
        }
        return true;
    }

    private List<ClickAction> parse(List<String> actionLines, String uuid) {
        List<ClickAction> actions = new ArrayList<>(actionLines.size());
        for (String line : actionLines) {
            parseLine(line.replace(UUID_TOKEN, uuid)).ifPresent(actions::add);
        }
        return actions;
    }

    private Optional<ClickAction> parseLine(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        if (parts.length < 2) {
            log.warn("event=rank_action_skipped reason=no_value line={}", line);
            return Optional.empty();
        }
        Optional<ClickActionType> type = ClickActionValueCheck.parseType(parts[0]);
        if (type.isEmpty()) {
            log.warn("event=rank_action_skipped reason=unknown_type line={}", line);
            return Optional.empty();
        }
        return Optional.of(new ClickAction(ClickTrigger.ANY, type.get(), parts[1]));
    }
}

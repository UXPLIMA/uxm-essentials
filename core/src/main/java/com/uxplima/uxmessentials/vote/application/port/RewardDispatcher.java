package com.uxplima.uxmessentials.vote.application.port;

import java.util.List;

/**
 * Outbound port that runs a batch of reward console commands for a named player. The adapter
 * substitutes the {@code {player}} placeholder with {@code playerName} and dispatches each command from
 * the console sender on the global region thread; the application never names a Bukkit type. A reward
 * line is operator-authored config content (e.g. {@code give {player} diamond 1}), not a player-facing
 * message, so it is dispatched as written rather than resolved through the message catalog.
 */
public interface RewardDispatcher {

    /** Run each command in {@code commands}, substituting {@code {player}} with {@code playerName}. */
    void dispatch(List<String> commands, String playerName);
}

package com.uxplima.uxmessentials.worlds.application.port;

import java.util.List;
import java.util.Optional;

/** Bukkit-free view of the server's gamerules so {@code :core} can validate rule names + value types. */
public interface GameRuleCatalog {

    /** The value kinds the validator understands (Bukkit gamerules are Boolean or Integer in 1.21). */
    enum GameRuleType {
        BOOLEAN,
        INTEGER
    }

    Optional<GameRuleType> typeOf(String name);

    List<String> names();
}

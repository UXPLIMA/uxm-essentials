package com.uxplima.uxmessentials.persistence.discordlink;

import java.util.Objects;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the discord-link context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link DiscordLinkStore} from the {@link Persistence} handle it already holds without ever naming a jOOQ type
 * (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile classpath).
 */
@NullMarked
public final class DiscordLinkStores {

    private DiscordLinkStores() {}

    /** A jOOQ {@link DiscordLinkStore} over the shared persistence DSL. */
    public static DiscordLinkStore jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqDiscordLinkStore(persistence.dsl());
    }
}

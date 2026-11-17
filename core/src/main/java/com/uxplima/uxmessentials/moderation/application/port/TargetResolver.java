package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Resolves a sanction target by name, online <em>or</em> offline. The kernel {@link
 * com.uxplima.uxmessentials.shared.application.port.PlayerLookup} only resolves online players by name; a
 * sanction must reach an offline (or never-joined) target too, an offline {@code /jail}, a {@code /banip
 * <player>} against a known-but-offline UUID, a {@code /warn} on a player who has logged off, so the
 * moderation context owns this wider resolution.
 *
 * <p>A uuid written out ({@code 069a79f4-44e9-4726-a5be-fca90e38aaf5}, or without dashes) names that account
 * wherever a name would.
 *
 * <p>The adapter resolves a player online on any backend of the network first, then falls back to the server's
 * offline-player cache (the has-played-before profile). A name that has never been seen matches nobody, which a
 * command maps to {@code UNKNOWN_TARGET}; on an offline-mode server a name several accounts hold only in different
 * case matches none of them, and the command lists the spellings instead of sanctioning a guess.
 */
public interface TargetResolver {

    /** The player this name, or this uuid written out, names: online or known offline. */
    NameMatch resolve(String name);

    /**
     * The account {@code typed} stands for when {@link #resolve} knows nobody by it: a uuid written out, or on an
     * offline-mode server a name, whose account the server derives from its spelling. Staff can then sanction an
     * account before it ever joins. Empty when nothing can name an account: an unknown name with authenticated logins
     * (Mojang owns that answer), or a Bedrock name, whose account Floodgate makes. The default knows none.
     */
    default Optional<PlayerRef> neverJoined(String typed) {
        return Optional.empty();
    }

    /** {@link #resolve}, and the {@link #neverJoined} account when it knows nobody by {@code name}. */
    default NameMatch resolveOrNeverJoined(String name) {
        NameMatch match = resolve(name);
        return match instanceof NameMatch.Unknown ? NameMatch.of(neverJoined(name)) : match;
    }
}

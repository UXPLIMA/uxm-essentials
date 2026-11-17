package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.NameMatch;

/**
 * Resolves a sanction target by name, online <em>or</em> offline. The kernel {@link
 * com.uxplima.uxmessentials.shared.application.port.PlayerLookup} only resolves online players by name; a
 * sanction must reach an offline (or never-joined) target too, an offline {@code /jail}, a {@code /banip
 * <player>} against a known-but-offline UUID, a {@code /warn} on a player who has logged off, so the
 * moderation context owns this wider resolution.
 *
 * <p>The adapter resolves a player online on any backend of the network first, then falls back to the server's
 * offline-player cache (the has-played-before profile). A name that has never been seen matches nobody, which a
 * command maps to {@code UNKNOWN_TARGET}; on an offline-mode server a name several accounts hold only in different
 * case matches none of them, and the command lists the spellings instead of sanctioning a guess.
 */
public interface TargetResolver {

    /** The player this name names, online or known offline. */
    NameMatch resolve(String name);
}

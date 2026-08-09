package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.Kick;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.Unban;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.Unmute;
import org.jspecify.annotations.NullMarked;

/**
 * The nine punishment use cases the published API runs.
 *
 * <p>These are the very instances the commands run, not copies: a ban handed down through the API and one typed by
 * a staff member are the same write, audited the same way and seen by the same listeners. Grouping them into one
 * value keeps the wiring's answer narrow, since the API needs nine of the several dozen the module assembles.
 *
 * @param ban the permanent ban
 * @param tempBan the timed ban, also what a capped permanent ban becomes
 * @param unban the ban lift
 * @param mute the mute, permanent or timed depending on the span it is given
 * @param unmute the mute lift
 * @param kick the disconnect
 * @param warn the warning, escalation rules included
 * @param jail the jail sentence
 * @param unjail the release
 */
@NullMarked
public record ModerationApiWrites(
        Ban ban,
        TempBan tempBan,
        Unban unban,
        Mute mute,
        Unmute unmute,
        Kick kick,
        IssueWarn warn,
        Jail jail,
        Unjail unjail) {

    public ModerationApiWrites {
        Objects.requireNonNull(ban, "ban");
        Objects.requireNonNull(tempBan, "tempBan");
        Objects.requireNonNull(unban, "unban");
        Objects.requireNonNull(mute, "mute");
        Objects.requireNonNull(unmute, "unmute");
        Objects.requireNonNull(kick, "kick");
        Objects.requireNonNull(warn, "warn");
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(unjail, "unjail");
    }
}

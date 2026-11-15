package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.shared.application.message.Notifier;

/**
 * The live half of an IP ban: kick everyone connected from the banned address with the same screen the login
 * check shows that address, so a player kicked now and a player refused on reconnect read the same line.
 * Shared by the local {@code /banip}, {@code /tempbanip} and the reaction to a peer's IP ban.
 *
 * <p>An exempt holder is spared, as the login check spares them, and so is the staff member who issued the ban
 * when they share the address, as {@code /kickall} spares its actor.
 */
final class IpBanKick {

    private IpBanKick() {}

    static void kickEveryoneOn(
            IpBan ban, Optional<UUID> actor, ModerationGuard guard, Sanctions sanctions, Notifier notifier) {
        Map<String, String> placeholders = Map.of("reason", ban.reason().orElse(""));
        sanctions.kickAddress(
                ban.ip(),
                player -> !actor.equals(Optional.of(player.uuid())) && !guard.isExempt(player),
                player -> notifier.render(player, ModerationMessageKey.BANIP_KICK, placeholders));
    }
}

package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.shared.application.message.Notifier;

/**
 * The live half of an IP ban: kick everyone connected from the banned address with the same screen the login
 * check shows that address, so a player kicked now and a player refused on reconnect read the same line.
 * Shared by the local {@code /banip}, {@code /tempbanip} and the reaction to a peer's IP ban.
 */
final class IpBanKick {

    private IpBanKick() {}

    static void kickEveryoneOn(IpBan ban, Sanctions sanctions, Notifier notifier) {
        Map<String, String> placeholders = Map.of("reason", ban.reason().orElse(""));
        sanctions.kickAddress(
                ban.ip(), player -> notifier.render(player, ModerationMessageKey.BANIP_KICK, placeholders));
    }
}

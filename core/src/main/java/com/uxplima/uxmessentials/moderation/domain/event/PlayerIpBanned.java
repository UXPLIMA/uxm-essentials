package com.uxplima.uxmessentials.moderation.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.domain.IpBan;

/**
 * An IP address was banned by {@code /banip}. The {@code ban} carries the address, the optional resolved
 * target UUID, the issuer and the expiry.
 *
 * @param ban the applied IP ban
 */
public record PlayerIpBanned(IpBan ban) implements ModerationEvent {

    public PlayerIpBanned {
        Objects.requireNonNull(ban, "ban");
    }
}

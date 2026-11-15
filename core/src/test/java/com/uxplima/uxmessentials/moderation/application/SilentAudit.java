package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** An audit log that writes nothing; the audit lines are not what these tests look at. */
final class SilentAudit implements ModerationAudit {

    @Override
    public void muted(
            PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {}

    @Override
    public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

    @Override
    public void jailed(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {}

    @Override
    public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

    @Override
    public void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {}

    @Override
    public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {}

    @Override
    public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

    @Override
    public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {}

    @Override
    public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

    @Override
    public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {}

    @Override
    public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {}

    @Override
    public void ipBanned(
            PlayerRef actor,
            String targetIp,
            Optional<UUID> target,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {}

    @Override
    public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {}

    @Override
    public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {}

    @Override
    public void jailLocationDefined(PlayerRef actor, String jail) {}

    @Override
    public void jailLocationRemoved(PlayerRef actor, String jail) {}

    @Override
    public void lockdown(UUID actor, boolean enabled) {}
}

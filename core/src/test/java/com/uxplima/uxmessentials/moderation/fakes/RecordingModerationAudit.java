package com.uxplima.uxmessentials.moderation.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link ModerationAudit} that records each emitted line so a test can assert exactly one audit line per
 * action and read its {@code event}/{@code ok} fields. Each method appends one {@link Line}.
 */
public final class RecordingModerationAudit implements ModerationAudit {

    public final List<Line> lines = new ArrayList<>();

    @Override
    public void muted(
            PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_mute", ok));
    }

    @Override
    public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_unmute", ok));
    }

    @Override
    public void jailed(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        lines.add(new Line("player_jail", ok));
    }

    @Override
    public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_unjail", ok));
    }

    @Override
    public void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_tempban", ok));
    }

    @Override
    public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_warn", ok));
    }

    @Override
    public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        lines.add(new Line("player_kick", ok));
    }

    @Override
    public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {
        lines.add(new Line("player_kickall", true));
    }

    @Override
    public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {
        lines.add(new Line("player_freeze", ok));
    }

    @Override
    public void ipBanned(
            PlayerRef actor,
            String targetIp,
            Optional<UUID> target,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        lines.add(new Line("player_banip", ok));
    }

    @Override
    public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {
        lines.add(new Line("player_unbanip", ok));
    }

    @Override
    public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {
        lines.add(new Line("alt_detected", true));
    }

    /** One recorded audit line: its event name and the ok flag. */
    public record Line(String event, boolean ok) {}
}

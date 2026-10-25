package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An operator gives a player their body back: {@code /poses reveal <player>}.
 *
 * <p>A pose hides the real player behind a copy, and a player can be left unseen by a pose that ended without giving
 * the body back, which an older build did. Nothing tells such a player from one another plugin made invisible on
 * purpose, so this is an operator's call rather than something the plugin does on its own. A pose the player is
 * still in is ended first, the ordinary way, so the seat and the copy go with it.
 */
public final class RevealPoser {

    /** What a reveal did. */
    public enum Outcome {
        /** The player was posing or left unseen, and is seen again. */
        REVEALED,
        /** Nothing was holding the player unseen that a reveal may undo. */
        NOTHING_TO_REVEAL
    }

    private final PoseSessions sessions;
    private final StopPose stopPose;
    private final PosePort poses;

    public RevealPoser(PoseSessions sessions, StopPose stopPose, PosePort poses) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.poses = Objects.requireNonNull(poses, "poses");
    }

    /** Give {@code who} their body back. Run on the thread that owns them. */
    public Outcome reveal(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean posing = sessions.isPosing(who);
        if (posing) {
            stopPose.stop(who, false);
        }
        boolean undone = poses.reveal(who);
        return posing || undone ? Outcome.REVEALED : Outcome.NOTHING_TO_REVEAL;
    }
}

package com.uxplima.uxmessentials.moderation.application;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** The whole login check at once: the pre-login read, the login decision and the bookkeeping after it. */
final class Logins {

    private Logins() {}

    static LoginEnforcement.Decision check(
            LoginEnforcement login, PlayerRef who, String ip, boolean hasBypass, boolean exempt) {
        LoginEnforcement.Standing standing = login.read(who, ip);
        login.carryOver(standing);
        LoginEnforcement.Decision decision = login.decide(standing, hasBypass, exempt);
        login.record(standing, !decision.allowed());
        return decision;
    }
}

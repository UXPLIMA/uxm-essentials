package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /unbanip <ip>}: lift an IP ban. An address that is not banned is refused (audit-logged); a banned
 * address's row is deleted and the login listener stops barring connections from it.
 */
public final class UnbanIp {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final SanctionHistoryRecorder history;

    public UnbanIp(
            ModerationRepository repository,
            Notifier notifier,
            ModerationAudit audit,
            SanctionHistoryRecorder history) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Lift the IP ban on {@code ip}, or refuse when it was not banned. */
    public Result<Unit, ModerationError> unbanIp(PlayerRef actor, String ip) {
        return unbanIp(actor, ip, ip);
    }

    /**
     * Lift the IP ban on {@code ip}, the canonical spelling, or else on {@code typed}, the spelling the staff member
     * wrote: a ban stored before addresses were kept canonical is keyed by the text it was banned with, and is
     * removed by typing that text. Refused when neither is banned.
     */
    public Result<Unit, ModerationError> unbanIp(PlayerRef actor, String ip, String typed) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(typed, "typed");
        String lifted = ip;
        if (!repository.removeIpBan(ip)) {
            if (typed.equals(ip) || !repository.removeIpBan(typed)) {
                audit.ipUnbanned(actor, ip, false);
                notifier.send(actor, ModerationError.NOT_IP_BANNED.messageKey());
                return Result.err(ModerationError.NOT_IP_BANNED);
            }
            lifted = typed;
        }
        history.unbanIp(actor, lifted);
        audit.ipUnbanned(actor, lifted, true);
        notifier.send(actor, ModerationMessageKey.UNBANIP_APPLIED, Map.of("ip", lifted));
        return Result.ok();
    }
}

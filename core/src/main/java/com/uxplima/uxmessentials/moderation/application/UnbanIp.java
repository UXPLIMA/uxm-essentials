package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
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
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(ip, "ip");
        if (!repository.removeIpBan(ip)) {
            audit.ipUnbanned(actor, ip, false);
            notifier.send(actor, ModerationError.NOT_IP_BANNED.messageKey());
            return Result.err(ModerationError.NOT_IP_BANNED);
        }
        history.unbanIp(actor, ip);
        audit.ipUnbanned(actor, ip, true);
        notifier.send(actor, ModerationMessageKey.UNBANIP_APPLIED, Map.of("ip", ip));
        return Result.ok();
    }
}

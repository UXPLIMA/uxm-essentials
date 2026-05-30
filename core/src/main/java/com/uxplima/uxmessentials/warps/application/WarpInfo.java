package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * {@code /warpinfo <name>}: show a warp's owner, creation time, cost, and required permission. The use case
 * reads the warp and pushes one header line plus a line per attribute through the notifier, so all text
 * resolves from {@link WarpsMessageKey}; the human-readable rendering of the creation timestamp is the
 * adapter's job, which is why the raw epoch-milli value is passed as a placeholder rather than a
 * pre-formatted date (formatting would need a locale/zone the application layer does not own).
 *
 * <p>A missing warp is rejected with {@link WarpError#NOT_FOUND}. A free warp reports its cost line as the
 * "free" value the catalog renders, and an ungated warp omits the permission line entirely.
 */
public final class WarpInfo {

    private final WarpRepository repository;
    private final WarpNotifier notifier;

    public WarpInfo(WarpRepository repository, WarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code viewer} the details of the warp {@code name}, or reject when it is missing. */
    public Result<Unit, WarpError> show(PlayerRef viewer, WarpName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Warp> warp = repository.find(name);
        if (warp.isEmpty()) {
            notifier.send(viewer, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        render(viewer, warp.get());
        return Result.ok();
    }

    private void render(PlayerRef viewer, Warp warp) {
        notifier.send(
                viewer,
                WarpsMessageKey.WARP_INFO_HEADER,
                Map.of("warp", warp.name().value()));
        notifier.send(
                viewer,
                WarpsMessageKey.WARP_INFO_OWNER,
                Map.of("owner", warp.owner().name()));
        notifier.send(
                viewer,
                WarpsMessageKey.WARP_INFO_CREATED,
                Map.of("epochMillis", Long.toString(warp.createdAt().toEpochMilli())));
        notifier.send(
                viewer,
                WarpsMessageKey.WARP_INFO_COST,
                Map.of("cost", warp.cost().amount().toPlainString()));
        warp.requiredPermission()
                .ifPresent(node ->
                        notifier.send(viewer, WarpsMessageKey.WARP_INFO_PERMISSION, Map.of("permission", node)));
    }
}

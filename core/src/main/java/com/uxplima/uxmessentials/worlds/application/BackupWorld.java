package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Kicks off a world backup. The use case stays thin: it refuses an unmanaged world, announces the
 * snapshot has started, and hands off to {@link WorldArchive#backup} which performs the long-running
 * zip off-tick and fires the {@code WORLD_BACKUP_CREATED} / {@code WORLD_BACKUP_FAILED} completion
 * notification itself. The initiator is threaded through to the archive so that completion message
 * reaches the right operator.
 */
public final class BackupWorld {

    private final WorldRepository repository;
    private final WorldArchive archive;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public BackupWorld(WorldRepository repository, WorldArchive archive, Notifier notifier, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<BackupId, WorldError> backup(PlayerRef who, WorldName world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        if (!repository.exists(world)) {
            notify(who, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", world.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        notify(who, WorldsMessageKey.WORLD_BACKUP_STARTED, Map.of("world", world.value()));
        return archive.backup(who, world);
    }

    /** Folia-safe notify: bounce the delivery back onto the recipient's region thread. */
    private void notify(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}

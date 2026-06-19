package com.uxplima.uxmessentials.worlds.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Lists the backups recorded for a managed world; an unmanaged world has no backups to show. */
public final class ListBackups {

    private final WorldRepository repository;
    private final WorldArchive archive;

    public ListBackups(WorldRepository repository, WorldArchive archive) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.archive = Objects.requireNonNull(archive, "archive");
    }

    public List<BackupRef> list(WorldName world) {
        Objects.requireNonNull(world, "world");
        return repository.exists(world) ? archive.list(world) : List.of();
    }
}

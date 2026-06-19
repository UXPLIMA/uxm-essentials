package com.uxplima.uxmessentials.worlds.application;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.Nullable;

/** Recording {@link WorldArchive} fake: a settable backup list plus captured backup/restore calls. */
final class FakeWorldArchive implements WorldArchive {

    List<BackupRef> listing = new ArrayList<>();
    Result<BackupId, WorldError> backupResult = Result.ok(BackupId.of("snapshot"));

    @Nullable PlayerRef backupInitiator;

    @Nullable WorldName backupWorld;

    @Nullable PlayerRef restoreInitiator;

    @Nullable WorldName restoreWorld;

    @Nullable BackupId restoreId;

    @Override
    public Result<BackupId, WorldError> backup(PlayerRef initiator, WorldName world) {
        this.backupInitiator = initiator;
        this.backupWorld = world;
        return backupResult;
    }

    @Override
    public List<BackupRef> list(WorldName world) {
        return listing;
    }

    @Override
    public Result<Unit, WorldError> restore(PlayerRef initiator, WorldName world, BackupId id) {
        this.restoreInitiator = initiator;
        this.restoreWorld = world;
        this.restoreId = id;
        return Result.ok();
    }
}

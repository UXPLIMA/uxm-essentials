package com.uxplima.uxmessentials.guard.application.port;

import java.util.List;

import com.uxplima.uxmessentials.guard.domain.Region;

/** Region backups as files: written on start, before an import and before a restore; read back by a restore. */
public interface GuardBackups {

    /** Write a backup and return its name; keeps the newest {@code keep} files. */
    String write(List<Region> regions, String reason, int keep);

    /** Read a backup by name. */
    List<Region> read(String name);

    /** The backup names, newest first. */
    List<String> list();
}

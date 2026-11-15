package com.uxplima.uxmessentials.vaults.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.jspecify.annotations.Nullable;

/**
 * Forwards to {@link InMemoryVaults}, but runs what another server or a second command does at the worst moment:
 * between a use case's read and its next write. A step armed for the next delete, create or update runs once, right
 * before that call reaches the rows, and a create can be made to fail as a broken database would.
 */
final class InterleavedVaults implements VaultRepository {

    private final InMemoryVaults rows;
    private @Nullable Runnable beforeDelete;
    private @Nullable Runnable beforeCreate;
    private @Nullable Runnable beforeUpdate;
    private @Nullable Runnable beforeWrite;
    private int lastWriteFailures;
    private boolean createFails;
    private boolean createFailsAfterWriting;
    private int writeFailures;
    private boolean writeFailsAfterCommit;

    InterleavedVaults(InMemoryVaults rows) {
        this.rows = rows;
    }

    /** Run {@code step} once, right before the next delete reaches the rows. */
    void beforeNextDelete(Runnable step) {
        beforeDelete = step;
    }

    /** Run {@code step} once, right before the next create reaches the rows. */
    void beforeNextCreate(Runnable step) {
        beforeCreate = step;
    }

    /** Run {@code step} once, right before the next update reaches the rows. */
    void beforeNextUpdate(Runnable step) {
        beforeUpdate = step;
    }

    /** Run {@code step} once, right before the next item write reaches the rows. */
    void beforeNextWrite(Runnable step) {
        beforeWrite = step;
    }

    /** Make the next {@code count} reads of who wrote a row last throw, as a lost database connection does. */
    void failLastWrites(int count) {
        lastWriteFailures = count;
    }

    /** Make the next create throw, as a lost database connection does. */
    void failNextCreate() {
        createFails = true;
    }

    /** Make the next item write throw, as a lost database connection does. */
    void failNextWrite() {
        failWrites(1);
    }

    /** Make the next {@code count} item writes throw before they reach the rows. */
    void failWrites(int count) {
        writeFailures = count;
    }

    /** Make the next item write reach the rows and then throw, as a commit whose answer was lost does. */
    void failNextWriteAfterCommit() {
        writeFailsAfterCommit = true;
    }

    /** Make the next create store its row and then throw, as a connection lost while the commit was answered does. */
    void failNextCreateAfterWriting() {
        createFailsAfterWriting = true;
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        return rows.find(id);
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        return rows.ownedIndices(owner);
    }

    @Override
    public List<VaultSummary> summaries(PlayerRef owner) {
        return rows.summaries(owner);
    }

    @Override
    public int count(PlayerRef owner) {
        return rows.count(owner);
    }

    @Override
    public VaultStamp save(Vault vault, String writer) {
        runBeforeWrite();
        failWriteIfArmed();
        return rows.save(vault, writer);
    }

    @Override
    public Optional<VaultStamp> writeContents(Vault vault, String writer, @Nullable VaultStamp onlyAt) {
        runBeforeWrite();
        failWriteIfArmed();
        Optional<VaultStamp> written = rows.writeContents(vault, writer, onlyAt);
        if (writeFailsAfterCommit) {
            writeFailsAfterCommit = false;
            throw new IllegalStateException("the answer to the commit was lost");
        }
        return written;
    }

    @Override
    public Optional<LastWrite> lastWrite(VaultId id) {
        if (lastWriteFailures > 0) {
            lastWriteFailures--;
            throw new IllegalStateException("the database went away");
        }
        return rows.lastWrite(id);
    }

    private void runBeforeWrite() {
        Runnable step = beforeWrite;
        beforeWrite = null;
        if (step != null) {
            step.run();
        }
    }

    private void failWriteIfArmed() {
        if (writeFailures > 0) {
            writeFailures--;
            throw new IllegalStateException("the database went away");
        }
    }

    @Override
    public boolean update(Vault vault, Columns columns) {
        Runnable step = beforeUpdate;
        beforeUpdate = null;
        if (step != null) {
            step.run();
        }
        return rows.update(vault, columns);
    }

    @Override
    public boolean create(Vault vault) {
        Runnable step = beforeCreate;
        beforeCreate = null;
        if (step != null) {
            step.run();
        }
        if (createFails) {
            createFails = false;
            throw new IllegalStateException("the database went away");
        }
        boolean created = rows.create(vault);
        if (createFailsAfterWriting) {
            createFailsAfterWriting = false;
            throw new IllegalStateException("the database went away after the commit");
        }
        return created;
    }

    @Override
    public boolean delete(VaultId id, Optional<VaultPayment> expected) {
        Runnable step = beforeDelete;
        beforeDelete = null;
        if (step != null) {
            step.run();
        }
        return rows.delete(id, expected);
    }

    @Override
    public int deleteUntouchedBefore(Instant cutoff) {
        return rows.deleteUntouchedBefore(cutoff);
    }
}

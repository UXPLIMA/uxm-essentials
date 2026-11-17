package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NameHolders} read from the plugin's own name table, which every server of the database writes, so an
 * account seen on another backend counts too. The in-memory name index is not asked: it holds a window of recent
 * names, and a ban can sit on an account that has not joined for months.
 *
 * <p>The server's name case is asked on every call, since it can turn case-sensitive while the server runs (the first
 * offline-mode login on a proxy that reports online mode). While names are not case-sensitive a name has one owner,
 * so nothing is read.
 */
@NullMarked
public final class StoredNameHolders implements NameHolders {

    /** The most accounts read under one name: far more spellings than any real name has, few enough to stay cheap. */
    static final int MOST_HOLDERS = 32;

    private final PlayerNameRepository names;
    private final Supplier<NameCase> nameCase;

    public StoredNameHolders(PlayerNameRepository names, Supplier<NameCase> nameCase) {
        this.names = Objects.requireNonNull(names, "names");
        this.nameCase = Objects.requireNonNull(nameCase, "nameCase");
    }

    @Override
    public List<PlayerRef> underName(String name) {
        Objects.requireNonNull(name, "name");
        if (nameCase.get() != NameCase.SENSITIVE) {
            return List.of();
        }
        return names.underName(name, MOST_HOLDERS).stream().map(PlayerName::ref).toList();
    }
}

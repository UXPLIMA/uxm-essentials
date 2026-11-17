package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.NameHolders;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NameHolders} read from the plugin's own name table, which every server of the database writes, so an
 * account seen on another backend counts too. The in-memory name index is not asked: it holds a window of recent
 * names, and a ban can sit on an account that has not joined for months.
 */
@NullMarked
public final class StoredNameHolders implements NameHolders {

    private final PlayerNameRepository names;

    public StoredNameHolders(PlayerNameRepository names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override
    public List<PlayerRef> underName(String name) {
        Objects.requireNonNull(name, "name");
        return names.underName(name).stream().map(PlayerName::ref).toList();
    }
}

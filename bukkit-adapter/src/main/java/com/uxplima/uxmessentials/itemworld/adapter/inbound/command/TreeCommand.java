package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.TreeType;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tree <type>}: grow a tree of the named {@link TreeType} one block above whatever the caller is looking at.
 * An admin-fun verb (audit-logged), a quick terrain tool that is easy to abuse, so each grow is recorded with actor
 * and tree type.
 *
 * <p>Type matching is forgiving: the argument is lower-cased and stripped of underscores before comparison with the
 * enum names, and the bare word {@code jungle} resolves to {@link TreeType#SMALL_JUNGLE} (so {@code /tree jungle}
 * grows the smaller sapling rather than the 2x2 variant). The shared node shape, gates and region-thread grow live in
 * {@link AbstractTreeCommand}; this class supplies only the resolver.
 */
@NullMarked
public final class TreeCommand extends AbstractTreeCommand implements CommandRegistration {

    public TreeCommand(ItemworldServices services) {
        super(services, "tree", SubFeatureGroup.ADMIN_FUN, "Generate a tree where you are looking.");
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    protected Optional<TreeType> resolveType(String arg) {
        return resolve(arg);
    }

    /** Resolve the friendly argument to a {@link TreeType}, with the {@code jungle} convenience. */
    private static Optional<TreeType> resolve(String arg) {
        Objects.requireNonNull(arg, "arg");
        String normalised = arg.toLowerCase(Locale.ROOT).replace("_", "");
        if (normalised.equals("jungle")) {
            return Optional.of(TreeType.SMALL_JUNGLE);
        }
        for (TreeType type : TreeType.values()) {
            if (type.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalised)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

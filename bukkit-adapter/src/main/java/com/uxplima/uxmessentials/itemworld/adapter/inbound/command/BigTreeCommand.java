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
 * {@code /bigtree <type>}: the large-variant sibling of {@code /tree}, growing the big form of the named tree one
 * block above whatever the caller is looking at. The friendly names map to the larger {@link TreeType} variants:
 * {@code jungle} grows the 2x2 mega jungle rather than {@code /tree}'s small sapling, and the bare
 * {@code tree}/{@code oak} word grows {@link TreeType#BIG_TREE}. An admin-fun verb, audit-logged like its sibling
 * because growing terrain is easy to abuse. The shared node shape, gates and region-thread grow live in
 * {@link AbstractTreeCommand}; this class supplies only the large-variant resolver.
 */
@NullMarked
public final class BigTreeCommand extends AbstractTreeCommand implements CommandRegistration {

    public BigTreeCommand(ItemworldServices services) {
        super(services, "bigtree", SubFeatureGroup.ADMIN_FUN, "Generate a large tree where you are looking.");
    }

    @Override
    public List<String> aliases() {
        return List.of("largetree");
    }

    @Override
    protected Optional<TreeType> resolveType(String arg) {
        return resolve(arg);
    }

    /**
     * Resolve the friendly argument to the large variant of a {@link TreeType}. The bare {@code tree}/{@code oak}
     * word grows {@link TreeType#BIG_TREE}; {@code jungle} grows the 2x2 {@link TreeType#JUNGLE} (contrasting
     * {@code /tree}'s small sapling); the redwood and dark-oak families pick their mega forms. Anything else is
     * lower-cased, stripped of underscores and matched against the enum names, else empty.
     */
    static Optional<TreeType> resolve(String arg) {
        Objects.requireNonNull(arg, "arg");
        String normalised = arg.toLowerCase(Locale.ROOT).replace("_", "");
        switch (normalised) {
            case "tree", "oak" -> {
                return Optional.of(TreeType.BIG_TREE);
            }
            case "jungle" -> {
                return Optional.of(TreeType.JUNGLE);
            }
            case "redwood", "spruce", "megaredwood" -> {
                return Optional.of(TreeType.MEGA_REDWOOD);
            }
            case "darkoak" -> {
                return Optional.of(TreeType.DARK_OAK);
            }
            default -> {
                /* fall through to the enum-name match below */
            }
        }
        for (TreeType type : TreeType.values()) {
            if (type.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalised)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

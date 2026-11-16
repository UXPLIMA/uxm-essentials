package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.OptionalLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.uxplima.uxmessentials.shared.adapter.outbound.OwningThreadReads;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Permissions} implementation backed by Bukkit's permission system, with an optional
 * LuckPerms meta path layered on top.
 *
 * <p>Boolean checks ({@link #has}) read the online {@link Player}'s own setting, which folds in vanilla op and
 * whatever permission plugin is installed, and fall back to the catalogue's default for a name-family node nobody set
 * ({@link CatalogPermissionCheck}). Value-bearing nodes
 * ({@link #resolveQuota}) are read by enumerating the player's effective permissions and folding the
 * numbered (and optionally world-scoped) nodes through {@link QuotaNodeReducer}; a LuckPerms meta
 * value, when present, is one more input to the same fold. An offline player cannot be
 * permission-checked, so {@code has} returns {@code false} and {@code resolveQuota} returns the config
 * default, the canonical conservative behaviour.
 *
 * <p>Both are read on the player's own thread through {@link OwningThreadReads}: a use case on the async lane asks
 * them after its database reads, and the player's permissions are theirs to read. A read that cannot reach the player
 * in time answers as for an offline player.
 */
@NullMarked
public final class BukkitPermissions implements Permissions {

    private final MetaSource meta;
    private final OwningThreadReads reads;

    /** Build with a meta source (the LuckPerms-backed one when LuckPerms is present). */
    public BukkitPermissions(MetaSource meta, OwningThreadReads reads) {
        this.meta = Objects.requireNonNull(meta, "meta");
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    @Override
    public boolean has(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        return reads.forPlayer(who, () -> hasNow(who, node), false);
    }

    private static boolean hasNow(PlayerRef who, String node) {
        Player player = Bukkit.getPlayer(who.uuid());
        return player != null && CatalogPermissionCheck.has(player, node);
    }

    @Override
    public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(family, "family");
        return reads.forPlayer(
                who, () -> resolveNow(who, family, world, configDefault), offlineQuota(family, world, configDefault));
    }

    private QuotaResult resolveNow(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        QuotaNodeReducer reducer = reducer(family, world, configDefault);
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return reducer.result();
        }
        foldEffectiveNodes(player, reducer);
        foldMeta(who, family, world, reducer);
        return reducer.result();
    }

    private static QuotaResult offlineQuota(QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        return reducer(family, world, configDefault).result();
    }

    private static QuotaNodeReducer reducer(QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        QuotaNodeReducer reducer = new QuotaNodeReducer(family, world == null ? null : world.name());
        reducer.seedDefault(configDefault);
        return reducer;
    }

    private static void foldEffectiveNodes(Player player, QuotaNodeReducer reducer) {
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue()) {
                reducer.offerNode(info.getPermission());
            }
        }
    }

    private void foldMeta(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, QuotaNodeReducer reducer) {
        OptionalLong unscoped = meta.metaValue(who.uuid(), family.node());
        if (unscoped.isPresent()) {
            reducer.offerMeta(unscoped.getAsLong());
        }
        if (world != null) {
            OptionalLong scoped = meta.metaValue(who.uuid(), family.node() + "." + world.name());
            scoped.ifPresent(reducer::offerMeta);
        }
    }
}

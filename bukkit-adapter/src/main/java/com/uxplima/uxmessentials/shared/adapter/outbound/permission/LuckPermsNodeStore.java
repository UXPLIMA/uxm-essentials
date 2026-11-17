package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NodeStore} backed by LuckPerms. {@code loadUser} reads the user from the LuckPerms storage when they are
 * not loaded here, which on a network sharing that storage is the same data every backend applies. It is constructed
 * only past {@link LuckPermsAccess#isPresent}, so a server without LuckPerms never loads this class.
 *
 * <p>The node is read without contexts: a grant held only with {@code server=survival} or a {@code world=} context
 * counts, wherever the read is made. A sanction reaches the player on every server and in every world, so a grant
 * that exempts them anywhere exempts them from it. A user LuckPerms holds no data for (its storage is not shared with
 * the server they played on, or they only ever had the default group) grants nothing here, which leaves the answer to
 * the other sources rather than proving the node is not held.
 */
@NullMarked
final class LuckPermsNodeStore implements NodeStore {

    private final LuckPerms luckPerms;

    LuckPermsNodeStore() {
        this.luckPerms = LuckPermsProvider.get();
    }

    @Override
    public Load prepare(UUID player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return () -> luckPerms
                .getUserManager()
                .loadUser(player)
                .thenApply(user -> grants(user, node) ? Answer.HELD : Answer.NOT_HELD);
    }

    /** Whether {@code user} holds {@code node} in any context, directly, through a group, or through a wildcard. */
    private static boolean grants(User user, String node) {
        QueryOptions everywhere = QueryOptions.nonContextual();
        if (user.getCachedData().getPermissionData(everywhere).checkPermission(node) == Tristate.TRUE) {
            return true;
        }
        // A denial in one context can hide a grant in another from the merged answer; any grant counts.
        return user.resolveInheritedNodes(everywhere).stream()
                .anyMatch(held -> held.getKey().equals(node) && held.getValue());
    }
}

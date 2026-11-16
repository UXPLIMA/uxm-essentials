package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;

import org.bukkit.permissions.Permissible;

import com.uxplima.uxmessentials.shared.application.permission.CatalogDefaults;
import com.uxplima.uxmessentials.shared.application.permission.PermissionHolder;
import org.jspecify.annotations.NullMarked;

/**
 * A permission check that gives a node nobody set the catalogue's default instead of the server's.
 *
 * <p>The server answers a node it was never told about as operators only, and the catalogue's name families are
 * never told, so a sender's own {@code hasPermission} is wrong for them. Any site that may check a family member, one
 * built from a name at runtime or one an operator wrote into a file, asks here; a site that checks one fixed node can
 * keep asking the sender, because that node was registered with its default. {@link CatalogDefaults} holds the rule.
 */
@NullMarked
public final class CatalogPermissionCheck {

    private CatalogPermissionCheck() {}

    /** Whether {@code who} holds {@code node}, an explicit setting first and the catalogue's default after it. */
    public static boolean has(Permissible who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        return CatalogDefaults.holds(new Holder(who), node);
    }

    private record Holder(Permissible who) implements PermissionHolder {

        @Override
        public boolean isPermissionSet(String node) {
            return who.isPermissionSet(node);
        }

        @Override
        public boolean hasPermission(String node) {
            return who.hasPermission(node);
        }

        @Override
        public boolean isOp() {
            return who.isOp();
        }
    }
}

package com.uxplima.uxmessentials.shared.application.permission;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.Nullable;

/**
 * One permission the plugin publishes, described once.
 *
 * <p>This record is the one place a node is declared. The adapter registers the fixed nodes with the server on
 * enable, so the server and the permission plugin know they exist, and {@code /uxmess permissions} lists every entry
 * to an operator in game. The permission pages under {@code docs/user/} are generated from the catalogue.
 *
 * @param node the node, written with its placeholder visible when the shape is a family
 *     ({@code uxmessentials.home.limit.<n>})
 * @param description what holding it lets a player do, in one sentence an operator can act on
 * @param fallback who holds it when nobody has granted or denied it
 * @param shape whether this is one node or the head of an open family
 * @param owner the feature module the node belongs to, or empty for the cross-cutting ones the kernel owns
 */
public record PermissionSpec(
        String node, String description, PermissionDefault fallback, PermissionShape shape, Optional<ModuleId> owner) {

    public PermissionSpec {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(owner, "owner");
        if (!node.startsWith(SharedPermissions.NAMESPACE)) {
            throw new IllegalArgumentException("every node belongs to the uxmessentials space: " + node);
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("node " + node + " needs a description an operator can act on");
        }
        if (shape == PermissionShape.FIXED && node.contains("<")) {
            throw new IllegalArgumentException("node " + node + " is written as a family but declared FIXED");
        }
        if (shape != PermissionShape.FIXED && !node.contains("<")) {
            throw new IllegalArgumentException("family " + node + " must show its placeholder, as in .<n> or .<name>");
        }
    }

    /** A fixed node belonging to a feature module. */
    public static PermissionSpec of(String node, String description, PermissionDefault fallback, ModuleId owner) {
        return new PermissionSpec(node, description, fallback, PermissionShape.FIXED, Optional.of(owner));
    }

    /** A family belonging to a feature module, written with its placeholder. */
    public static PermissionSpec family(
            String node, String description, PermissionDefault fallback, PermissionShape shape, ModuleId owner) {
        return new PermissionSpec(node, description, fallback, shape, Optional.of(owner));
    }

    /** A node the kernel owns rather than any one module: the admin root, {@code /help}, {@code /lang}. */
    public static PermissionSpec shared(String node, String description, PermissionDefault fallback) {
        return new PermissionSpec(node, description, fallback, PermissionShape.FIXED, Optional.empty());
    }

    /** A kernel-owned family, such as the cooldown space every context resolves its waits through. */
    public static PermissionSpec sharedFamily(
            String node, String description, PermissionDefault fallback, PermissionShape shape) {
        return new PermissionSpec(node, description, fallback, shape, Optional.empty());
    }

    /** Whether this entry names one real node the server can be told about. */
    public boolean registrable() {
        return shape == PermissionShape.FIXED;
    }

    /**
     * The fixed part of a family, up to but not including the placeholder: {@code uxmessentials.home.limit.} for
     * {@code uxmessentials.home.limit.<n>}. For a fixed node this is the node itself. This is what a literal in the
     * code is matched against when it is composed rather than written whole.
     */
    public String prefix() {
        int placeholder = node.indexOf('<');
        return placeholder < 0 ? node : node.substring(0, placeholder);
    }

    /** The owning module's id, or {@code "shared"} for the cross-cutting ones, for grouping and display. */
    public String area() {
        return owner.map(ModuleId::value).orElse("shared");
    }

    /**
     * Whether {@code candidate} is this entry: the node itself, or a member of the family it heads. The head of a
     * family counts as a member, because that is how the code holds one: the site that resolves a quota writes
     * {@code "uxmessentials.home.limit"} and appends the number it is looking for.
     */
    public boolean covers(@Nullable String candidate) {
        if (candidate == null) {
            return false;
        }
        if (shape == PermissionShape.FIXED) {
            return node.equals(candidate);
        }
        String prefix = prefix();
        return candidate.startsWith(prefix) || (candidate + ".").equals(prefix);
    }
}

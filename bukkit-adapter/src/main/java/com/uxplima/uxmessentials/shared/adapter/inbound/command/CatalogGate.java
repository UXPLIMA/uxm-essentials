package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.permission.CatalogPermissionCheck;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * A command gate for a node that may be a member of a catalogue family, one built from a name at runtime or one an
 * operator wrote into a file.
 *
 * <p>{@link Gate#permission} asks the sender's own {@code hasPermission}, which answers a family member the server
 * was never told about as operators only. This gate lets {@link CatalogPermissionCheck} decide instead and still
 * names the node when it refuses, so the sender reads the usual no-permission line.
 */
@NullMarked
public final class CatalogGate {

    private CatalogGate() {}

    /** A gate that lets a sender through exactly when the catalogue rule says they hold {@code node}. */
    public static Gate permission(String node) {
        Objects.requireNonNull(node, "node");
        Gate rule = Gate.when(sender -> CatalogPermissionCheck.has(sender, node));
        // The permission gate is only there for the node it names on refusal: joined with the rule by "or" and then
        // narrowed by the rule again, the result lets through exactly whom the rule lets through, and a plain refusal
        // reaches the permission part first and reports its node.
        return Gate.permission(node).or(rule).and(rule);
    }
}

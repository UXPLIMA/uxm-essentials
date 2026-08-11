package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * What the command gate would do with one command for one player, and why.
 *
 * <p>The answer is produced by the same resolution the gate itself runs when the player types the command, so it
 * cannot drift from what actually happens. It is a snapshot all the same: it depends on the world the player is
 * standing in and the permissions they hold at that moment, and both can change a tick later.
 *
 * @param command the command root the answer is about, lowercase and without its leading slash, whatever form was
 *     asked about
 * @param allowed whether the command would run
 * @param rule which rule settled it
 * @param group the permission group whose own command list decided, or empty when the {@code default} list did
 *     (also empty for {@link UxmCommandRule#BYPASS}, where no list was read)
 * @param world the world whose per-world override decided, or empty when the server-wide rules did
 */
@NullMarked
public record UxmCommandCheck(
        String command, boolean allowed, UxmCommandRule rule, Optional<String> group, Optional<String> world) {

    public UxmCommandCheck {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(world, "world");
    }

    /** Whether the command would be stopped. The other side of {@link #allowed()}, for a readable call site. */
    public boolean blocked() {
        return !allowed;
    }
}

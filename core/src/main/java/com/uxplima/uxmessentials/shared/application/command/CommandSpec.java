package com.uxplima.uxmessentials.shared.application.command;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;

/**
 * One command the plugin ships, described once: the name it registers under, the aliases it answers to out of the
 * box, the node that gates it and the module that owns it.
 *
 * <p>The adapter builds the real Brigadier node; this record is what is published about it. The catalogue folds its
 * aliases into the defaults the command catalog resolves, the plugin checks every registration against it on enable,
 * and the command reference under {@code docs/user/} is generated from it, so the name, aliases and node a reader
 * sees are the ones the server registers.
 *
 * @param id the stable id the command catalog keys a command by, which is its default name unless a literal the
 *     catalog cannot use as an id stands in front of it ({@code twofactor} for {@code /2fa})
 * @param name the literal the command registers under when no operator renames it
 * @param aliases the extra literals it answers to by default, in the order they are offered
 * @param permission the node the command's root is gated by
 * @param owner the feature module that registers it, or empty for the kernel's own commands
 */
public record CommandSpec(String id, String name, List<String> aliases, String permission, Optional<ModuleId> owner) {

    private static final Pattern LITERAL = Pattern.compile("[a-z0-9]+");

    public CommandSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(owner, "owner");
        aliases = List.copyOf(aliases);
        requireLiteral(id);
        requireLiteral(name);
        Set<String> seen = new HashSet<>();
        seen.add(name);
        for (String alias : aliases) {
            requireLiteral(alias);
            if (!seen.add(alias)) {
                throw new IllegalArgumentException("command " + id + " lists " + alias + " twice");
            }
        }
        if (!permission.startsWith(SharedPermissions.NAMESPACE)) {
            throw new IllegalArgumentException("command " + id + " is gated outside the uxmessentials space");
        }
    }

    /** A module's command whose id is its name. */
    public static CommandSpec of(ModuleId owner, String name, String permission, String... aliases) {
        return new CommandSpec(name, name, List.of(aliases), permission, Optional.of(owner));
    }

    /** A module's command registered under a name that differs from its catalog id. */
    public static CommandSpec named(ModuleId owner, String id, String name, String permission, String... aliases) {
        return new CommandSpec(id, name, List.of(aliases), permission, Optional.of(owner));
    }

    /** A command the kernel registers rather than any one module: {@code /uxmess}, {@code /help}, {@code /lang}. */
    public static CommandSpec shared(String name, String permission, String... aliases) {
        return new CommandSpec(name, name, List.of(aliases), permission, Optional.empty());
    }

    /** The owning module's id, or {@code shared} for the kernel's commands. */
    public String area() {
        return owner.map(ModuleId::value).orElse("shared");
    }

    /** Every literal the command answers to by default: its name first, then its aliases. */
    public List<String> literals() {
        return Stream.concat(Stream.of(name), aliases.stream()).toList();
    }

    private static void requireLiteral(String literal) {
        if (!LITERAL.matcher(literal).matches()) {
            throw new IllegalArgumentException(
                    "a command literal is lower-case letters and digits: " + literal.toLowerCase(Locale.ROOT));
        }
    }
}

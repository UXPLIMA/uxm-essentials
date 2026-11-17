package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.condition.action.NameFill;
import org.jspecify.annotations.NullMarked;

/**
 * Fills the name of a player this server resolved itself, a player connected here, into an operator's line.
 *
 * <p>uxmLib's {@link NameFill} takes a name it cannot vouch for, such as a name a vote site sent, and so fills only
 * one an account could have: letters, digits and underscores, up to sixteen, after a dot or a star. A Bedrock bridge
 * may give its players another mark ({@code #Bea} is common) or a longer name, and every line that names such a
 * player was skipped. A player connected here has a name the bridge built, so this also takes a name of up to 32
 * letters, digits and underscores after up to three marks from a set that cannot change what a line does: none of
 * them is a space, a bracket, a quote, a brace, a bar, a percent sign, or a mark a command reads as a flag, a
 * coordinate or a selector ({@code -}, {@code ~}, {@code ^}, {@code @}). Any other name is still never filled in, and
 * the line that would carry it does not run.
 */
@NullMarked
public final class ResolvedNameFill {

    /** A name a Bedrock bridge gives a player: a few harmless marks, then the account's own letters. */
    private static final Pattern BRIDGED = Pattern.compile("[.*#!+$=:]{0,3}[A-Za-z0-9_]{1,32}");

    private ResolvedNameFill() {}

    /**
     * {@code line} with {@code name}, the name of a player connected here, filled in for every spelling
     * {@link NameFill#TOKENS} lists.
     *
     * @return the filled line, or empty when the line names the player and the name could change what it does
     */
    public static Optional<String> into(String line, String name) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(name, "name");
        if (NameFill.isAccountName(name) || !BRIDGED.matcher(name).matches()) {
            return NameFill.into(line, name);
        }
        String filled = line;
        for (String token : NameFill.TOKENS) {
            filled = filled.replace(token, name);
        }
        return Optional.of(filled);
    }
}

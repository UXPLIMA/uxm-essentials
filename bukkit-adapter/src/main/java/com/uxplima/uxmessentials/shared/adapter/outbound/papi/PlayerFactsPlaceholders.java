package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam for the {@code player_*} placeholders: the facts the server itself holds about an account, rather
 * than anything a feature module stores. Like the server metrics, this seam belongs to no context and is wired
 * unconditionally, so a scoreboard can read a ping or a first-join date on a server with every module switched
 * off.
 *
 * <p>It exposes primitives and small value types only; the {@link PlaceholderResolver} owns the rendering, so the
 * resolver test can exercise the whole family against a plain fake with no live server.
 */
public interface PlayerFactsPlaceholders {

    /** What the server holds about a connected player, or empty when they are not connected. */
    Optional<Session> session(PlayerRef who);

    /** What the server holds about any account it has seen, connected or not. */
    Optional<Account> account(PlayerRef who);

    /** The item in a hand, or empty when that hand is empty or the player is not connected. */
    Optional<HeldItem> held(PlayerRef who, Hand hand);

    /** How many of one material the player carries, or empty when the material is not one the server knows. */
    OptionalInt itemCount(PlayerRef who, String material);

    /** Where the player stands, or empty when they are not connected. */
    Optional<Position> position(PlayerRef who);

    /**
     * Where a connected player stands, for the relational distance keys.
     *
     * @param world the world they are in
     * @param x their x coordinate
     * @param y their y coordinate
     * @param z their z coordinate
     */
    record Position(String world, double x, double y, double z) {}

    /** Which hand a {@code hand_*} key is asking about. */
    enum Hand {
        MAIN,
        OFF
    }

    /**
     * A connected player's live session.
     *
     * @param ping round-trip time to the client, in milliseconds
     * @param sneaking whether they are crouching
     * @param sprinting whether they are running
     * @param op whether the server treats them as an operator
     * @param world the world they stand in
     * @param worldTime that world's time of day, in ticks
     * @param worldStorming whether it is raining in that world
     * @param worldThundering whether that world carries a thunderstorm
     * @param level their experience level
     * @param totalExperience the experience points they hold in total
     * @param experienceToNextLevel how many points remain before the next level
     * @param experienceProgress how far through the current level they are, from 0 to 1
     */
    record Session(
            int ping,
            boolean sneaking,
            boolean sprinting,
            boolean op,
            String world,
            long worldTime,
            boolean worldStorming,
            boolean worldThundering,
            int level,
            int totalExperience,
            int experienceToNextLevel,
            float experienceProgress) {}

    /**
     * What the server remembers about an account whether or not it is connected.
     *
     * @param firstPlayed when the account first joined, or empty when the server has never seen it
     * @param lastSeen when the account was last connected, or empty while it is connected now
     * @param playtime how long the account has played in total
     * @param banned whether the server's own ban list holds it
     */
    record Account(Optional<Instant> firstPlayed, Optional<Instant> lastSeen, Duration playtime, boolean banned) {}

    /**
     * The item in a hand.
     *
     * @param type the material id, lower-cased
     * @param name the item's display name, or its material id when it carries none
     * @param amount how many are in the stack
     * @param damage how much durability has been spent
     * @param maxDurability the item's durability ceiling, or zero when it takes no damage
     * @param enchantments the enchantments on it, each as {@code name level}
     * @param lore the lore lines, already stripped of formatting
     * @param model the custom model data on it, or empty when it carries none
     */
    record HeldItem(
            String type,
            String name,
            int amount,
            int damage,
            int maxDurability,
            List<String> enchantments,
            List<String> lore,
            OptionalInt model) {

        public HeldItem {
            enchantments = List.copyOf(enchantments);
            lore = List.copyOf(lore);
        }
    }
}

package com.uxplima.uxmessentials.api.action;

import java.util.Optional;

/**
 * Everything uxmEssentials can be asked to do, on behalf of one plugin.
 *
 * <p>Obtained from the front door with your own plugin, which is what the audit log will name:
 *
 * <pre>{@code
 * UxmActions actions = api.actions(this);
 * actions.economy().ifPresent(economy ->
 *     economy.deposit(playerId, new BigDecimal("50"))
 *         .thenAccept(result -> result.ifFailed(failure -> getLogger().warning(failure.message()))));
 * }</pre>
 *
 * <p>Every accessor is an {@link Optional} for the same reason the queries are: empty means the module is off,
 * which is a different thing from the operation failing, and worth telling apart in a log line.
 */
public interface UxmActions {

    /** Moving money, or empty when the economy module is switched off. */
    Optional<UxmEconomyActions> economy();

    /** Setting and removing homes, or empty when the homes module is switched off. */
    Optional<UxmHomeActions> homes();

    /** Creating and removing warps, or empty when the warps module is switched off. */
    Optional<UxmWarpActions> warps();

    /** Handing out kits, or empty when the kits module is switched off. */
    Optional<UxmKitActions> kits();

    /** Handing down and lifting punishments, or empty when the moderation module is switched off. */
    Optional<UxmModerationActions> moderation();

    /** Setting the flags a player carries, or empty when the playerstate module is switched off. */
    Optional<UxmPlayerStateActions> playerState();

    /** Marking a player away, or empty when the presence module is switched off. */
    Optional<UxmPresenceActions> presence();

    /** Hiding a player, or empty when the vanish module is switched off. */
    Optional<UxmVanishActions> vanish();

    /** Moving a player, or empty when the teleport module is switched off. */
    Optional<UxmTeleportActions> teleport();

    /** Loading and unloading worlds, or empty when the worlds module is switched off. */
    Optional<UxmWorldsActions> worlds();

    /** Promoting, setting and prestiging a rank, or empty when the ranks module is switched off. */
    Optional<UxmRanksActions> ranks();

    /** Changing a Discord binding, or empty when the discordlink module is switched off. */
    Optional<UxmDiscordLinkActions> discordLink();

    /** Crediting votes, or empty when the vote module is switched off. */
    Optional<UxmVoteActions> vote();

    /** Sending messages and mail, or empty when the messaging module is switched off. */
    Optional<UxmMessagingActions> messaging();
}

"""The moderation menu lore, written in the canonical skeleton."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, note, row


def punish(title, what, verb, silent=False):
    """A punish button on the confirm panel: the loud and the silent form read the same apart from the broadcast."""
    tail = "without telling the rest of the server." if silent else "and announce it to staff."
    return lore(
        title,
        "punishment",
        [f"{what} {tail}"],
        actions=[action("click", verb)],
    )


BLOCKS = {
    "moderation.gui.list.entry-lore": lore(
        "Punishment",
        "in force now",
        ["a punishment this player is serving right now."],
        [
            row("type", "{mod_active_type}"),
            row("issued by", "{mod_active_issuer}"),
            row("reason", "{mod_active_reason}"),
            row("remaining", "{mod_active_remaining}", "muted"),
        ],
        [action("click", "to manage this punishment")],
    ),
    "moderation.gui.history.entry-lore": lore(
        "Record",
        "past punishment",
        ["one entry from this player's history."],
        [
            row("action", "{mod_history_action}"),
            row("by", "{mod_history_issuer}"),
            row("reason", "{mod_history_reason}"),
            row("when", "{mod_history_at}", "muted"),
        ],
    ),
    "moderation.gui.confirm.target-lore": lore(
        "Target",
        "who this hits",
        ["the player every button on this panel applies to."],
    ),
    "moderation.gui.confirm.ban-lore": punish("Ban", "shut this player out of the server", "to ban"),
    "moderation.gui.confirm.ban-silent-lore": punish(
        "Silent Ban", "shut this player out of the server", "to ban silently", silent=True),
    "moderation.gui.confirm.mute-lore": punish("Mute", "stop this player speaking in chat", "to mute"),
    "moderation.gui.confirm.mute-silent-lore": punish(
        "Silent Mute", "stop this player speaking in chat", "to mute silently", silent=True),
    "moderation.gui.confirm.tempban-lore": punish(
        "Temporary Ban", "shut this player out for the chosen duration", "to tempban"),
    "moderation.gui.confirm.tempban-silent-lore": punish(
        "Silent Tempban", "shut this player out for the chosen duration", "to tempban silently", silent=True),
    "moderation.gui.confirm.tempmute-lore": punish(
        "Temporary Mute", "stop this player speaking for the chosen duration", "to tempmute"),
    "moderation.gui.confirm.tempmute-silent-lore": punish(
        "Silent Tempmute", "stop this player speaking for the chosen duration", "to tempmute silently", silent=True),
    "moderation.gui.confirm.warn-lore": punish("Warn", "put a warning on this player's record", "to warn"),
    "moderation.gui.confirm.warn-silent-lore": punish(
        "Silent Warn", "put a warning on this player's record", "to warn silently", silent=True),
    "moderation.gui.confirm.banip-lore": lore(
        "Ban Address",
        "punishment",
        ["ban the last address this player connected from, so a new account on it is shut out too."],
        actions=[action("click", "to ban the address")],
    ),
    "moderation.gui.confirm.reason-set-lore": lore(
        "Reason",
        "punishment detail",
        ["the reason the punished player is shown, and the one that goes on their record."],
        [row("reason", "{mod_confirm_reason}")],
        [action("click", "to change the reason")],
    ),
    "moderation.gui.confirm.reason-none-lore": lore(
        "Reason",
        "punishment detail",
        ["no reason set yet: without one, the player is told only what happened."],
        actions=[action("click", "to add a reason")],
    ),
    "moderation.gui.jail.footer-jails-lore": lore(
        "Jails",
        "jail management",
        ["every jail defined on this server, and where each one sits."],
        actions=[action("click", "to manage the jails")],
    ),
    "moderation.gui.jail.footer-jailed-lore": lore(
        "Jailed Players",
        "jail management",
        ["who is serving a jail sentence right now."],
        actions=[action("click", "to view and release")],
    ),
    "moderation.gui.jail.choose-entry-lore": lore(
        "Jail",
        "pick a destination",
        ["send the player you are punishing to this jail."],
        [row("jail", "{jail}")],
        [action("click", "to choose this jail")],
    ),
    "moderation.gui.jail.list-entry-lore": lore(
        "Jail",
        "a defined jail",
        ["a location players are held at while they serve a sentence."],
        [
            row("name", "{jail}"),
            row("at", "{coords}"),
        ],
        [action("click", "to edit this jail")],
    ),
    "moderation.gui.jail.edit-anchor-lore": lore(
        "Re-anchor",
        "jail editor",
        ["move jail {mod_jail_edit_jail} to where you are standing."],
        actions=[action("click", "to re-anchor")],
    ),
    "moderation.gui.jail.edit-goto-lore": lore(
        "Visit",
        "jail editor",
        ["teleport to jail {mod_jail_edit_jail} to see it for yourself."],
        [row("at", "{mod_jail_edit_coords}")],
        [action("click", "to teleport there")],
    ),
    "moderation.gui.jail.edit-delete-lore": lore(
        "Delete",
        "jail editor",
        ["remove jail {mod_jail_edit_jail}: players held there are not released by this."],
        actions=[action("click", "to delete this jail")],
    ),
    "moderation.gui.jail.jailed-entry-lore": lore(
        "Jailed",
        "serving a sentence",
        ["a player being held in a jail right now."],
        [
            row("jail", "{mod_jailed_jail}"),
            row("issued by", "{mod_jailed_issuer}"),
            row("reason", "{mod_jailed_reason}"),
            row("remaining", "{mod_jailed_remaining}", "muted"),
        ],
        [action("click", "to release this player")],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)

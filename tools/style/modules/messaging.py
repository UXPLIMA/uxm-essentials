"""The messaging menu lore: the ignore list and the mailbox."""

import sys

sys.path.insert(0, "tools/style")

from apply_lore import apply
from lore import action, lore, note, row

BLOCKS = {
    "messaging.gui.ignore.entry-lore": lore(
        "Ignored",
        "your ignore list",
        ["a player whose messages never reach you."],
        [row("player", "{ignore_target}")],
        [action("click", "to hear from them again")],
    ),
    "messaging.gui.mail.entry-lore": lore(
        "Letter",
        "your mailbox",
        ["mail somebody left for you while you were away."],
        [
            row("from", "{mail_sender}"),
            row("sent", "{mail_time}", "muted"),
            row("opening", "{mail_snippet}", "subtext"),
        ],
        [action("click", "to read it")],
    ),
    "messaging.gui.mail.detail-lore": lore(
        "Letter",
        "your mailbox",
        [note("{mail_message}", "body")],
        [
            row("from", "{mail_sender}"),
            row("sent", "{mail_time}", "muted"),
        ],
    ),
}

if __name__ == "__main__":
    apply(BLOCKS)

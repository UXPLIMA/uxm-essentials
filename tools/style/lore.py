"""Build a menu item's lore in the shape the style canon fixes.

Every descriptive lore block reads the same way: a breadcrumb saying what kind of thing this is, a
description section, an optional information section of labelled facts, and the actions a click
performs. This renders that skeleton from its parts so the 300-odd blocks in the catalog cannot drift
from one another, and writes the fixed words in small capitals because that is how the interface is
written.

The diamond title line above the breadcrumb is not written here. The renderer puts it there from the
tile's own name, which is what lets one shared block ("one of this NPC's settings") open on the
setting the player is actually looking at.
"""

ASCII = "abcdefghijklmnopqrstuvwxyz"
SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

DESCRIPTION = "description"
INFORMATION = "information"


def sc(text):
    """The small-capital form of a fixed word; digits, symbols and placeholders pass through."""
    out = []
    skip = False
    for i, ch in enumerate(text):
        if ch == "{":
            skip = True
        if ch == "}":
            skip = False
            out.append(ch)
            continue
        if skip or not (ch.isascii() and ch.isalpha()) or ch.lower() == "x":
            out.append(ch)
            continue
        out.append(SMALL[ASCII.index(ch.lower())])
    return "".join(out)


def row(label, value, tone="value"):
    """A labelled fact: the dim bullet, the white label, then the value in its own tone."""
    return f"    <icon>•</icon> <body>{sc(label)}</body> <{tone}>{value}</{tone}> "


def note(text, tone="subtext"):
    """A free line inside a section, for a fact that has no label."""
    return f"    <{tone}>{sc(text)}</{tone}> "


def action(verb, rest, tone="cta"):
    """One click line: the arrow, the click word, then what the click does."""
    return f" <icon>→</icon> <{tone}>{sc(verb)}</{tone}> <subtext>{sc(rest)}</subtext> "


def lore(crumb, description=(), information=(), actions=()):
    lines = [f"    <crumb>{sc(crumb)}</crumb> "]
    if description:
        lines.append("")
        lines.append(f" <icon>✎</icon> <info>{sc(DESCRIPTION)}</info> ")
        lines.extend(note(line) if isinstance(line, str) else line for line in description)
    if information:
        lines.append("")
        lines.append(f" <icon>≡</icon> <info>{sc(INFORMATION)}</info> ")
        lines.extend(information)
    if actions:
        lines.append("")
        lines.extend(actions)
    return "<newline>".join(lines)

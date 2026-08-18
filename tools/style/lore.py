"""Build a menu item's lore in the shape the style canon fixes.

Every descriptive lore block reads the same way: a breadcrumb saying what kind of thing this is, a
description section wrapped to a readable width, an optional information section of labelled facts,
and the actions a click performs. This renders that skeleton from its parts so the 300-odd blocks in the catalog cannot drift
from one another, and writes the fixed words in small capitals because that is how the interface is
written.

The diamond title line above the breadcrumb is not written here, and neither is the blank line that
closes the block. The renderer adds both: the title comes from the tile's own name, which is what lets
one shared block ("one of this NPC's settings") open on the setting the player is actually looking at,
and the closing blank is the same line of air the blank display name buys at the top.
"""

ASCII = "abcdefghijklmnopqrstuvwxyz"
SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

DESCRIPTION = "description"
INFORMATION = "information"

# How wide a description line is allowed to get, in characters. A tooltip will happily draw a line
# twice this long, but a line that runs past the item grid is read as a paragraph rather than as a
# label, so a description is broken into lines of about this width and balanced across them.
WRAP_WIDTH = 34


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


def wrap(text, width=WRAP_WIDTH):
    """`text` broken into lines of at most `width` characters, as evenly as they will divide.

    Greedy filling alone leaves a last line holding one word. Once the number of lines is known, the
    narrowest width that still fills that many lines spreads the words out instead, so no line is left
    a straggler.
    """
    words = text.split()
    if len(words) <= 1:
        return [text.strip()]

    def fill(limit):
        lines = []
        current = ""
        for word in words:
            if current and len(current) + 1 + len(word) > limit:
                lines.append(current)
                current = word
            else:
                current = f"{current} {word}" if current else word
        if current:
            lines.append(current)
        return lines

    greedy = fill(width)
    for limit in range(max(len(word) for word in words), width):
        balanced = fill(limit)
        if len(balanced) == len(greedy):
            return balanced
    return greedy


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
        sentences = " ".join(line for line in description if isinstance(line, str))
        lines.extend(note(line) for line in wrap(sentences) if line)
        lines.extend(line for line in description if not isinstance(line, str))
    if information:
        lines.append("")
        lines.append(f" <icon>≡</icon> <info>{sc(INFORMATION)}</info> ")
        lines.extend(information)
    if actions:
        lines.append("")
        lines.extend(actions)
    return "<newline>".join(lines)

#!/usr/bin/env python3
"""Reading a message catalog: the one parser the i18n tools share.

A catalog is a flat HOCON file of quoted key / quoted value pairs, one per line, with comment
lines between the groups. Nothing else appears in it, which is what lets these tools treat a
line as text rather than parse HOCON.
"""

import re
from pathlib import Path

VALUE_LINE = re.compile(r'^"(?P<key>[^"]+)"\s*=\s*"(?P<value>.*)"\s*$')
META_PREFIX = "meta."

SMALL_CAPITALS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ"


def read(path: Path) -> dict[str, str]:
    """Every message in the file, in the order it is written, without the meta settings."""
    messages = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = VALUE_LINE.match(line)
        if match is not None and not match.group("key").startswith(META_PREFIX):
            messages[match.group("key")] = match.group("value")
    return messages


def meta(path: Path) -> dict[str, str]:
    """The meta settings only: the typography flag lives here, not among the messages."""
    settings = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = VALUE_LINE.match(line)
        if match is not None and match.group("key").startswith(META_PREFIX):
            settings[match.group("key")] = match.group("value")
    return settings


def placeholders(value: str) -> list[str]:
    return sorted(re.findall(r"\{[a-z0-9-]+\}", value))


# The header tag is the one whose argument is a sentence the player reads, so a translation changes it.
# The badge tags name a module or a state and stay as English writes them, which is why they are compared
# whole: a catalog that renames ERROR in one chunk and keeps it in the next reads as two products.
TRANSLATABLE_ARGUMENT = re.compile(r"^<(h):.*>$")


def tags(value: str) -> list[str]:
    """The palette tags a line uses, compared as a multiset because word order is the translator's."""
    found = []
    for tag in re.findall(r"<[^<>]*>", value):
        match = TRANSLATABLE_ARGUMENT.match(tag)
        found.append(f"<{match.group(1)}:>" if match is not None else tag)
    return sorted(found)


def plain_spans(value: str) -> list[str]:
    return sorted(re.findall(r"<plain>(.*?)</plain>", value))


def commands(value: str) -> list[str]:
    """The /command tokens a line names. They are typed by a player, so they never translate."""
    return sorted(set(re.findall(r"/[a-z][a-z0-9-]*", value)))

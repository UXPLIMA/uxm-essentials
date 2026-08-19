#!/usr/bin/env python3
"""Rewrite a message catalog in plain letters, and prove the render-time typography puts it back.

The catalog used to carry its small-capital letters in the file, which made it unreadable for a
translator and impossible to write in a language that has no small capitals. The letters are applied
at load time now, so the file itself is ordinary text.

    plain_text.py <catalog>            convert in memory and report every line the transform cannot
                                       reproduce byte for byte
    plain_text.py <catalog> --write    convert the file in place, refusing to write when a line does
                                       not round-trip

A run of ordinary letters that the original kept in ordinary letters (a state word such as "free"
standing where a number usually stands, or a subcommand inside a command) is wrapped in <plain>,
which is the tag that tells the transform to leave it alone.
"""

import argparse
import re
import sys
from pathlib import Path

SMALL_TO_ASCII = {
    "ᴀ": "a", "ʙ": "b", "ᴄ": "c", "ᴅ": "d", "ᴇ": "e", "ꜰ": "f", "ɢ": "g", "ʜ": "h",
    "ɪ": "i", "ᴊ": "j", "ᴋ": "k", "ʟ": "l", "ᴍ": "m", "ɴ": "n", "ᴏ": "o", "ᴘ": "p",
    "ǫ": "q", "ʀ": "r", "ꜱ": "s", "ᴛ": "t", "ᴜ": "u", "ᴠ": "v", "ᴡ": "w", "ʏ": "y",
    "ᴢ": "z",
}
ASCII_TO_SMALL = {ascii_letter: small for small, ascii_letter in SMALL_TO_ASCII.items()}

VALUE_LINE = re.compile(r'^("(?P<key>[^"]+)"\s*=\s*)"(?P<value>.*)"\s*$')
PLAIN_OPEN = "<plain>"
PLAIN_CLOSE = "</plain>"


def small_caps(text: str) -> str:
    return "".join(ASCII_TO_SMALL.get(character.lower(), character) if character.isascii() and character.isalpha() else character for character in text)


def apply_transform(template: str) -> str:
    """The Java SmallCapsTemplates.apply, mirrored so the round trip can be proved here."""
    out = []
    index = 0
    while index < len(template):
        if template.startswith(PLAIN_OPEN, index):
            close = template.find(PLAIN_CLOSE, index)
            end = len(template) if close < 0 else close + len(PLAIN_CLOSE)
            out.append(template[index:end])
            index = end
        elif template[index] in "<{":
            closing = ">" if template[index] == "<" else "}"
            close = template.find(closing, index)
            end = len(template) if close < 0 else close + 1
            out.append(template[index:end])
            index = end
        else:
            end = index
            while end < len(template) and template[end] not in "<{":
                end += 1
            out.append(small_caps(template[index:end]))
            index = end
    return "".join(out)


def to_plain(value: str) -> str:
    """The inverse: small capitals become letters, and a run that was already letters is marked."""
    out = []
    index = 0
    while index < len(value):
        if value[index] in "<{":
            closing = ">" if value[index] == "<" else "}"
            close = value.find(closing, index)
            end = len(value) if close < 0 else close + 1
            out.append(value[index:end])
            index = end
        else:
            end = index
            while end < len(value) and value[end] not in "<{":
                end += 1
            out.append(plain_prose(value[index:end]))
            index = end
    return "".join(out)


def plain_prose(text: str) -> str:
    """Small capitals become letters; a run that was already letters is marked as not-prose."""
    out = []
    marked = []
    for piece in re.split(r"(\s+)", text):
        if piece.strip() == "":
            marked.append((piece, None))
            continue
        if any(character in SMALL_TO_ASCII for character in piece):
            marked.append(("".join(SMALL_TO_ASCII.get(character, character) for character in piece), False))
        elif small_caps(piece) != piece:
            marked.append((piece, True))
        else:
            marked.append((piece, None))

    run = []
    for piece, keep in marked + [("", "end")]:
        if keep is True:
            run.append(piece)
            continue
        if run:
            out.append(f"{PLAIN_OPEN}{' '.join(run)}{PLAIN_CLOSE}")
            run = []
        if keep != "end":
            out.append(piece)
    return "".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--write", action="store_true", help="rewrite the file in place")
    parser.add_argument(
        "--accept-fixes",
        action="store_true",
        help="allow lines whose only difference is a word the catalog left half converted",
    )
    arguments = parser.parse_args()

    source = arguments.catalog.read_text(encoding="utf-8")
    rewritten = []
    problems = []
    for number, line in enumerate(source.splitlines(), start=1):
        match = VALUE_LINE.match(line)
        if match is None:
            rewritten.append(line)
            continue
        original = match.group("value")
        plain = to_plain(original)
        # The <plain> markers are the new part of the file, so they are stripped before the
        # comparison: what must match today's catalog is the text, not the marks around it.
        produced = apply_transform(plain).replace(PLAIN_OPEN, "").replace(PLAIN_CLOSE, "")
        if produced != original:
            problems.append((number, match.group("key"), original, produced))
        rewritten.append(f'{match.group(1)}"{plain}"')

    if problems and not arguments.accept_fixes:
        print(f"{len(problems)} line(s) do not round-trip:")
        for number, key, original, produced in problems[:20]:
            print(f"  line {number} [{key}]")
            print(f"    was: {original}")
            print(f"    got: {produced}")
        return 1

    if problems:
        print(f"{len(problems)} line(s) whose typography the conversion evens out:")
        for _, key, _, _ in problems:
            print(f"  {key}")

    print(f"{len(rewritten)} line(s) checked, every value round-trips")
    if arguments.write:
        arguments.catalog.write_text("\n".join(rewritten) + "\n", encoding="utf-8")
        print(f"wrote {arguments.catalog}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Check a translated catalog against the English one.

What a translation is free to change is the prose. Everything the plugin or the player depends on
stays exactly as English writes it: the key, the {placeholder} names, the palette tags, the text
inside <plain> (a command a player types, a permission node, a state word), and the /command
tokens a line mentions. Word order is free, so the tags are compared as a multiset rather than a
sequence: Turkish and Japanese put the verb last, and forcing English order would read as a
machine translation.

    check_catalog.py <en.conf> <translated.conf>
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog

# The one character the house style bans outright, named rather than typed.
EM_DASH = "\u2014"


def problems_for(key: str, source: str, translated: str) -> list[str]:
    found = []
    if catalog.placeholders(source) != catalog.placeholders(translated):
        found.append(
            f"placeholders differ: {catalog.placeholders(source)} vs {catalog.placeholders(translated)}"
        )
    if catalog.tags(source) != catalog.tags(translated):
        found.append(f"tags differ: {catalog.tags(source)} vs {catalog.tags(translated)}")
    if catalog.plain_spans(source) != catalog.plain_spans(translated):
        found.append(
            f"<plain> text differs: {catalog.plain_spans(source)} vs {catalog.plain_spans(translated)}"
        )
    missing = [command for command in catalog.commands(source) if command not in translated]
    if missing:
        found.append(f"command name(s) missing: {' '.join(missing)}")
    small = sorted({character for character in translated if character in catalog.SMALL_CAPITALS})
    if small:
        found.append(f"small capital letter(s) in the text: {''.join(small)}")
    if EM_DASH in translated:
        found.append("em dash in the text")
    if "§" in translated:
        found.append("legacy section colour code in the text")
    return [f"{key}: {reason}" for reason in found]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("english", type=Path)
    parser.add_argument("translated", type=Path)
    arguments = parser.parse_args()

    source = catalog.read(arguments.english)
    translated = catalog.read(arguments.translated)

    problems = []
    for key in source:
        if key not in translated:
            problems.append(f"{key}: missing from {arguments.translated.name}")
    for key in translated:
        if key not in source:
            problems.append(f"{key}: not a key of {arguments.english.name}")
    for key, value in source.items():
        if key in translated:
            problems.extend(problems_for(key, value, translated[key]))

    if problems:
        print(f"{arguments.translated.name}: {len(problems)} problem(s)")
        for problem in problems[:40]:
            print(f"  {problem}")
        if len(problems) > 40:
            print(f"  ... and {len(problems) - 40} more")
        return 1

    print(f"{arguments.translated.name}: {len(source)} keys checked, 0 problems")
    return 0


if __name__ == "__main__":
    sys.exit(main())

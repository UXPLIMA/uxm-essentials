#!/usr/bin/env python3
"""check-docs.py - documentation index guard.

Fails when `docs/00-README.md` and the contents of `docs/` disagree, in either
direction:

  * the index names a document that does not exist, or
  * a document exists that the index does not name.

The first case is the failure that rotted the plugin canon: its index pointed at
twelve GLOSSARY.md files, and none of them had ever been written. Nothing failed,
so nobody saw it.

Exit codes:
  0  the index and the directory agree
  1  they disagree
  2  the check could not run
"""

import re
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
INDEX = DOCS / "00-README.md"


def main() -> int:
    if not INDEX.is_file():
        print(f"check-docs: {INDEX} not found", file=sys.stderr)
        return 2

    text = INDEX.read_text(encoding="utf-8")

    on_disk = {f.name for f in DOCS.glob("[0-9][0-9]-*.md")}
    on_disk |= {d.name + "/" for d in DOCS.iterdir() if d.is_dir()}

    indexed = set(re.findall(r"`(\d{2}-[\w-]+\.md)`", text))
    # A backticked name with a trailing slash is a subdirectory reference, except for the
    # documentation directory itself: prose inside the index refers to `docs/` to mean the
    # directory that holds the index, not a child of it.
    indexed |= {n + "/" for n in re.findall(r"`([a-z]+)/`", text) if n != DOCS.name}

    promised = sorted(indexed - on_disk)
    unlisted = sorted(on_disk - indexed)

    if promised:
        print("The index names a document that does not exist:")
        for name in promised:
            print(f"  {name}")
    if unlisted:
        print("A document exists that the index does not name:")
        for name in unlisted:
            print(f"  {name}")

    if promised or unlisted:
        return 1

    print(f"check-docs: clean ({len(on_disk)} documents indexed)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

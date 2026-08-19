#!/usr/bin/env python3
"""Cut a catalog into chunks small enough for one translator to hold at once.

A chunk keeps the original lines verbatim, comments included, and is cut only at a blank line so a
group of related messages and the comment that introduces them stay together. A translator writes a
sibling file with the same key lines and translated values; assemble_catalog.py puts them back.

    split_catalog.py <catalog> <outdir> --chunk 200
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog


def chunks(lines: list[str], size: int) -> list[list[str]]:
    """Cut after at least `size` keys, at the next blank line, so a group is never split."""
    out = []
    current: list[str] = []
    keys = 0
    for line in lines:
        current.append(line)
        if catalog.VALUE_LINE.match(line):
            keys += 1
        if keys >= size and line.strip() == "":
            out.append(current)
            current = []
            keys = 0
    if any(line.strip() for line in current):
        out.append(current)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("outdir", type=Path)
    parser.add_argument("--chunk", type=int, default=200, help="keys per chunk")
    arguments = parser.parse_args()

    lines = arguments.catalog.read_text(encoding="utf-8").splitlines()
    arguments.outdir.mkdir(parents=True, exist_ok=True)
    for existing in arguments.outdir.glob("chunk-*.conf"):
        existing.unlink()

    parts = chunks(lines, arguments.chunk)
    for number, part in enumerate(parts, start=1):
        target = arguments.outdir / f"chunk-{number:02d}.conf"
        target.write_text("\n".join(part).rstrip("\n") + "\n", encoding="utf-8")
        keys = sum(1 for line in part if catalog.VALUE_LINE.match(line))
        print(f"{target} ({keys} keys)")
    print(f"{len(parts)} chunk(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Put translated chunks back together as one catalog.

The English file supplies the shape: its comments, its blank lines and its key order are kept, so a
translated catalog reads next to the original line for line and the parity gate has nothing to
complain about. Only the values come from the chunks, and a key nobody translated is an error, not
a silent English line.

    assemble_catalog.py <en.conf> <chunkdir> <out.conf> --small-caps false
"""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog

SMALL_CAPS_NOTE = [
    "# The typography this catalog is written in. Small capitals exist for the twenty-six Latin letters",
    "# only, so a language that uses anything else keeps its own letters and sets this to false.",
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("english", type=Path)
    parser.add_argument("chunkdir", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--small-caps", choices=("true", "false"), default="false")
    parser.add_argument("--language", default="English", help="the language named in the file header")
    arguments = parser.parse_args()

    translated = {}
    for chunk in sorted(arguments.chunkdir.glob("chunk-*.conf")):
        translated.update(catalog.read(chunk))

    lines = arguments.english.read_text(encoding="utf-8").splitlines()
    out = []
    missing = []
    comments: list[str] = []
    for line in lines:
        match = catalog.VALUE_LINE.match(line)
        if match is None:
            # Comments are held back so the note above a key can be replaced along with the key.
            if line.startswith("#"):
                comments.append(line)
            else:
                out.extend(comments)
                comments = []
                out.append(line)
            continue
        key = match.group("key")
        if key == "meta.small-caps":
            comments = []
            out.extend(SMALL_CAPS_NOTE)
            out.append(f'"meta.small-caps" = "{arguments.small_caps}"')
            continue
        out.extend(comments)
        comments = []
        if key.startswith(catalog.META_PREFIX):
            out.append(line)
            continue
        if key not in translated:
            missing.append(key)
            continue
        out.append(f'"{key}" = "{translated[key]}"')

    if missing:
        print(f"{len(missing)} key(s) were not translated:")
        for key in missing[:20]:
            print(f"  {key}")
        return 1

    out.extend(comments)
    if out and out[0].startswith("# English message catalog"):
        out[0] = out[0].replace("English", arguments.language, 1)
    arguments.out.write_text("\n".join(out).rstrip("\n") + "\n", encoding="utf-8")
    print(f"wrote {arguments.out} ({len(translated)} keys)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

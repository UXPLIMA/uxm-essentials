#!/usr/bin/env python3
"""Fill the generated tables on the uxmEssentials module pages.

Reads the model written by `./gradlew :bukkit-adapter:docsExport` and rewrites the text between
`{/* generated:x */}` and `{/* /generated */}` on each module page. Nothing outside those markers is
touched, so the prose on a page is written once, by hand, and never overwritten. The markers are MDX
comments because the docs platform compiles every page as MDX, where an HTML comment is a parse error.
"""

import argparse
import json
import re
import sys
from pathlib import Path

SECTIONS = ("commands", "permissions", "settings", "placeholders")
DEFAULTS = {"TRUE": "everyone", "OP": "op", "FALSE": "off"}


def cell(text):
    """A description sits in a table cell, where a bare pipe ends the column, a bare `<` opens a JSX tag
    the docs platform then demands a closing tag for, and a bare `{` opens an expression it tries to
    parse as JavaScript. All three are escaped; inside backticks they are already inert. A description
    stitched together from several comment lines also arrives with runs of spaces, which are collapsed."""
    escaped, in_code = [], False
    for character in " ".join(text.split()):
        if character == "`":
            in_code = not in_code
        if character in "|<{" and not in_code:
            escaped.append("\\")
        escaped.append(character)
    return "".join(escaped)


def render_commands(module):
    rows = ["| Command | What it does | Permission |", "|---|---|---|"]
    for command in module["commands"]:
        literal = f"`/{command['literal']}`"
        if command["aliases"]:
            literal += " (" + ", ".join(f"`/{alias}`" for alias in command["aliases"]) + ")"
        permission = f"`{command['permission']}`" if command["permission"] else ""
        rows.append(f"| {literal} | {cell(command['description'])} | {permission} |")
    return "\n".join(rows)


def render_permissions(module):
    rows = ["| Node | Default | Grants |", "|---|---|---|"]
    for permission in module["permissions"]:
        default = DEFAULTS.get(permission["fallback"], permission["fallback"].lower())
        rows.append(f"| `{permission['node']}` | {default} | {cell(permission['description'])} |")
    return "\n".join(rows)


def render_settings(module):
    rows = ["| Key | Default | What it does |", "|---|---|---|"]
    for setting in module["settings"]:
        rows.append(f"| `{setting['key']}` | `{setting['value']}` | {cell(setting['description'])} |")
    return "\n".join(rows)


def render_placeholders(module):
    rows = ["| Placeholder | Renders |", "|---|---|"]
    for placeholder in module["placeholders"]:
        rows.append(f"| `%uxmessentials_{placeholder['key']}%` | {cell(placeholder['description'])} |")
    return "\n".join(rows)


RENDERERS = {
    "commands": render_commands,
    "permissions": render_permissions,
    "settings": render_settings,
    "placeholders": render_placeholders,
}


def blocks_for(module):
    return {name: RENDERERS[name](module) for name in SECTIONS if module[name]}


def rewrite(text, blocks):
    found = set(re.findall(r"\{/\* generated:([a-z]+) \*/\}", text))
    if found != set(blocks):
        raise ValueError(f"markers {sorted(found)} do not match sections {sorted(blocks)}")
    for name, body in blocks.items():
        # The body may not contain a marker of its own, otherwise an empty pair would swallow
        # everything up to the next section's closing marker.
        pattern = re.compile(
            r"(\{/\* generated:" + name + r" \*/\}\n)"
            + r"(?:(?!\{/\*)[\s\S])*"
            + r"(\{/\* /generated \*/\})"
        )
        text, count = pattern.subn(lambda match: match.group(1) + body + "\n" + match.group(2), text)
        if count != 1:
            raise ValueError(f"marker {name} appears {count} times")
    return text


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("data", type=Path, help="docs-data.json written by the docsExport task")
    parser.add_argument("content", type=Path, help="content root holding minecraft/plugins/uxmessentials")
    parser.add_argument(
        "--require-all", action="store_true", help="fail when a registered module has no page yet"
    )
    args = parser.parse_args(argv)

    modules = json.loads(args.data.read_text(encoding="utf-8"))["modules"]
    pages = args.content / "minecraft" / "plugins" / "uxmessentials" / "modules"
    written, missing = 0, []
    for module in modules:
        page = pages / f"{module['id']}.md"
        if not page.exists():
            missing.append(module["id"])
            continue
        text = page.read_text(encoding="utf-8")
        try:
            updated = rewrite(text, blocks_for(module))
        except ValueError as error:
            print(f"{page}: {error}", file=sys.stderr)
            return 1
        if updated != text:
            page.write_text(updated, encoding="utf-8")
            written += 1
    if missing and args.require_all:
        print("no page for: " + ", ".join(missing), file=sys.stderr)
        return 1
    print(f"{written} page(s) updated, {len(missing)} without a page yet")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

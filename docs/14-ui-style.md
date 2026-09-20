# 14. UI style

What every window and every line of text this plugin draws has in common. It is written from
the code that cites it, and three guards keep the parts that drift.

## 14.1 One look, not thirty four looks

A module does not get to have a style. The palette, the tile grammar, the lore layout and the
sounds are the plugin's, so a player who learns one window has learned all of them. A new
window that looks like its author is a defect, however good it looks.

The palette is `theme.conf`, and it is shared with the other plugins of this family: the
second plugin a server installs finds the file the first one wrote. Nothing hardcodes a
colour, and nothing writes a legacy colour code.

## 14.2 MiniMessage, and no legacy codes

Every line a player reads is MiniMessage, and every one of them comes out of the catalogue.
`ChatColor` and the section-sign codes are forbidden, and `LegacyChatApiDriftTest` fails the
build over either.

The reason is restyling, not taste. A legacy code baked into a message is a colour no
operator can change from the palette afterwards, and mixing the two in one string produces a
line whose second half ignores the theme.

## 14.3 A window announces itself

Every shipped menu opens with a sound. A window that appears in silence reads as a window
that half loaded, so the opening page turn is part of the look rather than a per-menu
decision, and `MenuOpenSoundDriftTest` fails a spec that forgets it.

Click feedback is not declared per item. The engine plays it for every gesture it accepts, so
it cannot drift out of a spec.

## 14.4 The lore of an item is a layout, not a paragraph

A tile's lore is built in the same order everywhere: the description, then the information
rows that carry values, then the action lines that say what a click does. The section headers
are the small-caps words the catalogue holds, so a translator gets them for free.

## 14.5 A description line is about thirty four characters

A tooltip draws a line of any length, so nothing stops a description from running the width
of the screen. At that width it stops reading as a label and starts reading as a paragraph.

The canon wraps a description at about thirty four characters, balanced across the lines,
which is what the generator does when it writes a block. `LoreDescriptionWidthDriftTest` is
the other half: a line somebody hand edited into the shipped catalogue is held to the same
width, with a word's worth of slack, and fails the build past thirty eight characters.

Only the description is measured. An information row carries a placeholder whose value is
unknown at build time, and an action line is one short phrase by construction.

## 14.6 Bedrock is not a second design

A window a Bedrock player cannot use is unfinished, not degraded. The form is drawn from the
same spec, so a menu gains its Bedrock face by existing rather than by somebody remembering.

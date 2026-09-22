# The copy map: the 37 names this plugin shares with uxmLib

Written 2026-09-22, before any porting, because the porting order depends on it.

Thirty seven simple names exist in both trees. That number is not a coincidence of counting: it
is every class name that appears in `uxm-lib/*/src/main/java` and in this repository at once.
**Thirty six of the thirty seven take nothing from the library.** Only
`holograms/adapter/inbound/gui/ActionProperty` imports a uxmLib type at all.

A shared name is not a duplicate. Three of the four groups below are names that collide and
jobs that do not, and porting one of those would be a defect rather than a cleanup. The group
that matters is the first.

## A. The same thing, and the library's should win

### Verified by reading, ready to port

| Class | This repository | uxmLib |
|---|---|---|
| `HealthResult` | `core/shared/application/health` | `uxmlib-common/health` |
| `HealthStatus` | `core/shared/application/health` | `uxmlib-common/health` |
| `HealthCheck` | `core/shared/application/health` | `uxmlib-common/health` |
| `HealthReport` | `core/shared/application/health` | `uxmlib-common/health` |

`HealthResult` is the same record with the same three factories. `HealthStatus` is the same
three constants; this repository adds `worst(other)` and the library does the same comparison
inside `HealthReport`. `HealthCheck` is the same pair of methods, with `name()` abstract here
and defaulted there, and the safe wrapper called `safe()` here and `safely()` there.
`HealthReport` is the same record with the same `run`, `overall` and `hasFailure`.

**Blast radius: 30 files**, 16 in `bukkit-adapter`, 11 in `core`, 3 in `persistence-adapter`.
This is the first port and it is the one item 1 of the standing order names third.

### Looks identical from its own first sentence, confirm before porting

| Class | Why it looks the same |
|---|---|
| ~~`Tiles`~~ | Confirmed and **rejected**, but it found a defect. See below |
| ~~`MenuTitles`~~ | Confirmed and **rejected**, see below |
| ~~`EquipmentSlot`~~ | Identical, and blocked by the core rule. See below |
| ~~`SerializedItems`~~ | Confirmed and **ported**, see below |
| ~~`Comparison`~~ | Confirmed and **ported**, on correctness. See below |

**`SerializedItems` carries a data compatibility risk and nothing else here does.** A stored
click-action payload was written by this plugin's codec; if the two formats differ by a byte,
porting it silently breaks every action already saved on every server. Read both formats before
touching it, and if they differ the answer is a reader that accepts both, not a port.

## B. The same name, a different job. Keep both

| Class | Here | In the library |
|---|---|---|
| `Hologram`, `HologramLine`, `Appearance`, `Billboard`, `Transform` | This plugin **is** the hologram product: 571 lines of domain | A consumer seam onto whichever hologram plugin is installed |
| `Wallet` | An economy module's own balance model | The spend seam a condition takes money through |
| `Json` | A REST body codec | An update check's tiny parser |
| `RateLimiter` | REST authentication throttling | Item click throttling |
| `Result` | A domain outcome | One row of a SQL result |
| `RowMapper` | The standalone migration tool's, which must not depend on a plugin runtime | The library's SQL one |
| `Cooldowns` | An application port | The command layer's annotation support |
| `NearbyPlayers` | What `/near` lists, nearest first | Who should currently see a hologram |
| `GuiLayout` | A browse menu's presentation, loaded from a file | Pure layout arithmetic |
| `GuiText` | Resolves a `MessageKey` into a `Component` in the viewer's locale | Where a menu gets its words: a seam the consumer answers |
| `MenuPlaceholders` | The read seam an expansion queries | The placeholders the engine can answer out of its own context |
| `PlayerNames` | A uuid to display name resolver for stored rows | The names of the players online, for a command argument |

**The five hologram types are the clearest case and the one most likely to be ported by
mistake.** The library's `Hologram` is 61 lines because it is a handle onto somebody else's
hologram. This one is 571 because it is the hologram.

## C. Ports this plugin declares by design

`Scheduler`, `Messages`, `MessageKey`, `ClaimProvider`, `RegionService`, `SkinTextures`,
`VaultEconomy`.

A hexagonal port is supposed to belong to the plugin that declares it: that is what keeps
`core` free of the server. **The question for each is not the port, it is the adapter behind
it**, and whether that adapter reimplements what uxmLib already does. That is a second audit and
it is per port rather than per name.

Item 1 of the standing order names `Scheduler` and `Messages` as ports to move. Read this way,
moving them means pointing their outbound adapters at `uxmlib-common`, not deleting the port.

## D. Wants something the library has not got

| Class | What is missing |
|---|---|
| `Hooks`, `PluginHook` | This plugin resolves each registered hook lazily and caches it, through a registry. The library's pair does presence checking and nothing else: 28 and 14 lines against 86 and 51 |
| `WorldGuardReflection` | 145 lines here against 71. This plugin reaches further into WorldGuard than the seam was built for |
| `WorldGuardRegionService` | 476 against 107. The regions module is a product feature here and a seam there |

For these, the honest answers are the two the workspace rules allow: this plugin keeps its own,
or the library grows the capability **as a mechanism** and this plugin builds on it. Neither is
free, and neither is decided here.

## What this map does not say

It does not say that a class in group A is worth porting on its own. Four health types are
thirty files of churn for no behaviour change, and the value is the fifth plugin that reads
them, not this one. The value of the port is that a doctor here answers in the same six lines
as every other doctor in the estate, which is a product property and not a tidiness one.

## Two constraints found while writing this, which change what a port is

### `core` takes no uxmLib, on purpose

`core/build.gradle.kts` depends on `:api` and jspecify and nothing else. `StaffPurityTest` names
`com.uxplima.uxmlib` in the same forbidden list as `org.bukkit` and `net.kyori`, so the rule is
not an accident of the dependency list: a core source may not import the library any more than
it may import the server.

**This decides the health port and it decides it against the obvious answer.** The four health
types live in `core/shared/application/health` and are read by eleven core files. Deleting them
in favour of `uxmlib-common`'s would either give `core` a library dependency it has refused, or
move eleven core files out of core. Neither is a cleanup.

The answer that keeps both properties is the one this repository already uses everywhere else:
**core keeps its port and the adapter bridges.** The doctor lives in `bukkit-adapter`, which
already takes uxmLib, so a dozen lines there turn a core `HealthCheck` into a
`uxmlib.health.HealthCheck` and the report an operator reads is the library's. Core stays pure
and the estate gets one health vocabulary.

**Read the rest of group A the same way.** For a plugin whose core is deliberately library-free,
porting means the adapter side delegates, not that the core type disappears. That is already
what group C says about `Scheduler` and `Messages`; it turns out to be the rule rather than the
exception. The classes in group A that can be ported outright are the ones already living in
`bukkit-adapter`: `Tiles`, `MenuTitles`, `SerializedItems`, `Comparison`.

### The doctor is deliberately untranslated, and that is the repository's call

Every other plugin in this estate renders its doctor through six catalogue keys, so a Turkish
operator reads a Turkish doctor. `UxmessCommand` renders `"[OK] "`, `"[WARN] "` and
`"uxmEssentials: health checks"` as literals, and says why in its own javadoc: "Output is
deliberately plain, operator-facing diagnostic text: the i18n MessageKey catalog backs
player-facing feature messages."

**That is a decision this repository has written down, so it stands.** The workspace rule is
that when a family rule and a repository disagree, the repository wins and the disagreement is
reported rather than settled in passing. It is reported here and in
`standards/PROGRESS.md`. What is worth the owner's attention is not which answer is right but
that the estate currently gives two answers to "what language is a doctor in", and an operator
running twenty seven of our plugins meets both.

## `MenuTitles`: confirmed, and it is not a port

The first entry taken off the "looks identical" list, and the reason is worth more than the
entry.

Both centre a chest title by the same arithmetic: a 176 pixel window, a title origin of 8, the
free space halved into spaces. They use different width tables underneath, `FontWidths` here and
`GlyphWidthTable` there, and **if those disagreed then a menu title in this plugin would sit at a
different offset from a menu title in every other plugin we ship, on the same server, in the same
font.** This estate has already paid for that once: `2026-08-29-a-title-centred-twice`.

They agree. `MenuTitleWidthAgreementTest` asks, over fourteen titles built from the characters
that actually differ in width, and it is kept: if either table drifts, the two plugins' menus
drift apart and that test says so on the next build.

**So the arithmetic is duplicated and the behaviour is not.** The library's version appends the
original component and keeps its styling; this one flattens to plain text, because the style
canon writes a menu title centred and bare, with no colour. Porting would give every menu title
in this plugin the colour the canon says it must not have.

Verdict: same arithmetic, different policy, and this plugin's policy is the canon's. Not a port.

**The general lesson, which changes how the rest of this list should be read.** The first
sentence of a class is what its author meant, not what it does. Two classes can open with the
same sentence, compute the same number, and differ on the one decision that matters. **Confirm a
"looks identical" by reading the behaviour at the end of the method, not the description at the
top of the file.** Four entries are still unconfirmed on that list, and `SerializedItems` is the
one where getting it wrong costs data rather than colour.

## `SerializedItems`: confirmed, and ported

The entry flagged as the one where getting it wrong costs data, and the flag was right: the two
codecs share the `b64:` prefix and **do not write the same bytes**. The library stamps a `UXMI`
magic, a format version and the server's data version in front of the payload; this plugin wrote
the bare payload. The same prefix meant two formats.

**It was safe in one direction only.** The library's reader takes a header-less blob as well as a
headered one, which is documented on `ItemSerialization.fromBase64` and is now pinned here by
`StoredItemTokensStillReadTest`: every click action, hologram and rank reward already stored on
every server keeps reading, and what is written from now on carries the header. The other
direction would have been a data loss: this plugin's reader handed `UXMI...` straight to the
deserialiser, which fails, and the item would have disappeared without a word.

That test is not a test of the codec. It is the reason the port was allowed, kept where somebody
will find it: if the library ever stops reading a header-less blob, it fails, and what fails with
it is every payload written before today.

**One thing I called a defect on the way was not one, and correcting it is the more useful
record.** `SerializedItems.decode` returns an `Optional` and throws on a damaged token, and I read
that as a signature not keeping its promise. It is keeping a different one: **empty means the
token is not mine**, so a caller walking a chain of icon providers passes it along, and the
exception means it was mine and is broken. `SerializedStackIconProviderTest` in uxmLib had already
written that down, from the caller's side, and I changed the method without reading the test that
guarded it. The change was reverted in uxmLib 0.101.0 and the codec's own test now states the rule
from the codec's side too.

**What the port needed instead was a catch here, and that is where the decision belongs.** An
icon provider chain has somewhere to pass a token along to; a click action resolving an item has
not, so `ItemActions.resolveItem` catches and treats a damaged payload as the skip its own javadoc
already promised. That is one call site, not a library change.

Eleven files were repointed and two classes deleted.

## `EquipmentSlot`: identical, and blocked for the same reason the health types are

The same six constants in the same order. This plugin adds a `parse(String)` that answers with an
empty rather than throwing; the library has none.

It lives in `core/npc/domain`, and `core` takes no uxmLib. So this is the health types again, and
it is worth naming the pattern rather than the instance: **group A splits by module, not by
similarity.** Nothing in `core` is portable however identical it is, and the only entries that
were ever candidates are the ones already living in `bukkit-adapter`.

That leaves group A with one port done, `SerializedItems`, and one rejected, `MenuTitles` and
`Tiles`.

## `Tiles`: not a port, and it found a defect anyway

Same job and three real differences. The library takes a `Theme` as an argument and reads the
glyph off it; this one reads a static palette and **held the glyph as a constant of its own**. The
library paints the title bold; this one does not. The library refuses to double a trailing blank
line with `endsBlank`; this one always appends the pad.

Threading a `Theme` through every call site to gain one implementation is not worth the churn,
and the bold difference is a look decision that belongs to the canon rather than to a port. Not a
port.

**But the glyph was a defect on its own, and it is fixed.** `ThemeFile` writes `glyphs.title` into
the theme the library's renderers read, and its own javadoc calls it "the one glyph a tile title
is drawn with". `Tiles` never asked for it. So an operator who changed `glyphs.title` changed it
for every tile uxmLib draws and for none of the tiles this plugin draws itself: **one setting, two
glyphs, on one server**, with nothing anywhere saying which was which. `StyleTags` holds it now,
beside the palette, and both installation points set it so a theme reload moves both halves
together.

**That is the second time the port question has paid without a port.** `MenuTitles` asked whether
two width tables agree and left a test behind; `Tiles` found a setting that reached half the
screen. **Comparing two implementations is worth doing even when the answer is to keep both.**

## `Comparison`: confirmed, ported, and it was a bug

The last entry on the list, and the only one where the library's is better rather than different.

**This plugin's split on the first operator symbol it could find anywhere in the text.** So an
operator character inside a placeholder body split the expression there:

```
%math_1>2% < 5   ->   left "%math_1"   right "2% < 5"
```

which then resolved to nothing, compared nothing, and reported whatever fell out. The gate is a
`CONDITION` on a click action and comparing PlaceholderAPI values is its entire job.

**It only went wrong for a single character operator whose symbol sorts after the one inside the
placeholder.** `%math_2<3% >= 5` reads correctly, because `>=` is tried before `<`. That is the
worst shape a bug can have: the half that works is the half anybody would test.

The library's parse skips a `%...%` span whole, which its own javadoc names as the reason. It
also knows three operators this plugin never had, `?=`, `*` and `||`, so a gate here now reads
the same vocabulary as a condition in every other plugin of ours.

**One behaviour changes and it is worth knowing.** An ordering compare between two non-numbers
was `false` here and is a lexicographic comparison in the library. That is an edge an operator
reaches only by writing an ordering compare on text, which is almost certainly a mistake either
way, and neither answer is silently worse than the other.

**The gate's catch changed shape and is pinned.** Ours answered a malformed expression with an
empty Optional; the library's throws. `ConditionGateComparisonTest` asserts the exception type
and message, because if that ever changed a mistyped gate would abort a click chain instead of
being skipped, and nothing else in this repository would notice.

## The map is finished

Eleven entries in group A: two ported, two rejected on policy, five blocked by the core rule, and
the last two are the health types that travel with the four. Twelve in group B, keep both. Seven
in group C, which is the adapter audit and a separate exercise. Three in group D, which is the
owner's.

**Two ports out of thirty seven shared names, and three defects found on the way**: a `decode`
that threw where its signature promised an empty, a tile glyph that reached half the screen, and
a condition that split inside a placeholder. **The map was worth more than the ports.**

## Group C, the adapter audit: `Scheduler` and `Messages`

The standing order names these two as ports to move. Read against the code, **neither is a
reimplementation and neither should move.** The reason is the same for both and it is not a
matter of taste.

**`Scheduler`.** The core port is
`onGlobal/onRegion/onEntity/async/asyncAfter/laterGlobal/repeatGlobal`, taking `PlayerRef` and
`Position`, which are this plugin's domain types. uxmLib's takes `org.bukkit.Entity` and
`org.bukkit.Location` and hands back a `TaskHandle`. **Core could not use the library's port if
it wanted to**: the whole point of declaring this one is that core names no Bukkit type.
`FoliaScheduler` is the translation from domain types to Paper's region schedulers, which is
what an outbound adapter is; and `EngineScheduler.Adapter` already bridges to uxmLib's
`Scheduler` at the one place uxmLib asks for one.

One difference worth knowing rather than fixing: on teardown the library runs a one-shot inline
and warns about a dropped timer, while `FoliaScheduler` returns silently for everything once
`plugin.isEnabled()` is false. Both are documented decisions in their own javadoc, and this
one's reasoning is that the only work scheduled during teardown is display cleanup for players
who are being disconnected anyway.

**`Messages`.** The core port is `String resolve(PlayerRef, MessageKey, Map<String, String>)`:
a plain string, `{brace}` placeholders, no MiniMessage. uxmLib's renders an Adventure
`Component` through MiniMessage with `TagResolver`s. Core may not know Adventure, so the port
returns a `String` and cannot be the library's. `CatalogMessages` is the adapter and it does
one thing: look the template up for the viewer's locale and substitute braces.

**Verdict for both: no work, and the standing order's list is one step out of date here.** These
two were named as ports to move; what they are is ports that already exist, behind adapters that
already do the translating. Nothing about them looks like the duplication the list was worried
about.

### One observation from the same read, recorded rather than chased

`bukkit-adapter` calls `MiniMessage.deserialize` in fifty four places, fifteen with a resolver
and thirty nine without. Most of the bare ones are the command layer building a colour prefix by
hand, which is consistent with the decision that operator console output is plain. **Whether any
of them is operator-written content that should have resolved a palette token, and rendered
`<body>` as literal text instead, is a separate question and a real one.** It wants its own
pass, with the answer per call site rather than per count.

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
| `Tiles` | Both say "puts the title of a menu tile on the first line of its lore, under a blank name" |
| ~~`MenuTitles`~~ | Confirmed and **rejected**, see below |
| `EquipmentSlot` | Both say "the six slots a fake-player NPC can wear" |
| `SerializedItems` | Both are one item written down as one line of text |
| `Comparison` | Both are a comparison of two resolved operands under an operator |

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

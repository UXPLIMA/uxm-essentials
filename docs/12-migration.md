# 12. Migration

Bringing a server that already runs something else onto uxmEssentials, and keeping the
config files of a server that already runs this one current. One hundred and thirty five
javadoc comments point here, more than at any other document; it is written from them.

## 12.1 What the importer is

A one-way import, run by an operator from the console, that reads another plugin's data and
writes it into this plugin's own storage. It is not a sync and not a bridge: it runs when
somebody runs it, and nothing fires it at enable.

### 12.1.1 Built sources

A source exists when it is in the registry, and the registry is what the command resolves
against. A name the registry does not know is answered with an "unknown source" message that
lists the built ids, never with a silent no-op, and the tab completion offers built ids only.

Built today: **EssentialsX** (a full on-disk source), **Vault** and **PlayerPoints** (live
sources, balance only), **LiteBans** (JDBC, sanctions), **DecentHolograms** and
**FancyHolograms** (on-disk, server-wide holograms), and three player-warp sources,
**AxPlayerWarps** (JDBC), **Athelion PlayerWarps** (a serialised `data.yml`) and **Olzie
PlayerWarps** (JDBC).

### 12.1.2 Planned is not stubbed

CMI, HuskHomes, PlayerVaultX, CoinsEngine, SunLight and AxVault are planned. A planned source
has no registry entry, no mapping table and no tab completion, so an operator can never start
an import that quietly does nothing. The day one is built is the day it appears.

## 12.2 The foreign format stops at the parse seam

Each source has a `parse/` package, and whatever shape the other plugin chose stays inside
it: a YAML node, a `ResultSet`, a serialised blob. What comes out is this repository's own
domain type. Nothing downstream of the seam knows which plugin the data came from, which is
why a new source is a new `parse/` package and not a change to the pipeline.

## 12.3 The command

`/uxmess import <source> [--dry-run]`, behind `uxmessentials.admin.import`. The bare
`dry-run` literal and the flag are equivalent.

The handler hands the run to the bounded executor off tick and returns immediately, so the
console is never blocked by an import. Its acknowledgements are plain operator text rather
than catalogue keys: an import is run from a console or by an admin reading a diagnostic,
never by a player.

## 12.4 One record's failure is one record

A record that cannot be read or cannot be written is counted and the run continues. An import
that stops on the first bad row is an import an operator cannot finish, because the one thing
they cannot do is edit the other plugin's data.

The same rule is applied inside a record where it makes sense: a location naming a world this
server does not have drops that location rather than failing the whole record, so a home in a
deleted world does not cost a player their other homes. Every drop and every failure is
counted and audited, so the summary says what did not arrive.

## 12.5 What each source migrates

Each source carries a mapping table in code, and the tables together are the greppable answer
to "what does the importer claim to migrate?". A row names the source field, the target
aggregate, the context that owns it, the mapper and the conflict unit.

### 12.5.1 EssentialsX

| Source | Target | Context | Conflict unit |
|---|---|---|---|
| `homes.<name>` | `Home` | homes | (uuid, home name) |
| `money` | `Wallet` | economy | uuid |
| `mail` | `Mailbox` | messaging | uuid |
| `warps/<name>` | `Warp` | warps | warp name |
| `kits.yml` entry | `KitDefinition` | kits | kit name |
| `jailed` + `jail` | `ModerationProfile` | moderation | uuid |
| `muted` + `muteTimeout` | `ModerationProfile` | moderation | uuid |

The deliberate gaps: **player vaults**, which EssentialsX does not ship, and **nicknames**
and **chat settings**, which are dropped. They are written here rather than represented as
empty rows, so the drift guard holds the code table against exactly this list.

A kit item descriptor is converted by its own converter, which handles the realistic subset
of the descriptor grammar rather than pretending to cover all of it.

### 12.5.2 Vault

One row: the balance becomes a `Wallet`, keyed by uuid. It is a live source, read through the
running plugin rather than off disk.

### 12.5.3 PlayerPoints

One row, the same shape: the points balance becomes a `Wallet`, keyed by uuid.

### 12.5.4 LiteBans

The first JDBC source, and it seeds the moderation context only.

| Source | Target | Conflict unit |
|---|---|---|
| `litebans_bans` where `ipban=0` | `TempbanState` | uuid |
| `litebans_bans` where `ipban=1` | `IpBan` | ip |
| `litebans_mutes` | `ModerationProfile` | uuid |
| `litebans_warnings` | `Warn` | (uuid, issued at) |

### 12.5.5 DecentHolograms

One YAML file per hologram under `holograms/`, each becoming a `Hologram` keyed by its name.

### 12.5.6 FancyHolograms

One file with a `holograms` section and an entry under it per hologram, becoming the same
`Hologram` target. Both hologram sources are server-wide displays rather than per-player
state.

### 12.5.7 AxPlayerWarps

A JDBC source, and the widest of the three warp sources: the warps themselves, ratings,
bans, favourites and visit counts, each into the player-warps context.

### 12.5.8 Athelion PlayerWarps

A serialised `data.yml`. The warps and what hangs off them, read through the parse seam so
the serialisation format never escapes it.

### 12.5.9 Olzie PlayerWarps

A JDBC source with the same targets as the other two warp sources, off its own tables.

## 12.6 Conflicts and balances

One `ImportOptions` is built from `migration.conf` plus the command's flag and threaded read
only through the plan, the mappers and the writer, so every stage sees the same decisions.

- **`on-conflict`** decides what happens when an imported record collides with one that
  exists. The default is `skip`: the existing record always wins, so a re-run can never
  clobber data. The branch taken is recorded per record in the audit line's `conflict` field.
- **`balance-policy`** decides the same question for money, separately, because money is the
  one thing an operator cannot repair by hand. The default is `skip-if-present`: a wallet that
  already holds a balance is left alone. `overwrite` is for a clean target.

An unrecognised value in either falls back to the safe default rather than refusing to start.

## 12.7 The pool

Imports run on a fixed-size pool of platform threads, which is the deliberate exception to
this repository's prefer-virtual-threads default: the goal is to cap write pressure on the
database, not to maximise parallelism. Four writers, a bounded queue of a thousand, and a
caller-runs policy when the queue is full, so the producer stalls when the writers lag and a
fifty thousand file source cannot out-run them and exhaust the heap.

The pool is created when an import starts and closed when it finishes and when the module
stops. It never touches a region or a tick thread, and it is the only place the importer
starts work: no `new Thread`, no `supplyAsync`, no `BukkitRunnable`, and a guard fails the
build over each.

## 12.8 The guards

### 12.8.1 Mapping drift

The code tables, the rows in §12.5 and the checked-in fixtures move together. A mapper added
in code with no row, or a row with no code path and no fixture, fails `check`.

### 12.8.2 Golden files

Each path has a fixture of real source data and the expected result, so a parser change that
alters an outcome shows as a diff rather than as a surprise on somebody's server.

### 12.8.3 Source registry drift

The registry keys, the roster and the per-source mapping tables stay in lock-step, which is
what keeps §12.1.1 and §12.5 from disagreeing with the code.

## 12.9 The runbook

1. **Back up.** A live run snapshots the plugin's config and data tree into a timestamped
   `pre-import-…` sibling directory before it writes anything, and records the snapshot name
   on the `migration_import_start` audit line. This captures the `.conf` state under the data
   directory; the off-host database backup is still the operator's own job and the runbook
   says so.
2. **Dry run.** `--dry-run` accumulates instead of writing and emits the identical audit
   lines with `dry_run=true`, so the real run can be diffed against it.
3. **Read the audit.** The importer writes `event=migration_import_*` lines to the
   `com.uxplima.uxmessentials.audit` channel: one at the start, one per record, one at the
   finish. The family makes a run replayable after the fact. The format and how to route the
   channel to a retained file are in `09-deployment.md` §Audit logging.
4. **Run it live**, and read the summary: what arrived, what collided, what was dropped.

## 12.10 Wiring and configuration

Migration is a first-class feature module, command gated like any other: it has its own id,
its own config root and its own switch, and a server that will never import can turn it off.

### 12.10.1 A source that needs credentials has its own block

A JDBC source cannot be discovered on disk, so it is configured: the URL, the optional
credential pair, the table prefix. Those blocks are seeded into `migration.conf` by the
config ladder rather than written by hand, so an operator edits values that are already there
with the comments that explain them.

## 12.11 What it does not do

It does not roll back. The snapshot in step 1 is what makes a bad import recoverable, and
that is a deliberate trade: a transactional import of a fifty thousand record source would
hold the database for the length of the run.

It does not merge. `skip` and `overwrite` are the two answers, per record, and neither tries
to reconcile two versions of the same home.

It does not run twice by itself. A re-run with the default policies is safe precisely because
the existing record wins.

## 12.12 The config ladder

The second half of this document is about this plugin's own files rather than another
plugin's data.

### 12.12.1 What it is

A numbered ladder of steps that brings a `migration.conf` from the version on disk to the
version this build ships. Step 1 seeds the defaults; step 2 seeds the LiteBans source block.
A step is a class, named for its number and what it does.

### 12.12.2 The no-op path is the common one

The ladder reports the version it started at and the version it ended at. When they are equal
the file was already current and nothing was rewritten, and the caller uses that to skip the
backup and the disk write entirely. A server that has been up to date since it was installed
therefore pays nothing at every start.

### 12.12.3 The four properties

Numbered and contiguous, idempotent, tracked by the `config-version` key, and never
destructive. A fresh config ships at the target version and triggers no step at all; an old
config upgrades to the target, and running it again is a no-op. All four are asserted, which
is what makes a new step safe to add.

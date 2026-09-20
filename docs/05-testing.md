# 5. Testing

What is tested where, and the four rules that make the suite worth having. It is written from
the code that cites it, so every rule below is one the repository already follows and a guard
already keeps.

## 5.1 The stack

JUnit 5, AssertJ, Mockito, MockBukkit and ArchUnit. The test JVM runs classes in parallel and
is pinned to a 2 GB heap, because an unset ceiling is a quarter of the host's RAM: the same
suite was given 5 GB on a workstation and 1.7 GB on a smaller runner, where the parallel
MockBukkit servers then ran out of heap. One runner-sized value is what the build is verified
against.

## 5.2 A test is named as a sentence

The method name says what must be true, not which method is called. `payRejectsMoreThanTheSenderHas`
is a sentence a reader can check against the behaviour; `testPay2` is not. A failing name should
tell an operator-facing story before anybody opens the file.

## 5.3 Which layer gets which test

### 5.3.1 The domain

Plain unit tests, no framework and no server. A domain class has no Bukkit type in it, so a
test of one needs nothing but `new`.

### 5.3.2 The application

A use case is tested against fakes of its ports, never against a running server. The fakes
live beside the tests that use them, in a `fakes` package under the context.

### 5.3.3 The adapters

An inbound adapter is tested under MockBukkit, which gives it a server that behaves like one.
An outbound adapter is tested against the real thing where the real thing fits in a test: the
persistence adapters run against an embedded database rather than against a mock of jOOQ, so
what they prove is what the SQL does.

### 5.3.4 A port with several implementations gets one contract suite

Where a port has more than one implementation, the behaviour is written once as an abstract
suite and each implementation subclasses it with a factory. `EconomyProviderContractTest` is
the pattern to copy: every `EconomyProvider` must satisfy the same four properties, and they
are the ones that cost money when they are wrong.

1. A pay is atomic.
2. Insufficient funds are rejected, never clamped, and the rejection mutates nothing.
3. The rich list comes back in descending order.
4. Concurrent debits do not double spend.

The fourth is the load-bearing one. A provider that passes single threaded and loses the
concurrent race is unfit to register through the `ServicesManager`, and this is where that
surfaces rather than on a server at peak. It is written with a latch and a `@RepeatedTest`,
so the race is run many times rather than once.

## 5.4 Fakes, not mocks, where the double has behaviour

Mockito is for a collaborator whose call you want to observe. A collaborator with state, a
store or a clock, is a small hand written fake: the test then reads as the behaviour it
describes rather than as a list of stubbings, and one fake serves twenty tests.

## 5.5 A test never sleeps

Time is a seam. A cooldown, a warmup and a scheduled task are driven by moving the seam, not
by waiting for the wall clock. A sleeping test is slow when it passes and flaky when it fails.

## 5.6 A test that needs a machine lives on its own task

The Redis round trip needs a broker, and a broker is not something this build can promise. It
is in an `integrationTest` source set that `check` never runs, so a missing broker fails that
task rather than skipping quietly inside `test`. This was found the hard way: the uxmLib
equivalent had gone green for months on a machine that happened to run a Redis, proving
nothing anywhere else.

## 5.7 No test is deleted, weakened or skipped

A skipped test protects nothing and reports as green. MockBukkit turns an unimplemented mock
into a `TestAbortedException`, which JUnit records as a skip, so a guard can stop running and
nobody is told. There is no legitimate skip in this repository, so `verifyNoSkippedTests`
reads the result XML after `test` and fails `check` with the name of every suite that skipped
anything.

The rule reaches beyond the mechanism. A test that is in the way is a test that found
something: fix the code, or change the test to assert the new truth, and never delete it to
make a build green.

## 5.8 Prove a guard before you trust it

Introduce the violation, watch the build fail, remove it, watch the build pass. A guard that
cannot fail is a comment with a build time. This applies to every guard in §5.16 and to the
ordinary assertions that stand in for one.

## 5.9 Where tests live

Each module's `src/test/java` mirrors its `src/main/java`, package for package. A guard that
is about the whole repository rather than about one class lives in
`bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/`, whichever module it
reads.

## 5.16 The drift guards

*The number is a gap on purpose. Six guards cite this section as §16 from their own javadoc,
so the number is theirs rather than this document's, and renumbering it would send every one
of them somewhere else. Sections 10 to 15 are unwritten.*


A rule that only lives in `CLAUDE.md` is a rule somebody breaks in a hurry. Every rule this
repository states is kept by a test that fails the build the day the rule stops being true,
and each of those names the rule it keeps and the defect that paid for it.

There are two kinds, and picking the wrong one is the usual mistake.

**ArchUnit, for a rule about class identity.** Who may depend on whom, which package may name
`JavaPlugin`, which types a layer may import, what shape a domain fact has. ArchUnit reads the
bytecode, so it answers these exactly and it cannot be fooled by how something is spelled.
`ArchitectureTest` holds the layering rules: no Bukkit in the domain, no Bukkit in the
application, `bootstrap` alone names `JavaPlugin`, nothing depends on the Bukkit scheduler, the
API modules do not reach into internals.

**Source text, for a rule the bytecode cannot see.** SQL built by concatenation is one string
by the time the class file exists. A message written inline instead of through the catalogue
looks like any other literal. A word from the glossary used to mean something else is not a
type at all.

A source guard reads through `ProductionSources`, and it reads `code(source)`, which blanks
every comment and leaves every other character where it was. That matters more than it
sounds: half the mentions of `BukkitRunnable` in this repository are javadoc explaining why it
is never used, and a guard that cannot tell those from a real call is a guard nobody can keep
green, so it gets deleted rather than obeyed. Positions are preserved, so a line number taken
from the blanked text still points at the real line.

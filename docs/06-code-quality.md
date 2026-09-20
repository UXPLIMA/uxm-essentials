# 6. Code quality

The rules that are about how code is written rather than about where it lives, and the guards
that keep them.

## 6.1 A warning is a failure

Error Prone and NullAway run under `-Werror`. NullAway runs in `onlyNullMarked` mode, so a
package is checked once it carries `@NullMarked` in its `package-info.java`, which every
package in `:core` does and a guard enforces.

## 6.2 A failure is never swallowed

Two halves of one rule, and both produce the same operator experience: a server that
misbehaves and a log that says nothing useful.

`printStackTrace()` writes to the JVM's standard error, which on a Paper server is an
unattributed wall of text with no plugin name, no context and no level. An operator cannot
tell whose fault it is, and a support thread starts from zero.

An empty catch is worse. The failure leaves no trace at all and the next symptom turns up
somewhere unrelated, hours later.

So every catch does one of two things: it logs through the injected logger port with enough
context to act on, or it rethrows wrapped in a domain exception. A catch body may be nothing
but a comment, and only when the comment says why that failure is genuinely uninteresting. A
body with neither a statement nor a comment is always a bug.

`PrintStackTraceDriftTest` fails the build over either half.

## 6.3 SQL is never built by concatenation

Statements go through jOOQ's DSL. A string that grows a query by `+` is both an injection
risk and a statement no reader can see the shape of. The rule is checked against the source,
because concatenation is gone by the time the class file exists.

## 6.4 Sizes

A method stays at or below thirty lines. A class over about three hundred lines is a smell
worth answering rather than a limit that fails the build. Both exist to keep a unit small
enough to hold in your head, which is also what makes a test of it short.

## 6.5 A comment says why

The code says what it does. A comment that repeats the code is noise that goes stale; a
comment that says why the code is as it is, which alternative was tried, which defect it
prevents, is the one thing review cannot reconstruct later. Most comments in this repository
are of the second kind, and a new one should be too.

No comment is written in the voice of an assistant, and no commit message mentions one.

## 6.6 No long dash

Not in code, not in a comment, not in a message, not in a document. A colon, a comma or a
full stop instead. `scripts/check-style.sh` fails over one.

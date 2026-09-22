#!/usr/bin/env bash
#
# check-style.sh - the rules a script can keep, for this repository alone.
#
# Run it before every commit. CI runs it on every push, before the build, which is why it
# must not need a workspace around it: a clone of this repository is all it reads.
#
# Exit codes:
#   0  the tree is clean
#   1  something forbidden was found
#   2  the check itself could not run
#
# Three earlier versions of this script were wrong, and each failure is worth remembering:
#
#   1. It used `git grep -P` with a \x{...} class and hid the result with `2>/dev/null`.
#      The pattern is invalid without PCRE UTF mode, git exited 128, and the empty output
#      read as "no hits". The guard reported success over a real violation. Never silence
#      the exit code of a check.
#   2. It held the forbidden characters literally, so it always matched itself. A guard
#      must not be its own violation.
#   3. It checked the long dash and nothing else, in every repository, for months, while
#      the workspace copy checked three things and the family canon advertised three. The
#      two rules missing here are the two that have actually cost us something: one
#      `git add -A` into a repository with no .gitignore put 22905 files and 92 binaries
#      into a public history, and a Folia test boot wrote a generated secret into a
#      server.properties that was then committed. CI ran this script before both.
#
set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

found=0
report() {
    echo "$1"
    shift
    printf '%s\n' "$@" | sed 's/^/  /'
    found=1
}

# --- the long dash ------------------------------------------------------------------------
#
# U+2014 EM DASH and U+2013 EN DASH, written as escapes so that this file holds neither.
#
# It reads the tracked files and not the whole working tree, which is uxmLib's copy of this
# script and is the better rule: a local note, a build directory and an editor's own file are
# not published, and a guard that fails on them is a guard people learn to skip. A checkout
# with no git around it falls back to walking the tree, so a source export still gets an
# answer.
em_dash=$'\u2014'
en_dash=$'\u2013'

if git rev-parse --git-dir > /dev/null 2>&1; then
    hits="$(git ls-files -z | xargs -0 grep -n "[${em_dash}${en_dash}]" --binary-files=without-match)"
    status=$?
    # xargs answers 123 when one grep in the batch found nothing, which is the usual case.
    # Anything else that is not 0 or 1 is the check itself failing.
    if [ "$status" != 0 ] && [ "$status" != 1 ] && [ "$status" != 123 ]; then
        echo "check-style: the search failed with status ${status}" >&2
        exit 2
    fi
else
    hits="$(grep -rn "[${em_dash}${en_dash}]" . \
        --exclude-dir=.git \
        --exclude-dir=.gradle \
        --exclude-dir=build \
        --exclude-dir=node_modules \
        --binary-files=without-match)"
    status=$?
    if [ "$status" != 0 ] && [ "$status" != 1 ]; then
        echo "check-style: grep failed with status ${status}" >&2
        exit 2
    fi
fi
[ -n "$hits" ] && report "Forbidden long dash. Use a colon, a comma, a full stop, or brackets." "$hits"

# --- a binary in version control ------------------------------------------------------------
#
# The wrapper jar is the one exception, because Gradle's own bootstrap is committed by design.
# Everything else on this list is a build output or a download, and none of it belongs in a
# history: the 38 MB Mojang-mapped server jar that once landed in one had to be rewritten out.
if git rev-parse --git-dir > /dev/null 2>&1; then
    binaries="$(git ls-files | grep -Ei '\.(jar|zip|tar\.gz|mca|db)$' \
        | grep -v 'gradle/wrapper/gradle-wrapper\.jar$')"
    [ -n "$binaries" ] && report "A binary is tracked. Build outputs and downloads stay out of the history." "$binaries"

    # --- a secret in version control ---------------------------------------------------------
    #
    # By file name first, which catches a key or an environment file committed whole.
    named="$(git ls-files | grep -Ei '(^|/)(\.env|secrets/|.*\.(pem|key|keystore)|forwarding\.secret)$')"
    [ -n "$named" ] && report "A file that holds a secret is tracked." "$named"

    # Then by content, because a secret does not have to live in a file whose name says so.
    # An assignment whose key names a credential and whose value is long enough to be one. An
    # empty value is what a shipped configuration should carry, so an empty value passes.
    #
    # The exceptions are by value and never by path: a secret in a test file is still a secret,
    # and excluding test sources would put this back to sleep. What is excluded is the two
    # published TOTP vectors, JBSWY3DPEHPK3PXP from RFC 4648 and GEZDGNBVGY3TQOJQ, which is
    # base32 of "12345678901234567890" from RFC 6238, and change_me_please, which is a shipped
    # default quoted in the page that tells an operator to change it.
    inside="$(git ls-files | while IFS= read -r f; do
        [ -f "$f" ] || continue
        grep -HInE '^[^#]*[-_.]?(secret|password|passwd|token|api[-_]?key|access[-_]?key)[-_a-z0-9]*[=:][[:space:]]*["'"'"']?[A-Za-z0-9/+_-]{16,}' "$f" 2>/dev/null
    done | grep -vE 'secret=(JBSWY3DPEHPK3PXP|GEZDGNBVGY3TQOJQ)' | grep -v 'change_me_please')"
    [ -n "$inside" ] && report "A tracked file holds something shaped like a secret." "$inside"
fi

if [ "$found" -eq 0 ]; then
    echo "check-style: clean"
    exit 0
fi
exit 1

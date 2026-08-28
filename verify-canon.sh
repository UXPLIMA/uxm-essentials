#!/usr/bin/env bash
#
# verify-canon.sh: version-catalog validator (docs/04-build.md §22).
#
# Two phases, because a catalog can be wrong in two independent ways.
#
# Phase 1, parity. docs/04-build.md §4 prints itself as the canonical catalog, and
# a plugin cloned from this template starts from that printed copy. So the doc is
# diffed against the real gradle/libs.versions.toml, entry for entry. Skipping this
# is how the doc once drifted a whole Minecraft generation behind the build while
# the script still reported a clean run: every stale coordinate it checked still
# existed in a repository, because old releases are never withdrawn. Reachability
# alone can never catch staleness. (VersionsCanonDriftTest enforces the same
# invariant offline inside ./gradlew check; this is the release-time copy.)
#
# Phase 2, reachability. Every [libraries] entry's version.ref is resolved against
# [versions] and each Maven coordinate is HEADed against the repositories that can
# serve it (Maven Central, PaperMC, CodeMC for Treasury, JitPack for Vault and the
# claim APIs, extendedclip for PlaceholderAPI, mikeprimm for Dynmap, opencollab for
# Floodgate). Fails loudly on any 404 or unresolved TODO-VERIFY literal.
#
# Phase 2 reads the real catalog, not the doc: what must be provably resolvable is
# what the build actually pulls.
#
# Exit codes:
#   0  the doc matches the build and every coordinate resolved (or was skipped)
#   1  the doc drifted, a coordinate is missing, or a TODO-VERIFY literal remains
#   2  an input file could not be found (run from the repo root)
#
set -uo pipefail

DOC="docs/04-build.md"
TOML="gradle/libs.versions.toml"

if [[ ! -f "$TOML" ]]; then
    echo "verify-canon: cannot find $TOML: run from the repo root" >&2
    exit 2
fi

# Repositories to try, in order. The first 200 wins.
REPOS=(
    "https://repo1.maven.org/maven2"
    "https://repo.papermc.io/repository/maven-public"
    "https://repo.codemc.org/repository/maven-public"
    "https://jitpack.io"
    "https://repo.extendedclip.com/releases"
    "https://repo.mikeprimm.com"
    "https://repo.opencollab.dev/main"
)

# The build's own catalog: the authority for what actually has to resolve.
CATALOG="$(cat "$TOML")"

# --- Phase 1: the doc must reprint the catalog verbatim. ---------------------
#
# docs/ is deliberately kept out of the published repository, so a checkout
# without it skips parity rather than failing on it.
parity_failed=0

if [[ -f "$DOC" ]]; then
    # The one fenced toml block inside §4.
    DOC_CATALOG="$(awk '
        /^## / { if (insec) exit; insec = ($0 ~ /^## 4\. /); next }
        !insec { next }
        /^```toml/ { intoml = 1; next }
        /^```/ { if (intoml) exit; next }
        intoml { print }
    ' "$DOC")"

    if [[ -z "$DOC_CATALOG" ]]; then
        echo "verify-canon: no version-catalog toml block found in $DOC §4" >&2
        exit 2
    fi

    # Compare content, not layout: comments and blank lines carry no pins.
    strip() { grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$' | sed 's/[[:space:]]*#.*$//; s/[[:space:]]\+/ /g; s/^ //; s/ $//'; }

    if diff_out="$(diff <(strip <<< "$DOC_CATALOG") <(strip <<< "$CATALOG"))"; then
        echo "✓ $DOC §4 matches $TOML"
    else
        echo "✗ $DOC §4 has drifted from $TOML (< doc, > build):"
        echo "$diff_out"
        parity_failed=1
    fi
else
    echo "~ $DOC not present in this checkout: parity check skipped"
fi

echo

# --- Build the versions map (key -> value), stripping trailing comments. ---
declare -A VERSIONS
while IFS= read -r line; do
    # match: key = "value"   (ignore lines without an = "..." pair)
    if [[ "$line" =~ ^([a-zA-Z0-9_-]+)[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
        VERSIONS["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
    fi
done < <(awk '/^\[versions\]/{v=1;next} /^\[/{v=0} v' <<< "$CATALOG")

checked=0
skipped=0
failed=0

head_ok() {
    local url="$1"
    curl -sfI --max-time 20 "$url" >/dev/null 2>&1
}

resolve_one() {
    local module="$1" version="$2"
    local group="${module%%:*}"
    local artifact="${module##*:}"
    local grouppath="${group//.//}"
    local pom="${grouppath}/${artifact}/${version}/${artifact}-${version}.pom"
    local repo
    for repo in "${REPOS[@]}"; do
        if head_ok "${repo}/${pom}"; then
            return 0
        fi
    done
    return 1
}

# --- Walk every [libraries] entry. ---
while IFS= read -r line; do
    # match: key = { module = "g:a", version.ref = "ref" }
    # version.ref is optional (some entries inherit a BOM version).
    if [[ "$line" =~ module[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
        module="${BASH_REMATCH[1]}"
    else
        continue
    fi

    version=""
    if [[ "$line" =~ version\.ref[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
        ref="${BASH_REMATCH[1]}"
        version="${VERSIONS[$ref]:-}"
    elif [[ "$line" =~ version[[:space:]]*=[[:space:]]*\"([^\"]+)\" ]]; then
        version="${BASH_REMATCH[1]}"
    fi

    # No version anywhere (BOM-managed entry like junit-jupiter): skip.
    if [[ -z "$version" ]]; then
        printf '~ %-55s (no pinned version, BOM-managed)\n' "$module"
        skipped=$((skipped + 1))
        continue
    fi

    if [[ "$version" == *TODO-VERIFY* ]]; then
        printf '✗ %-55s %s (TODO-VERIFY, unresolved)\n' "$module" "$version"
        failed=$((failed + 1))
        continue
    fi

    if [[ "$version" == *SNAPSHOT* ]]; then
        printf '~ %-55s %s (skipped, SNAPSHOT)\n' "$module" "$version"
        skipped=$((skipped + 1))
        continue
    fi

    if resolve_one "$module" "$version"; then
        printf '✓ %-55s %s\n' "$module" "$version"
        checked=$((checked + 1))
    else
        printf '✗ %-55s %s (not found in any known repo)\n' "$module" "$version"
        failed=$((failed + 1))
    fi
done < <(awk '/^\[libraries\]/{l=1;next} /^\[/{l=0} l' <<< "$CATALOG")

echo
echo "Checked: ${checked}   Skipped: ${skipped}   Failed: ${failed}   Doc drift: ${parity_failed}"

if [[ "$failed" -gt 0 || "$parity_failed" -gt 0 ]]; then
    exit 1
fi
exit 0

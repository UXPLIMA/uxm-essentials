#!/usr/bin/env bash
#
# verify-canon.sh — version-catalog validator (docs/04-build.md §22).
#
# Reads the canonical version catalog out of docs/04-build.md §4, resolves every
# [libraries] entry's version.ref against [versions], and HEADs each Maven
# coordinate against the repositories that can serve it (Maven Central, PaperMC,
# CodeMC for Treasury, JitPack for Vault). Fails loudly on any 404 or on any
# unresolved TODO-VERIFY literal.
#
# Exit codes:
#   0  every coordinate resolved (or was legitimately skipped)
#   1  one or more coordinates missing, or a TODO-VERIFY literal remains
#   2  the input doc could not be found (run from the repo root)
#
set -uo pipefail

DOC="docs/04-build.md"

if [[ ! -f "$DOC" ]]; then
    echo "verify-canon: cannot find $DOC — run from the repo root" >&2
    exit 2
fi

# Repositories to try, in order. The first 200 wins.
REPOS=(
    "https://repo1.maven.org/maven2"
    "https://repo.papermc.io/repository/maven-public"
    "https://repo.codemc.org/repository/maven-public"
    "https://jitpack.io"
    "https://repo.opencollab.dev/main"
)

# Extract the single fenced toml block that contains the version catalog.
CATALOG="$(awk '
    /^```toml/ { intoml = 1; next }
    /^```/      { if (intoml) intoml = 0; next }
    intoml      { print }
' "$DOC")"

if [[ -z "$CATALOG" ]]; then
    echo "verify-canon: no [versions]/[libraries] toml block found in $DOC" >&2
    exit 2
fi

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
        printf '~ %-55s (no pinned version — BOM-managed)\n' "$module"
        skipped=$((skipped + 1))
        continue
    fi

    if [[ "$version" == *TODO-VERIFY* ]]; then
        printf '✗ %-55s %s (TODO-VERIFY — unresolved)\n' "$module" "$version"
        failed=$((failed + 1))
        continue
    fi

    if [[ "$version" == *SNAPSHOT* ]]; then
        printf '~ %-55s %s (skipped — SNAPSHOT)\n' "$module" "$version"
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
echo "Checked: ${checked}   Skipped: ${skipped}   Failed: ${failed}"

if [[ "$failed" -gt 0 ]]; then
    exit 1
fi
exit 0

#!/bin/bash
# Prints the body of the section "## [<name>]" of CHANGELOG.md, for example as the notes of a GitHub release. The link
# line of the section is left out. Fails when the section does not exist or is empty.
# Usage: scripts/release-notes.sh <version|Snapshot>
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <version|Snapshot>" >&2
  exit 2
fi

notes=$(
  awk -v heading="## [$1]" '
    /^## / { if (found) exit; found = ($0 == heading); next }
    found && /^\[[^]]+\]: / { next }
    found { print }
  ' CHANGELOG.md | sed -e '/./,$!d'
)

if [ -z "$notes" ]; then
  echo "release-notes: CHANGELOG.md has no section [$1] with content" >&2
  exit 1
fi
printf '%s\n' "$notes"

#!/bin/bash
# Fails when a link of a markdown file does not resolve: a relative link or image to a file or folder that does not
# exist, an #anchor without a heading of that name (as GitHub builds it, for headings of plain ASCII), or an HTML
# src/href of that kind. Absolute URLs are not followed, so this needs no network. Links inside code blocks and
# inline code are not checked. Files that are not tracked yet are included, files that are deleted are not.
# Usage: scripts/check-links.sh
set -euo pipefail
cd "$(dirname "$0")/.."

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

git ls-files --cached --others --exclude-standard | while IFS= read -r file; do
  if [ -e "$file" ]; then echo "$file"; fi
done > "$tmp"

markdown=()
while IFS= read -r file; do
  markdown+=("$file")
done < <(grep -E '\.md$' "$tmp" || true)

if [ "${#markdown[@]}" -eq 0 ]; then
  echo "check-links: no markdown files"
  exit 0
fi

# Pass 0 reads the list of files, pass 1 the headings of the markdown files, pass 2 their links
if ! awk '
  function slug(heading,    text) {
    text = tolower(heading)
    gsub(/[^a-z0-9 _-]/, "", text)
    gsub(/ /, "-", text)
    return text
  }

  # The path without "." and "..", or "?outside" when it leaves the repository
  function normalize(path,    count, i, depth, segment, parts, stack, result) {
    count = split(path, parts, "/")
    depth = 0
    for (i = 1; i <= count; i++) {
      segment = parts[i]
      if (segment == "" || segment == ".") continue
      if (segment == "..") {
        if (depth == 0) return "?outside"
        depth--
        continue
      }
      stack[++depth] = segment
    }
    result = ""
    for (i = 1; i <= depth; i++) result = result (i > 1 ? "/" : "") stack[i]
    return result
  }

  function check(file, line, target,    anchor, base, path, position, resolved) {
    sub(/ .*$/, "", target)
    if (target == "") return
    if (target ~ /^[a-zA-Z][a-zA-Z0-9+.-]*:/ || target ~ /^\/\//) return
    sub(/\?[^#]*/, "", target)
    position = index(target, "#")
    anchor = ""
    path = target
    if (position > 0) {
      anchor = substr(target, position + 1)
      path = substr(target, 1, position - 1)
    }
    if (path == "") {
      resolved = file
    } else if (substr(path, 1, 1) == "/") {
      resolved = normalize(path)
    } else {
      base = file
      sub(/[^\/]*$/, "", base)
      resolved = normalize(base path)
    }
    if (resolved == "?outside") {
      fail(file, line, target, "leaves the repository")
    } else if (resolved != "" && !(resolved in tracked) && !(resolved in folders)) {
      fail(file, line, target, "no such file or folder")
    } else if (anchor != "" && resolved ~ /\.md$/ && !((resolved SUBSEP tolower(anchor)) in headings)) {
      fail(file, line, target, "no such heading in " resolved)
    }
  }

  function fail(file, line, target, reason) {
    print file ":" line ": " target ": " reason
    broken++
  }

  pass == 0 {
    tracked[$0] = 1
    folder = $0
    while (sub(/\/[^\/]*$/, "", folder)) folders[folder] = 1
    next
  }

  FNR == 1 { fenced = 0 }
  /^ *(```|~~~)/ { fenced = !fenced; next }
  fenced { next }

  pass == 1 && /^#+ / {
    heading = $0
    sub(/^#+ +/, "", heading)
    sub(/ +#*$/, "", heading)
    name = slug(heading)
    duplicates = seen[FILENAME SUBSEP name]++
    if (duplicates > 0) name = name "-" duplicates
    headings[FILENAME SUBSEP name] = 1
  }

  pass == 2 {
    text = $0
    gsub(/`[^`]*`/, "", text)
    rest = text
    while (match(rest, /\]\([^)]*\)/)) {
      check(FILENAME, FNR, substr(rest, RSTART + 2, RLENGTH - 3))
      rest = substr(rest, RSTART + RLENGTH)
    }
    rest = text
    while (match(rest, /(src|href)="[^"]*"/)) {
      target = substr(rest, RSTART, RLENGTH)
      sub(/^[a-z]+="/, "", target)
      sub(/"$/, "", target)
      check(FILENAME, FNR, target)
      rest = substr(rest, RSTART + RLENGTH)
    }
    if (text ~ /^\[[^]]+\]: */) {
      target = text
      sub(/^\[[^]]+\]: */, "", target)
      check(FILENAME, FNR, target)
    }
  }

  END { exit (broken > 0) }
' pass=0 "$tmp" pass=1 "${markdown[@]}" pass=2 "${markdown[@]}"; then
  echo "check-links: found links that do not resolve (see above)" >&2
  exit 1
fi
echo "check-links: ok"

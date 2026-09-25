#!/usr/bin/env bash
# Proves the PrimeReact aliasing (primefaces-themes/theme-base/_primereact.scss) cannot change how
# JSF pages render: compiles the theme as committed at <ref> (default HEAD) and as in the working
# tree, then checks every old rule survives with identical declarations and a superset of its
# selectors, that every selector added is a .p-* one, and that every new rule is .p-* only.
# Usage: frontend/dev/check-jsf-theme-regression.sh [git-ref]   (needs `sass` and python3)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
ref="${1:-HEAD}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
git -C "$repo" archive "$ref" primefaces-themes | tar -x -C "$tmp"
sass --no-source-map --quiet "$tmp/primefaces-themes/themes/primefaces-siamois-theme/theme.scss" "$tmp/old.css"
sass --no-source-map --quiet "$repo/primefaces-themes/themes/primefaces-siamois-theme/theme.scss" "$tmp/new.css"
python3 "$here/check-jsf-theme-regression.py" "$tmp/old.css" "$tmp/new.css"

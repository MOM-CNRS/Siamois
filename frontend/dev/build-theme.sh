#!/usr/bin/env bash
# Dev-only: compiles the Siamois PrimeFaces theme (the same SCSS the JSF app ships) into
# frontend/dev/.generated/, next to PrimeFaces' own structural CSS, so the theme harness
# (dev/theme/harness.html, served by `npm run dev`) renders with the real host-page stylesheets.
# Needs a `sass` binary on PATH (dart-sass). `--watch` keeps recompiling on change.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
out="$here/.generated"
theme="$repo/primefaces-themes/themes/primefaces-siamois-theme"
mkdir -p "$out/fonts"

# PrimeFaces structural CSS + its PrimeIcons, straight from the jar Maven already resolved.
jar="$(ls -d "$HOME"/.m2/repository/org/primefaces/primefaces/*/primefaces-*-jakarta.jar 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$jar" ]; then
  unzip -oqj "$jar" "META-INF/resources/primefaces/components.css" -d "$out"
  mkdir -p "$out/primeicons"
  unzip -oqj "$jar" "META-INF/resources/primefaces/primeicons/*" -d "$out/primeicons"
fi
cp "$repo"/src/main/resources/META-INF/resources/resources/fonts/Inter-*.ttf "$out/fonts/"

# The theme's JSF resource expressions (#{resource['fonts:…']}) are resolved by Faces at runtime;
# point them at the copied fonts instead.
fix_fonts() { sed -E "s#\#\{resource\['fonts:([^']+)'\]\}#fonts/\1#g" "$1" > "$1.tmp" && mv "$1.tmp" "$1"; }

compile() {
  sass --no-source-map --quiet "$theme/theme.scss" "$out/theme.css"
  sass --no-source-map --quiet "$theme/settings.scss" "$out/settings.css"
  fix_fonts "$out/theme.css"
}

compile
echo "theme compiled → $out"
if [ "${1:-}" = "--watch" ]; then
  # Poll-based so it needs nothing beyond sass itself.
  last=""
  while true; do
    now="$(find "$repo/primefaces-themes" -name '*.scss' -newer "$out/theme.css" | head -1)"
    if [ -n "$now" ]; then compile && echo "recompiled ($(date +%T))"; fi
    sleep 1
  done
fi

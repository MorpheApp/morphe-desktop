#!/usr/bin/env bash
#
# Fetches a Material Symbols SVG from Google and prints a MorpheIcons declaration.
#
# Usage: scripts/add-icon.sh <CamelCaseName> [style]
# Styles: filled (default), outline, rounded, sharp, rounded-outline, sharp-outline
# Example: scripts/add-icon.sh Bookmark outline

set -euo pipefail

name="${1:-}"
style="${2:-filled}"
if [ -z "$name" ]; then
    echo "Usage: $0 <CamelCaseName> [filled|outline|rounded|sharp|rounded-outline|sharp-outline]" >&2
    exit 1
fi

# Map the style to a Material Symbols family + fill + a name suffix.
case "$style" in
    filled)          family="outlined"; fill="fill1";   suffix="" ;;
    outline)         family="outlined"; fill="default"; suffix="Outline" ;;
    rounded)         family="rounded";  fill="fill1";   suffix="Rounded" ;;
    sharp)           family="sharp";    fill="fill1";   suffix="Sharp" ;;
    rounded-outline) family="rounded";  fill="default"; suffix="RoundedOutline" ;;
    sharp-outline)   family="sharp";    fill="default"; suffix="SharpOutline" ;;
    *) echo "Unknown style '$style'. Use: filled|outline|rounded|sharp|rounded-outline|sharp-outline" >&2; exit 1 ;;
esac

# CamelCase -> snake_case for Google's icon id (CloudDownload -> cloud_download).
snake=$(printf '%s' "$name" | sed -E 's/([a-z0-9])([A-Z])/\1_\2/g' | tr '[:upper:]' '[:lower:]')
val="${name}${suffix}"
url="https://fonts.gstatic.com/s/i/short-term/release/materialsymbols${family}/${snake}/${fill}/24px.svg"

svg=$(curl -fsSL "$url" 2>/dev/null || true)
if [ -z "$svg" ]; then
    echo "Not found ('$snake', $style). Check the exact name at https://fonts.google.com/icons" >&2
    exit 1
fi

# Every <path d="..."> becomes a quoted, comma-separated arg (symbol() is vararg).
args=$(printf '%s' "$svg" | grep -oE 'd="[^"]*"' | sed 's/^d=//' | paste -sd, - | sed 's/,/, /g')

echo "Paste into the MorpheIcons object in MorpheIcons.kt:"
echo ""
printf '    val %s: ImageVector by lazy { symbol("%s", %s) }\n' "$val" "$val" "$args"

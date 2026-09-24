#!/usr/bin/env bash
#
# Morphe Desktop launcher for RPM installs.
#
# 1. Runtime data: the upstream JAR stores runtime data in a `morphe-data/`
#    folder next to the JAR, falling back to ~/morphe when that isn't
#    writable. On a system install the JAR lives under /usr/share
#    (root-owned), so we point the app at an XDG data location via
#    MORPHE_DATA_DIR — the override documented in MorpheData.kt for
#    package-manager installs.
#
# 2. HiDPI / fractional display scaling: Java apps need explicit help to
#    follow the desktop scale factor on X11/XWayland — at GNOME 125% a
#    1x-rendered window appears far too small (mainline OpenJDK's
#    sun.java2d.uiScale changes reported coordinates only, not the native
#    window size).
#      * With the bundled JBR (--with-jbr build): JBR implements native
#        HiDPI scaling on X11 and auto-detects the effective DPI from the
#        Xft.dpi resource. No flags needed.
#      * With system Java: best-effort — pass the scale derived from
#        Xft.dpi (192 = 2x on a 125% fractional display) via
#        sun.java2d.uiScale, which Compose/Skiko picks up for its content
#        scale.
#
# Manual override (both variants): MORPHE_UI_SCALE=1.25 morphe-desktop
# (or MORPHE_UI_SCALE=1 to disable scaling entirely.)
#
export MORPHE_DATA_DIR="${MORPHE_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/morphe-desktop}"

JAR=/usr/share/java/morphe-desktop/morphe-desktop.jar
BUNDLED_JAVA=""
for d in /usr/lib64 /usr/lib; do
    [[ -x "$d/morphe-desktop/jbr/bin/java" ]] && BUNDLED_JAVA="$d/morphe-desktop/jbr/bin/java" && break
done

if [[ -n "$BUNDLED_JAVA" ]]; then
    scale_args=()
    if [[ -n "${MORPHE_UI_SCALE:-}" ]]; then
        scale_args=(-Dawt.uiScale="$MORPHE_UI_SCALE")
    fi
    exec "$BUNDLED_JAVA" "${scale_args[@]}" --enable-native-access=ALL-UNNAMED \
        -jar "$JAR" "$@"
fi

if [[ -z "${MORPHE_UI_SCALE:-}" ]]; then
    xft_dpi=$(xrdb -query 2>/dev/null | awk '$1 == "Xft.dpi:" { print $2; exit }')
    if [[ -n "$xft_dpi" && "$xft_dpi" != "96" && "$xft_dpi" != "96.0" ]]; then
        MORPHE_UI_SCALE=$(awk -v d="$xft_dpi" 'BEGIN { printf "%.3f", d / 96 }')
    fi
fi

scale_args=()
if [[ -n "${MORPHE_UI_SCALE:-}" && "$MORPHE_UI_SCALE" != "1" ]]; then
    scale_args=(-Dsun.java2d.uiScale="$MORPHE_UI_SCALE")
fi

exec java "${scale_args[@]}" --enable-native-access=ALL-UNNAMED \
    -jar "$JAR" "$@"

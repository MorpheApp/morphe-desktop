#!/usr/bin/env bash
#
# Build the Morphe Desktop RPM for Fedora from the Gradle-built shadow JAR.
#
# Prerequisites:
#   * The Gradle build has already produced the shadow JAR:
#       ./gradlew assemble          (repo root)
#   * rpm-build installed.
#
# Usage:
#   ./build-rpm.sh [--with-jbr]
#
#   --with-jbr   Bundle a jlink-trimmed JetBrains Runtime into the package.
#                JBR implements native HiDPI window scaling on X11/XWayland,
#                which mainline OpenJDK lacks — without it the UI renders
#                tiny under GNOME fractional display scaling (e.g. 125%).
#                Requires the JBR_JDK environment variable pointing at a
#                JBR/JDK 21+ SDK that contains jmods/ (e.g. the JetBrains
#                Runtime SDK downloaded by the Gradle toolchain).
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOPDIR="${RPM_TOPDIR:-$HOME/rpmbuild}"
JBR_VERSION="21.0.11"

WITH_JBR=0
for arg in "$@"; do
    case "$arg" in
        --with-jbr) WITH_JBR=1 ;;
        *) echo "Unknown argument: $arg" >&2; exit 1 ;;
    esac
done

VERSION=$(grep -E '^version[[:space:]]*=' "$REPO/gradle.properties" | head -1 | sed 's/[^=]*=[[:space:]]*//' | tr -d '[:space:]')
JAR="$REPO/build/libs/morphe-desktop-$VERSION-all.jar"

[[ -f "$JAR" ]] || { echo "ERROR: JAR not found: $JAR (run ./gradlew assemble first)" >&2; exit 1; }

RPMBUILD_ARGS=(--define "pkg_version $VERSION")

if [[ "$WITH_JBR" == 1 ]]; then
    : "${JBR_JDK:?--with-jbr requires the JBR_JDK env var pointing at a JBR/JDK 21+ SDK with jmods/}"
    [[ -f "$JBR_JDK/jmods/java.base.jmod" ]] || { echo "ERROR: no jmods/ in JBR_JDK=$JBR_JDK" >&2; exit 1; }
    echo "Creating jlink-trimmed JBR runtime from $JBR_JDK ..."
    STAGING=$(mktemp -d)
    trap 'rm -rf "$STAGING"' EXIT
    "$JBR_JDK/bin/jlink" \
        --add-modules java.base,java.desktop,java.instrument,java.sql,java.logging,java.xml,java.naming,java.management,java.net.http,jdk.management,jdk.security.auth,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,jdk.localedata,jdk.charsets \
        --output "$STAGING/jbr" \
        --strip-debug --no-header-files --no-man-pages --compress=zip-6
    tar -C "$STAGING" -czf "$STAGING/morphe-jbr-$JBR_VERSION.tar.gz" jbr
    install -p -m 0644 "$STAGING/morphe-jbr-$JBR_VERSION.tar.gz" \
        "$TOPDIR/SOURCES/morphe-jbr-$JBR_VERSION.tar.gz"
    RPMBUILD_ARGS+=(--with jbr)
fi

mkdir -p "$TOPDIR"/{SOURCES,SPECS,RPMS,SRPMS,BUILD,BUILDROOT}

install -p -m 0644 "$JAR" "$TOPDIR/SOURCES/morphe-desktop-$VERSION-all.jar"
install -p -m 0644 "$REPO/packaging/rpm/morphe-desktop.sh" "$TOPDIR/SOURCES/morphe-desktop.sh"
install -p -m 0644 "$REPO/packaging/rpm/morphe.desktop" "$TOPDIR/SOURCES/morphe.desktop"
install -p -m 0644 "$REPO/src/main/resources/morphe_logo.png" "$TOPDIR/SOURCES/morphe_logo.png"
install -p -m 0644 "$REPO/LICENSE" "$TOPDIR/SOURCES/LICENSE"
install -p -m 0644 "$REPO/NOTICE" "$TOPDIR/SOURCES/NOTICE"
install -p -m 0644 "$REPO/packaging/rpm/morphe-desktop.spec" "$TOPDIR/SPECS/"

rpmbuild -bb "${RPMBUILD_ARGS[@]}" --define "_topdir $TOPDIR" "$TOPDIR/SPECS/morphe-desktop.spec"

echo
echo "RPM(s):"
find "$TOPDIR/RPMS" -name 'morphe-desktop-*.rpm' -printf '%p\n' -exec ls -la {} \;

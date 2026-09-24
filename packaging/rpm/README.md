# RPM packaging (Fedora)

Builds an RPM for Morphe Desktop from the Gradle-built shadow JAR. Tested on
Fedora 44.

## Prerequisites

1. Build the shadow JAR from the repo root:

   ```bash
   ./gradlew assemble   # produces build/libs/morphe-desktop-<version>-all.jar
   ```

2. Install the RPM build tooling:

   ```bash
   sudo dnf install rpm-build
   ```

## Build

```bash
cd packaging/rpm
./build-rpm.sh
```

The RPM is written to `~/rpmbuild/RPMS/<arch>/morphe-desktop-<version>-1.<dist>.<arch>.rpm`
and installs:

- the shadow JAR under `/usr/share/java/morphe-desktop/`
- a `morphe-desktop` launcher in `/usr/bin` (GUI and CLI)
- a `.desktop` entry and an hicolor application icon

Runtime data lives under `$XDG_DATA_HOME/morphe-desktop` (the launcher exports
`MORPHE_DATA_DIR`, the override documented in `MorpheData.kt` for
package-manager installs), so the package owns nothing mutable.

Install with:

```bash
sudo dnf install <path to the .rpm>
```

## Fractional display scaling (HiDPI)

Java's AWT does not follow the desktop scale factor on X11/XWayland: at GNOME
125% a 1:1-rendered window appears at ~0.6× (XWayland runs at 2× the logical
resolution), and `-Dsun.java2d.uiScale` only changes reported coordinates, not
the native window size. The launcher mitigates this as follows:

- **System Java variant (default):** derives the effective DPI from the
  `Xft.dpi` X resource (192 on a 125% display) and passes it to the JVM via
  `sun.java2d.uiScale`, which Compose/Skiko uses for its content scale.
- **Bundled JBR variant (`./build-rpm.sh --with-jbr`):** bundles a
  jlink-trimmed JetBrains Runtime (~83 MB). JBR implements *native* HiDPI
  window scaling on X11 and auto-detects the effective DPI, giving correctly
  sized windows (and crisp text) with no flags. Requires `JBR_JDK` to point
  at a JBR/JDK 21+ SDK containing `jmods/` — e.g. the JetBrains Runtime SDK
  the Gradle toolchain downloads into `~/.gradle/jdks/`.

Either way the scale can be overridden at launch time with
`MORPHE_UI_SCALE=<factor>` (`1` disables scaling).

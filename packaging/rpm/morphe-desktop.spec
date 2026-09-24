%global debug_package %{nil}

# Version of the bundled JetBrains Runtime image (only used with jbr bcond).
%global jbr_version 21.0.11

# Set with `--with jbr` (see build-rpm.sh): bundle a jlink-trimmed JBR that
# implements native HiDPI scaling on X11/XWayland, fixing the tiny-UI problem
# under GNOME fractional display scaling. Without it the package Requires the
# system Java runtime instead.
%bcond_with jbr

%{!?pkg_version:%global pkg_version 0.0.0}

Name:           morphe-desktop
Version:        %{pkg_version}
Release:        1%{?dist}
Summary:        Patch Android apps from the desktop (GUI and CLI)

# GPLv3 with additional conditions under GPLv3 Section 7 (see NOTICE):
# attribution, name and branding restrictions for derivative works.
License:        GPL-3.0-or-later
URL:            https://morphe.software
Source0:        morphe-desktop-%{pkg_version}-all.jar
Source1:        morphe-desktop.sh
Source2:        morphe.desktop
Source3:        morphe_logo.png
Source4:        LICENSE
Source5:        NOTICE
%if %{with jbr}
Source6:        morphe-jbr-%{jbr_version}.tar.gz
%endif

%if %{with jbr}
BuildArch:      x86_64
Provides:       bundled(JetBrainsRuntime) = %{jbr_version}
%else
BuildArch:      noarch
Requires:       java >= 21
%endif

# ADB lets users install patched APKs straight from the GUI (optional).
Recommends:     android-tools

%description
Morphe Desktop is a command-line and GUI application that uses Morphe Patcher
to patch Android apps. Drag and drop an APK in the GUI, or drive the patching
process from the terminal:

    morphe-desktop patch -p patches.mpp app.apk

Based on the prior work of ReVanced CLI.

%prep
# Nothing to unpack: every source is installed verbatim in %install.

%install
install -D -p -m 0644 %{SOURCE0} %{buildroot}%{_javadir}/morphe-desktop/morphe-desktop.jar
install -D -p -m 0755 %{SOURCE1} %{buildroot}%{_bindir}/morphe-desktop
install -D -p -m 0644 %{SOURCE2} %{buildroot}%{_datadir}/applications/morphe.desktop
install -D -p -m 0644 %{SOURCE3} %{buildroot}%{_datadir}/icons/hicolor/256x256/apps/morphe-desktop.png
install -D -p -m 0644 %{SOURCE4} %{buildroot}%{_licensedir}/morphe-desktop/LICENSE
install -D -p -m 0644 %{SOURCE5} %{buildroot}%{_licensedir}/morphe-desktop/NOTICE
%if %{with jbr}
mkdir -p %{buildroot}%{_libdir}/morphe-desktop
tar -xzf %{SOURCE6} -C %{buildroot}%{_libdir}/morphe-desktop
%endif

%files
%license %{_licensedir}/morphe-desktop/LICENSE
%license %{_licensedir}/morphe-desktop/NOTICE
%{_bindir}/morphe-desktop
%{_javadir}/morphe-desktop/morphe-desktop.jar
%if %{with jbr}
%{_libdir}/morphe-desktop/jbr/
%endif
%{_datadir}/applications/morphe.desktop
%{_datadir}/icons/hicolor/256x256/apps/morphe-desktop.png

%changelog
* Fri Sep 25 2026 Morphe contributors <contact@morphe.software> - %{pkg_version}-1
- Initial RPM packaging: shadow JAR, launcher, .desktop entry and icon.
- Optional bundled JBR (jlink-trimmed) for native HiDPI scaling on X11/XWayland.

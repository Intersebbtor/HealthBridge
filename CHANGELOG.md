# Changelog

## [Unreleased]

### Changed

- New app icon (bridge with heart) for macOS and Android, adaptive and themed Android launcher icon.
- macOS menu bar icon is now a template image that adapts to light and dark menu bars.
- Redesigned macOS dashboard: step ring with daily goal, heart rate and "to go" tiles, connection status, transparent title bar, light and dark mode that follows the system.
- Redesigned Android app with the same look, edge-to-edge layout and sync interval chips.
- Menu bar menu shows goal progress and last sync time.

### Added

- `tools/icons/generate_icons.py` generates all icons from one definition.
- Preview renderer for the dashboard (`HEALTHBRIDGE_RENDER_PREVIEW`).

## [1.0.0] - 2026-05-04

Initial public release bridging health data from Android's Health Connect to macOS.

### Added

- Sync of today's steps and heart rate from Android to the Mac over local Wi-Fi.
- Health Connect integration on Android.
- macOS menu bar menu and dashboard window with current values.
- mDNS discovery and QR code pairing.

[1.0.0]: https://github.com/Intersebbtor/HealthBridge/releases/tag/v1.0.0

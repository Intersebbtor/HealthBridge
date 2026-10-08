# Roadmap and current state

_Last updated: 2026-10-08_

## Current state (v1.1.0)

**Working**

- Android: Health Connect read for today's steps and heart rate (average of the latest 5 samples from the last 24 h).
- Android: Foreground service that syncs on a configurable interval (default 10 s).
- Android: mDNS discovery of the Mac and QR code pairing as fallback.
- macOS: Ktor server on port `8080` with `POST /api/sync` (Bearer token).
- macOS: Menu bar (template icon) menu with steps, goal progress and heart rate. Dashboard with step ring, heart rate, connection status, last sync time, light/dark mode, QR pairing dialog.
- Android: Redesigned dashboard with step ring, pairing and sync interval chips, follows the system theme.
- Release v1.1.0 with DMG (arm64) and APK, built from this repo.

**Not there yet**

- Mac app keeps data in memory only. No history, no charts.
- Sleep permission is requested on Android, but sleep is not read or shown.
- Only macOS is packaged. Windows and Linux are not built or tested.

## Known issues

| # | Issue | Impact |
| :--- | :--- | :--- |
| 1 | API token is broadcast in the mDNS TXT record (`token=...`). | Anyone on the same LAN can read the token and post fake data. QR pairing adds no security on top. |
| 2 | Token is regenerated on every Mac app start. | Android has to re-discover or re-pair after each restart. |
| 3 | Port `8080` is hardcoded on both sides. | Conflicts with other local dev servers. Discovery reports the port, but `SyncManager` ignores it. |
| 4 | Plain HTTP (`usesCleartextTraffic=true`). | Data is unencrypted on the LAN. |
| 5 | Released APK is a debug build signed with the debug key. | Not suitable for wider distribution, cannot be updated by a properly signed build later without uninstalling. |
| 6 | DMG is arm64 only, not signed or notarized. | Intel Macs not supported, Gatekeeper warning on first launch. |
| 7 | No tests and no CI. | Regressions go unnoticed. |
| 8 | Old Gradle/Kotlin stack (Kotlin 1.9.20, Compose 1.5.11, AGP 8.2.2). | Harder to build with current toolchains. |

## Next

**Security and stability**

- [ ] Remove the token from mDNS. Use mDNS only to find the host and port, get the token via QR pairing.
- [ ] Persist the token on the Mac so pairing survives restarts. Add a "reset pairing" action.
- [ ] Use the port from discovery instead of hardcoding `8080`.
- [ ] Release-signed APK and a GitHub Actions workflow that builds DMG and APK on tag.

**Features**

- [x] Daily step goal with progress ring and menu bar progress (fixed at 10,000).
- [ ] Make the daily step goal configurable.
- [ ] Notification on the Mac when the step goal is reached.
- [ ] Steps directly in the menu bar title, not only in the dropdown.
- [ ] Sleep summary of last night.
- [ ] Simple history (today vs. last 7 days).

**Platform and polish**

- [ ] Universal (arm64 + x86_64) DMG, signing and notarization.
- [ ] Windows and Linux packages (Compose Desktop supports both).
- [x] Final logo and consistent Material 3 theme across Android and desktop.
- [x] Screenshots for the README.
- [x] Menu bar screenshot with the new template icon.
- [ ] Short demo GIF for the README.

# HealthBridge

HealthBridge bridges your health data from Android's **Health Connect** to your **macOS** desktop. Monitor your steps and heart rate in real-time directly from your Mac's menu bar.

> **Disclaimer:** This project was developed primarily by a single developer with significant assistance from AI. While functional, it is provided "as is" and should be used at your own risk.

## Features

- **Real-time Sync:** Seamlessly pushes health data from your Android device to your Mac.
- **Android Health Connect Integration:** Accesses steps and heart rate via Google's Health Connect.
- **Automatic Discovery:** Automatically finds your Mac on the local network.
- **Secure Pairing:** Quick QR code pairing for a secure initial setup.
- **Menu Bar App:** A minimalist tray icon for macOS to see current stats at a glance.
- **Modern Dashboard:** A beautiful, modern interface for detailed data visualization.

## Roadmap

- **Step Goals:** Setting and tracking personalized daily activity targets.
- **Notifications:** Alerts when step goals are reached.
- **Expanded Platform Support:** Future support for Windows and Linux.

## Getting Started

### Prerequisites

- **Android:** Android 14+ (or Android 8+ with the Health Connect app installed).
- **macOS:** macOS 11+.

### Download

You can find the latest release artifacts in the `release_artifacts/` folder:
- **Android:** `release_artifacts/HealthBridge-Android.apk`
- **macOS:** `release_artifacts/HealthBridge-macOS.dmg`

### Setup

1. **macOS:** Install the `.dmg` file and launch the app. It will appear in your menu bar.
2. **Android:** Install the `.apk` file and launch the app.
3. **Pairing:** Click the menu bar icon on your Mac to show the pairing QR code. Scan this code with the Android app to connect.
4. **Permissions:** Grant the necessary Health Connect permissions on your Android device when prompted.

## Project Structure

- `composeApp/`: The Android application (Kotlin/Compose).
- `desktop/`: The macOS desktop application.
- `release_artifacts/`: Compiled binaries for easy download.

## Contribute

Contributions are highly welcome! Whether it's bug reports, feature requests, or pull requests, feel free to join the project and help us bridge the gap between platforms.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

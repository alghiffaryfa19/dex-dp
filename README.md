# NativeDex

> Make zFlip 5 great again

~~NativeDex is a utility that unlocks Samsung DeX **natively** on your device (On One UI 8.0, the Z Flip 5 simply mirrors the built-in display), without the need for ADB, Root, Shizuku, Virtual Display or USB Debugging!~~

NativeDex is a utility that unlocks Samsung DeX on your device's built-in screen or external monitor. 
**Update:** NativeDex now utilizes **Shizuku** and **Virtual Displays** to provide a complete DeX experience.

### Why Move to Shizuku & Virtual Display? (Technical Reasons)
In its initial iteration, NativeDex attempted to run DeX purely "natively" (directly on a *physical external display*) without the need for ADB, Shizuku, or Virtual Displays. However, this approach encountered roadblocks due to Android and Samsung One UI restrictions:
1. **System Decorations (Wallpaper, Taskbar, Navbar):** For DeX to correctly display the wallpaper and desktop interface, Android requires the `WRITE_SECURE_SETTINGS` permission and the execution of several hidden APIs (such as `setShouldShowSystemDecors`). Without shell privileges from Shizuku/ADB, this execution silently fails, causing the DeX wallpaper to remain completely black.
2. **Flexibility (DeX on Phone Screen):** The public API for rendering `SecondaryLauncher` on a secondary display is extremely rigid. By switching to Shizuku, we can create a *Virtual Display* with *shell privileges* that supports hidden flags like `VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS`. 
3. **Better User Experience:** Thanks to the Virtual Display, DeX can now be displayed **directly on your phone's screen (built-in display)** without needing to connect via HDMI, complete with seamless direct touch input mapping.

Currently tested on a **Galaxy Z Flip 5 running One UI 8.0 (Android 16)**.

<div align="center">
  <img src="img/photo_1.jpeg" width="30%" />
  <img src="img/photo_2.jpeg" width="30%" />
  <img src="img/photo_3.jpeg" width="30%" />
</div>

## Requirements

1. A secondary display/monitor with a USB Type-C to HDMI hub.
2. A Galaxy Z Flip 5 running One UI 8.0 or above. _(Feel free to test on other Samsung devices with different One UI versions!)_

## Installation & Setup Guide

Follow these steps to set up NativeDex using Shizuku:

1. **Install Shizuku:** Download and install the [Shizuku app](https://shizuku.rikka.app/) from the Google Play Store or GitHub.
2. **Start Shizuku:** Follow the instructions within the Shizuku app to start its service (typically via Wireless Debugging or ADB).
3. **Download NativeDex:** Download the latest NativeDex APK from the [Releases page](https://github.com/alghiffaryfa19/dex-dp/releases) and install it on your device.
4. **Open NativeDex:** Launch the NativeDex application.
5. **Launch DeX:** Tap the **"Launch DeX on Phone Screen (Shizuku)"** button.
6. **Grant Shizuku Permission:** When prompted by Shizuku, select "Allow all the time" to grant NativeDex the necessary shell privileges.
7. **Enjoy DeX:** Samsung DeX will now launch seamlessly! If you have an external HDMI monitor connected, the virtual display will automatically adapt to its resolution. Otherwise, DeX will render directly on your phone's built-in display.

## Contributing & Issues

Found a bug or have a great feature idea? I welcome all contributions and feedback! Feel free to open an issue or submit a pull request.

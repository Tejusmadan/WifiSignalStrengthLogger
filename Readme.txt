Overview

A Compose-based Android app that:

Scans nearby Wi‑Fi APs 100× per user-selected location (three tabs).

Shows each AP’s RSSI range (min..max).

Expandable 10×10 sample matrix per AP.

Comparison tab displays side-by-side RSSI ranges across locations.

Real‑time linear progress bar during scans.

Prerequisites

Android Studio

Min SDK 24, Target SDK 35

Runtime permissions:

ACCESS_FINE_LOCATION

ACCESS_WIFI_STATE

CHANGE_WIFI_STATE

Project Structure

/app
  ├─ src/main
  │    ├─ AndroidManifest.xml
  │    ├─ java/.../MainActivity.kt  # Compose UI + scan logic

Build & Run

Clone the repo and open in Android Studio.

Grant requested permissions on launch.

Switch to a Location tab, tap Scan, and view results; use Comparison for analysis.


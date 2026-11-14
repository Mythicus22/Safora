# SAFORA Wearable Firmware — ESP32-CAM Setup & Flashing Guide

This directory contains the production Arduino IDE firmware for the **SAFORA Intelligent Personal Safety Wearable Necklace** based on the **AI-Thinker ESP32-CAM** module with the **RHYX M21-45** camera sensor.

---

## 1. Directory Structure

```text
hardware/safora_wearable/
├── safora_wearable.ino    # Main Arduino sketch & FreeRTOS dual-core task scheduler
├── Config.h               # Pinouts, BLE UUIDs, Wi-Fi SoftAP credentials & segment settings
├── CameraManager.h        # Camera driver with automatic RGB565-to-JPEG conversion (for RHYX M21-45)
├── StorageManager.h       # MicroSD card driver (1-bit SD_MMC mode), segment folder & manifest creator
├── BleManager.h           # BLE GATT Server matching the Android companion app UUIDs
├── WebServerManager.h     # HTTP REST server on 192.168.4.1 for evidence downloads
└── README.md              # Setup and wiring guide
```

---

## 2. Hardware Wiring & Pinout

### A. Power System
- Connect a **3.7V LiPo Battery** (with built-in PCM protection) to a boost converter / charging circuit (e.g., TP4056 + 5V boost).
- Provide a clean, regulated **5V** to the **5V pin** on the ESP32-CAM.
- **Current Requirement:** The ESP32-CAM with Wi-Fi and camera bursts up to **350–500mA**. Ensure your regulator can deliver **1A peak**.

### B. Physical SOS Push-Button
- Connect one terminal of the push-button to **GPIO 13**.
- Connect the other terminal to **GND**.
- *Note:* The code configures `INPUT_PULLUP` on GPIO 13. When pressed, the pin is pulled LOW, triggering the hardware interrupt debounced at 400ms.
- *Why GPIO 13?* By running the MicroSD card in **SD_MMC 1-bit mode**, GPIO 12 and GPIO 13 remain completely free from SD bus conflict!

### C. Status Indicator
- **Red Status LED (GPIO 33):** Blinks twice on boot. Stays ON while recording.
- **MicroSD Card:** Insert a formatted FAT32 MicroSD card into the onboard slot.

---

## 3. Arduino IDE Board & Library Setup

1. Open **Arduino IDE**.
2. Go to **File -> Preferences**, and add the ESP32 board manager URL:
   ```text
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
3. Go to **Tools -> Board -> Boards Manager**, search for `esp32` by Espressif Systems and install version **2.0.x** (or later).
4. Select board settings in the **Tools** menu:
   - **Board:** `AI Thinker ESP32-CAM`
   - **CPU Frequency:** `240MHz (WiFi/BT)`
   - **Flash Frequency:** `80MHz`
   - **Flash Mode:** `QIO`
   - **Partition Scheme:** `Huge APP (3MB No OTA/1MB SPIFFS)` or `Minimal SPIFFS (Large APPS with OTA)`
   - **PSRAM:** `Enabled` (Crucial for RGB565 to JPEG conversion buffer)
   - **Port:** Select your FTDI programmer COM / ttyUSB port.

---

## 4. Flashing Instructions

Because the AI-Thinker ESP32-CAM does not have a native USB port:
1. Connect an FTDI USB-to-UART adapter:
   - `FTDI VCC (5V)` -> `ESP32-CAM 5V`
   - `FTDI GND` -> `ESP32-CAM GND`
   - `FTDI TX` -> `ESP32-CAM U0R (GPIO 3)`
   - `FTDI RX` -> `ESP32-CAM U0T (GPIO 1)`
   - **Jumper GPIO 0 to GND** (enters flash mode).
2. Hit the ESP32-CAM **RST** button.
3. In Arduino IDE, click **Upload**.
4. Once upload completes, **disconnect GPIO 0 from GND** and press the **RST** button to boot into runtime mode.

---

## 5. Dual-Core Architecture Breakdown

- **Core 0 (Camera & Storage Engine):**
  - Runs `cameraCaptureTask`.
  - Captures frames from the RHYX M21-45 camera. If sensor is in RGB565 format, converts to JPEG in PSRAM via `fmt2jpg()`.
  - Groups frames into 5-second directories: `/evidence/seg_1/frame_001.jpg`, `frame_002.jpg`...
  - Writes `/evidence/seg_1/manifest.json`.
- **Core 1 (Communication Engine):**
  - Runs the main loop: BLE GATT server + SoftAP WebServer.
  - Keeps BLE fully responsive with zero frame drop or connection timeout while heavy camera capturing is occurring!

---

## 6. Protocols & Endpoints Implemented

### BLE (Matching Android Companion App):
- **Service UUID:** `7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001`
- **Commands Characteristic (Write):** `7c1e0002-8a4b-4f3c-9d2e-1b6a5c7d0001` (`START_RECORD`, `STOP_RECORD`, `GET_STATUS`, `PING`)
- **Events Characteristic (Notify):** `7c1e0004-8a4b-4f3c-9d2e-1b6a5c7d0001` (`MANUAL_SOS`, `RECORDING_STARTED`, `RECORDING_STOPPED`, `CAMERA_ERROR`, `SD_CARD_ERROR`, `STORAGE_FULL`)

### Wi-Fi SoftAP & HTTP Server:
- **SSID:** `SAFORA_CAM_AP`
- **Password:** `safora123`
- **Static IP:** `192.168.4.1` (Port 80)
- **Endpoints:**
  - `GET /evidence/list` — Returns JSON array of all recorded segment directories.
  - `GET /evidence?segment=1` — Returns `manifest.json` for that segment.
  - `GET /evidence?segment=1&file=frame_001.jpg` — Streams the raw JPEG image bytes.
  - `GET /status` — Returns storage and hardware health status.

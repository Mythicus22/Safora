#pragma once

#include <Arduino.h>

// =========================================================================
// AI-Thinker ESP32-CAM Pin Configuration (Verified with RHYX M21-45)
// =========================================================================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5

#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// Onboard flash LED / Status indicator
#define LED_FLASH_PIN      4
#define LED_STATUS_PIN    33

// Physical Necklace SOS Button (Pull-up with internal resistor, active LOW)
// Uses GPIO 13 (Free when MicroSD is used in SD_MMC 1-bit mode!)
#define SOS_BUTTON_PIN    13

// =========================================================================
// Camera Configuration
// Notice: RHYX M21-45 requires xclk_freq_hz = 10000000 (10MHz) and PIXFORMAT_RGB565
// =========================================================================
#define CAMERA_XCLK_FREQ_HZ   10000000
#define CAMERA_FRAME_SIZE     FRAMESIZE_QVGA
#define JPEG_QUALITY_VAL      80

// =========================================================================
// Wi-Fi Access Point Configuration
// Compatible with both SAFORA companion app and legacy RHYX setups
// =========================================================================
#define WIFI_AP_SSID          "SAFORA_CAM_AP"
#define WIFI_AP_PASS          "12345678"
#define WIFI_AP_CHANNEL        1
#define WIFI_AP_MAX_CONN       4
#define HTTP_SERVER_PORT       80

// =========================================================================
// BLE GATT Specification (Matching Android Companion App Exactly)
// =========================================================================
#define BLE_DEVICE_NAME             "SAFORA_DEVICE"
#define BLE_SERVICE_UUID            "7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_COMMAND_UUID       "7c1e0002-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_STATUS_UUID        "7c1e0003-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_EVENTS_UUID        "7c1e0004-8a4b-4f3c-9d2e-1b6a5c7d0001"

// =========================================================================
// Recording & Storage Configuration
// =========================================================================
#define SEGMENT_DURATION_MS         5000     // 5 seconds per evidence segment
#define TARGET_FPS                  5        // 5 FPS captures (~25 frames per 5s segment)
#define EVIDENCE_BASE_DIR           "/evidence"

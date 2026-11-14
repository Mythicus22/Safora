#include <Arduino.h>
#include "Config.h"
#include "CameraManager.h"
#include "StorageManager.h"
#include "BleManager.h"
#include "WebServerManager.h"

// =========================================================================
// Global Subsystem Instances
// =========================================================================
CameraManager camera;
StorageManager storage;
BleManager ble;
WebServerManager webServer(storage);

// State Flags
volatile bool isRecording = false;
volatile bool sosButtonPressed = false;
unsigned long lastButtonInterruptMs = 0;
unsigned long lastStatusPingMs = 0;

// Camera Task Handle (Core 0 Dedicated Task)
TaskHandle_t cameraTaskHandle = NULL;

// Forward Declarations
void handleBleCommand(const String &cmd);
void IRAM_ATTR onSosButtonInterrupt();
void cameraCaptureTask(void *parameter);

// =========================================================================
// Setup
// =========================================================================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n=======================================================");
    Serial.println("         SAFORA WEARABLE — ESP32-CAM FIRMWARE         ");
    Serial.println("=======================================================");

    // 1. Setup Status LED & Flashlight
    pinMode(LED_STATUS_PIN, OUTPUT);
    digitalWrite(LED_STATUS_PIN, HIGH); // Inverted on ESP32-CAM (HIGH = OFF)

    // 2. Setup Physical SOS Button on GPIO 13 with Internal Pull-up
    pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(SOS_BUTTON_PIN), onSosButtonInterrupt, FALLING);
    Serial.printf("[Button] Hardware SOS button attached on GPIO %d (Active LOW)\n", SOS_BUTTON_PIN);

    // 3. Initialize Camera (Using tested RHYX M21-45 10MHz clock & RGB565 frame2jpg)
    if (!camera.init()) {
        Serial.println("[Error] Camera initialization failed! Sending CAMERA_ERROR...");
        ble.notifyEvent("CAMERA_ERROR");
    }

    // 4. Initialize MicroSD Card Storage in 1-bit SD_MMC Mode
    if (!storage.init()) {
        Serial.println("[Error] SD Card initialization failed! Sending SD_CARD_ERROR...");
        ble.notifyEvent("SD_CARD_ERROR");
    }

    // 5. Initialize Wi-Fi Access Point & Native esp_http_server (Serves /stream and /evidence)
    webServer.init();

    // 6. Initialize BLE GATT Server
    ble.init(handleBleCommand);

    // 7. Launch Dedicated FreeRTOS Camera Capture Task on Core 0
    // This guarantees camera captures & file writes never stutter or block BLE on Core 1!
    xTaskCreatePinnedToCore(
        cameraCaptureTask,      // Task function
        "CameraTask",           // Task name
        8192,                   // Stack size (bytes)
        NULL,                   // Parameter
        2,                      // Priority
        &cameraTaskHandle,      // Task handle
        0                       // Core 0 (System/BLE runs on Core 1)
    );

    Serial.println("[System] Initialization complete. Standby for triggers.");
    // Blink status LED twice to indicate ready
    for (int i = 0; i < 2; i++) {
        digitalWrite(LED_STATUS_PIN, LOW);
        delay(100);
        digitalWrite(LED_STATUS_PIN, HIGH);
        delay(100);
    }
}

// =========================================================================
// Main Loop (Runs on Core 1: BLE & Button Handling)
// Note: esp_http_server runs its own asynchronous worker tasks in the background!
// =========================================================================
void loop() {
    // 1. Handle Physical SOS Button Press Event
    if (sosButtonPressed) {
        sosButtonPressed = false;
        Serial.println("[Button] PHYSICAL SOS BUTTON TRIGGERED!");

        // Notify Phone immediately over BLE (triggers calls, SMS & GPS broadcast)
        ble.notifyEvent("MANUAL_SOS");

        // Automatically start recording camera segments
        if (!isRecording) {
            isRecording = true;
            ble.notifyEvent("RECORDING_STARTED");
            digitalWrite(LED_STATUS_PIN, LOW); // LED ON
        }
    }

    // 2. Periodic Status Ping over BLE every 15 seconds
    if (millis() - lastStatusPingMs > 15000) {
        lastStatusPingMs = millis();
        if (storage.isStorageFull()) {
            ble.notifyEvent("STORAGE_FULL");
        }
        ble.updateStatus(88, isRecording);
    }

    vTaskDelay(pdMS_TO_TICKS(20));
}

// =========================================================================
// Hardware Interrupt for Physical SOS Button (Debounced)
// =========================================================================
void IRAM_ATTR onSosButtonInterrupt() {
    unsigned long now = millis();
    if (now - lastButtonInterruptMs > 400) { // 400ms debounce
        lastButtonInterruptMs = now;
        sosButtonPressed = true;
    }
}

// =========================================================================
// BLE Command Dispatcher (Commands received from Android App)
// =========================================================================
void handleBleCommand(const String &cmd) {
    if (cmd == "START_RECORD") {
        if (!isRecording) {
            isRecording = true;
            ble.notifyEvent("RECORDING_STARTED");
            digitalWrite(LED_STATUS_PIN, LOW); // Turn on red status LED
            Serial.println("[BLE] Recording STARTED by Android app.");
        }
    } else if (cmd == "STOP_RECORD") {
        if (isRecording) {
            isRecording = false;
            ble.notifyEvent("RECORDING_STOPPED");
            digitalWrite(LED_STATUS_PIN, HIGH); // Turn off red status LED
            Serial.println("[BLE] Recording STOPPED by Android app.");
        }
    } else if (cmd == "GET_STATUS") {
        ble.updateStatus(88, isRecording);
    } else if (cmd == "PING") {
        ble.notifyEvent("PONG");
    } else {
        Serial.printf("[BLE] Unknown command received: %s\n", cmd.c_str());
    }
}

// =========================================================================
// FreeRTOS Dedicated Camera Task (Runs on Core 0)
// =========================================================================
void cameraCaptureTask(void *parameter) {
    Serial.println("[CameraTask] Dedicated camera task started on Core 0");

    while (true) {
        if (isRecording) {
            if (storage.isStorageFull()) {
                Serial.println("[CameraTask] Storage full! Halting recording.");
                ble.notifyEvent("STORAGE_FULL");
                isRecording = false;
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }

            int segmentIndex = storage.getNextSegmentIndex();
            String dirPath;
            if (!storage.createSegmentDirectory(segmentIndex, dirPath)) {
                Serial.println("[CameraTask] Failed to create segment directory");
                ble.notifyEvent("SD_CARD_ERROR");
                vTaskDelay(pdMS_TO_TICKS(1000));
                continue;
            }

            Serial.printf("[CameraTask] >>> Starting 5-second evidence segment #%d\n", segmentIndex);
            unsigned long segmentStartTime = millis();
            int frameCount = 0;

            // Capture frames for SEGMENT_DURATION_MS (5000ms)
            while (isRecording && (millis() - segmentStartTime < SEGMENT_DURATION_MS)) {
                unsigned long frameStartTime = millis();

                uint8_t *jpegBuf = NULL;
                size_t jpegLen = 0;

                if (camera.captureJpegFrame(&jpegBuf, &jpegLen)) {
                    frameCount++;
                    if (!storage.saveJpegFrame(dirPath, frameCount, jpegBuf, jpegLen)) {
                        Serial.println("[CameraTask] Failed to save frame to MicroSD");
                    }
                    camera.releaseJpeg(jpegBuf);
                } else {
                    Serial.println("[CameraTask] Frame capture dropped");
                }

                // Target FPS pacing (e.g., ~200ms per frame for 5 FPS)
                unsigned long elapsed = millis() - frameStartTime;
                unsigned long targetInterval = 1000 / TARGET_FPS;
                if (elapsed < targetInterval) {
                    vTaskDelay(pdMS_TO_TICKS(targetInterval - elapsed));
                }
            }

            // Write segment manifest.json
            unsigned long totalDuration = millis() - segmentStartTime;
            storage.saveManifest(dirPath, segmentIndex, frameCount, totalDuration);
            Serial.printf("[CameraTask] <<< Completed segment #%d (%d frames in %lums)\n", segmentIndex, frameCount, totalDuration);
        } else {
            // Idle delay when not recording
            vTaskDelay(pdMS_TO_TICKS(100));
        }
    }
}

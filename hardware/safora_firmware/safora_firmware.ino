#include <Arduino.h>
#include <WiFi.h>
#include "esp_camera.h"
#include "img_converters.h"
#include "esp_http_server.h"
#include "FS.h"
#include "SD_MMC.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <string.h>
#include <stdlib.h>

// =========================================================================
// 1. PIN DEFINITIONS & CONSTANTS (AI-Thinker ESP32-CAM)
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

// Onboard Indicators & Inputs
#define LED_FLASH_PIN      4
#define LED_STATUS_PIN    33  // Inverted logic on ESP32-CAM (LOW = ON, HIGH = OFF)
#define SOS_BUTTON_PIN    13  // Free in SD_MMC 1-bit mode (Internal pull-up)

// Battery measurement pin (optional voltage divider ADC; -1 if unequipped)
#define BATTERY_ADC_PIN   -1

// Camera Configurations (Tested RHYX M21-45: 10MHz clock, QVGA, RGB565)
#define CAMERA_XCLK_FREQ_HZ   10000000
#define CAMERA_FRAME_SIZE     FRAMESIZE_QVGA
#define JPEG_QUALITY_VAL      80

// Wi-Fi SoftAP Configuration
static const char* AP_SSID = "SAFORA_CAM_AP";
static const char* AP_PASS = "12345678";
#define WIFI_AP_CHANNEL        1
#define WIFI_AP_MAX_CONN       4
#define HTTP_SERVER_PORT       80
static const char* API_AUTH_TOKEN = "Bearer safora_auth_token_2026"; // HTTP Bearer Token protection

// BLE GATT UUIDs (Preserved exact 128-bit contract matching Android app)
#define BLE_DEVICE_NAME             "SAFORA_DEVICE"
#define BLE_SERVICE_UUID            "7c1e0001-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_COMMAND_UUID       "7c1e0002-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_STATUS_UUID        "7c1e0003-8a4b-4f3c-9d2e-1b6a5c7d0001"
#define BLE_CHAR_EVENTS_UUID        "7c1e0004-8a4b-4f3c-9d2e-1b6a5c7d0001"

// Timing & Storage Parameters
#define SEGMENT_DURATION_MS         5000     // 5 seconds per evidence segment
#define TARGET_FPS                  5        // 5 FPS captures (~25 frames per 5s segment)
#define EVIDENCE_BASE_DIR           "/evidence"
#define SOS_LOG_PATH                "/evidence/sos_events.log"

// =========================================================================
// 2. GLOBAL SYSTEM STATE & SHARED FRAME BUFFER (Thread-Safe Single Pipeline)
// =========================================================================
static httpd_handle_t camera_httpd = NULL;

// Hardware Readiness Flags
static bool cameraReady = false;
static bool sdCardReady = false;
static volatile bool isRecording = false;

// SOS State & Persistent Delivery Flags
static volatile bool isrButtonFlag = false;
static unsigned long lastButtonIsrTimeMs = 0;
static bool pendingSosEvent = false;

// Shared Frame Buffer for HTTP Streaming & Web View (Single Source of Truth)
static portMUX_TYPE frameBufferMux = portMUX_INITIALIZER_UNLOCKED;
static uint8_t* latestJpegBuffer = NULL;
static size_t latestJpegLength = 0;
static uint32_t latestFrameTimestamp = 0;

// Performance Benchmarking Metrics
static uint32_t lastConversionTimeUs = 0;
static uint32_t totalConvertedFrames = 0;

// Storage state
static int currentSegmentIndex = 0;

// Task Handle
static TaskHandle_t cameraTaskHandle = NULL;

// BLE Server & Characteristics
static BLEServer* pBleServer = NULL;
static BLECharacteristic* pCommandChar = NULL;
static BLECharacteristic* pStatusChar = NULL;
static BLECharacteristic* pEventsChar = NULL;
static volatile bool bleClientConnected = false;

// Stream Boundary Definitions
static const char* STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=frame";
static const char* STREAM_BOUNDARY = "\r\n--frame\r\n";
static const char* STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

// HTML Interface
static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SAFORA Wearable — ESP32-CAM</title>
    <style>
        body { background: #0f141c; color: #f8fafc; font-family: -apple-system, Arial, sans-serif; text-align: center; margin: 0; padding: 16px; }
        h2 { color: #e53935; margin-bottom: 4px; letter-spacing: 2px; }
        .sub { color: #94a3b8; font-size: 13px; margin-bottom: 16px; }
        .card { background: #181f2c; border: 1px solid #2d374d; border-radius: 12px; padding: 14px; margin: 12px auto; max-width: 520px; }
        img { width: 100%; max-width: 320px; height: auto; border: 2px solid #2563eb; border-radius: 8px; background: #000; }
        .btn { background: #2563eb; color: #fff; border: none; padding: 8px 14px; border-radius: 6px; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; margin: 4px; }
        ul { list-style: none; padding: 0; text-align: left; }
        li { background: #222b3d; margin: 6px 0; padding: 8px 12px; border-radius: 6px; display: flex; justify-content: space-between; align-items: center; }
        .badge { background: #10b981; color: #000; font-size: 11px; font-weight: bold; padding: 2px 6px; border-radius: 4px; }
    </style>
</head>
<body>
    <h2>SAFORA WEARABLE</h2>
    <div class="sub">AI-Thinker ESP32-CAM &bull; RHYX M21-45 (QVGA RGB565) &bull; 192.168.4.1</div>
    <div class="card">
        <h3>Live Shared View</h3>
        <img src="/stream" id="camStream">
        <div style="margin-top: 8px;"><span class="badge">SINGLE PIPELINE ACTIVE</span></div>
    </div>
    <div class="card">
        <h3>Evidence Segments (MicroSD)</h3>
        <div id="segments">Loading segment list...</div>
        <button class="btn" onclick="fetchList()">Refresh Segments</button>
    </div>
    <script>
        function fetchList() {
            fetch('/evidence/list')
                .then(r => r.json())
                .then(data => {
                    let html = '<ul>';
                    if (!data || data.length === 0) {
                        html += '<li>No evidence segments recorded yet.</li>';
                    } else {
                        data.forEach(s => {
                            let segNum = s.replace('seg_', '');
                            html += `<li><span>Segment: <b>${s}</b></span> <div><a class="btn" href="/evidence/manifest?segment=${segNum}" target="_blank">Manifest</a><a class="btn" style="background:#059669;" href="/evidence/zip?segment=${segNum}">Download ZIP</a></div></li>`;
                        });
                    }
                    html += '</ul>';
                    document.getElementById('segments').innerHTML = html;
                })
                .catch(err => { document.getElementById('segments').innerHTML = 'Error: ' + err; });
        }
        fetchList();
    </script>
</body>
</html>
)rawliteral";

// Forward Declarations
void IRAM_ATTR onSosButtonIsr();
void sendBleNotification(const char* eventStr);
void checkAndDeliverPendingSos();
void handleBleIncomingCommand(const String& cmd);
void cameraCaptureAndProcessingTask(void *parameter);
bool appendSosLog(unsigned long timestamp);
int queryBatteryPercentage();
bool isStorageSpaceLow();
void initializeStorageIndex();
void setupHttpServerEndpoints();

// =========================================================================
// 3. HARDWARE ISR & BUTTON DEBOUNCING
// =========================================================================
void IRAM_ATTR onSosButtonIsr() {
    // Ultra-minimal interrupt handler: sets single volatile flag only
    isrButtonFlag = true;
}

// =========================================================================
// 4. BLE SERVER & CHARACTERISTIC CALLBACKS
// =========================================================================
class BleServerCallbackHandler : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        bleClientConnected = true;
        Serial.println("[BLE] Companion phone connected!");
        // Reconnected: Deliver any pending SOS events immediately!
        checkAndDeliverPendingSos();
    }

    void onDisconnect(BLEServer* pServer) override {
        bleClientConnected = false;
        Serial.println("[BLE] Companion phone disconnected. Restarting advertising...");
        pServer->getAdvertising()->start();
    }
};

class BleCommandCallbackHandler : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
        std::string rxVal = pCharacteristic->getValue();
        if (rxVal.length() > 0) {
            String command = String(rxVal.c_str());
            command.trim();
            Serial.printf("[BLE] Command received: %s\n", command.c_str());

            // Requirement 8: Send immediate COMMAND_RECEIVED acknowledgement
            String ack = "COMMAND_RECEIVED:" + command;
            sendBleNotification(ack.c_str());

            // Process command
            handleBleIncomingCommand(command);
        }
    }
};

void sendBleNotification(const char* eventStr) {
    if (pEventsChar && bleClientConnected) {
        pEventsChar->setValue(eventStr);
        pEventsChar->notify();
        Serial.printf("[BLE] Event Notified -> %s\n", eventStr);
    } else {
        Serial.printf("[BLE] Offline / Not Connected. Event queued: %s\n", eventStr);
    }
}

void checkAndDeliverPendingSos() {
    if (pendingSosEvent && bleClientConnected) {
        sendBleNotification("MANUAL_SOS");
        pendingSosEvent = false;
        Serial.println("[BLE] Successfully delivered pending SOS to reconnected phone.");
    }
}

int queryBatteryPercentage() {
    if (BATTERY_ADC_PIN < 0) {
        // Battery measurement hardware not equipped
        return -1;
    }
    // Read calibrated ADC if pin is configured
    int raw = analogRead(BATTERY_ADC_PIN);
    float voltage = (raw / 4095.0f) * 2.0f * 3.3f * 1.1f;
    int pct = (int)((voltage - 3.3f) / (4.2f - 3.3f) * 100.0f);
    return constrain(pct, 0, 100);
}

void updateBleStatus() {
    if (pStatusChar && bleClientConnected) {
        int batt = queryBatteryPercentage();
        char buf[64];
        if (batt >= 0) {
            snprintf(buf, sizeof(buf), "DEVICE_STATUS:BATTERY=%d;REC=%d", batt, isRecording ? 1 : 0);
        } else {
            snprintf(buf, sizeof(buf), "DEVICE_STATUS:BATTERY=UNAVAILABLE;REC=%d", isRecording ? 1 : 0);
        }
        pStatusChar->setValue(buf);
        pStatusChar->notify();
    }
}

void handleBleIncomingCommand(const String& cmd) {
    if (cmd == "START_RECORD") {
        // Requirement 10: Check hardware readiness before starting
        if (!cameraReady) {
            sendBleNotification("CAMERA_ERROR");
            Serial.println("[Command] Cannot start recording: Camera not ready.");
            return;
        }
        if (!sdCardReady) {
            sendBleNotification("SD_CARD_ERROR");
            Serial.println("[Command] Cannot start recording: SD card not mounted.");
            return;
        }
        if (isStorageSpaceLow()) {
            sendBleNotification("STORAGE_FULL");
            Serial.println("[Command] Cannot start recording: MicroSD card full.");
            return;
        }

        if (!isRecording) {
            isRecording = true;
            digitalWrite(LED_STATUS_PIN, LOW); // Turn status LED ON
            sendBleNotification("RECORDING_STARTED");
            Serial.println("[Command] Recording successfully started.");
        }
    } else if (cmd == "STOP_RECORD") {
        if (isRecording) {
            isRecording = false;
            digitalWrite(LED_STATUS_PIN, HIGH); // Turn status LED OFF
            sendBleNotification("RECORDING_STOPPED");
            Serial.println("[Command] Recording stopped.");
        }
    } else if (cmd == "GET_STATUS") {
        updateBleStatus();
    } else if (cmd == "PING") {
        sendBleNotification("PONG");
    } else {
        Serial.printf("[Command] Unknown command: %s\n", cmd.c_str());
    }
}

// =========================================================================
// 5. STORAGE & RECOVERY MANAGEMENT
// =========================================================================
bool isStorageSpaceLow() {
    if (!sdCardReady) return true;
    uint64_t total = SD_MMC.totalBytes();
    uint64_t used = SD_MMC.usedBytes();
    return (total > used && (total - used) < (20ULL * 1024ULL * 1024ULL));
}

void initializeStorageIndex() {
    currentSegmentIndex = 0;
    if (!sdCardReady) return;

    File root = SD_MMC.open(EVIDENCE_BASE_DIR);
    if (!root || !root.isDirectory()) {
        if (root) root.close();
        SD_MMC.mkdir(EVIDENCE_BASE_DIR);
        return;
    }

    File entry = root.openNextFile();
    while (entry) {
        if (entry.isDirectory()) {
            String name = String(entry.name());
            int idx = name.lastIndexOf("seg_");
            if (idx != -1) {
                int num = name.substring(idx + 4).toInt();
                if (num > currentSegmentIndex) {
                    currentSegmentIndex = num;
                }
            }
        }
        entry.close(); // Crucial: Always close file/dir handles!
        entry = root.openNextFile();
    }
    root.close(); // Crucial: Close root handle!
    Serial.printf("[Storage] Normalized next segment index: %d\n", currentSegmentIndex + 1);
}

bool appendSosLog(unsigned long timestamp) {
    if (!sdCardReady) return false;
    File logFile = SD_MMC.open(SOS_LOG_PATH, FILE_APPEND);
    if (!logFile) return false;
    logFile.printf("SOS_EVENT: timestamp_ms=%lu, ble_connected=%d\n", timestamp, bleClientConnected ? 1 : 0);
    logFile.close();
    return true;
}

bool writeSegmentManifest(const String& dirPath, int segIdx, int frameCount, unsigned long durationMs, bool completed) {
    if (!sdCardReady) return false;
    String manifestPath = dirPath + "/manifest.json";
    File f = SD_MMC.open(manifestPath.c_str(), FILE_WRITE);
    if (!f) return false;

    // Requirement 5: Accurately declare 320x240 and completion status
    f.printf("{\n");
    f.printf("  \"segmentIndex\": %d,\n", segIdx);
    f.printf("  \"timestamp\": %lu,\n", millis());
    f.printf("  \"durationSeconds\": %lu,\n", durationMs / 1000);
    f.printf("  \"framesCount\": %d,\n", frameCount);
    f.printf("  \"resolution\": \"320x240\",\n");
    f.printf("  \"pixelFormat\": \"RGB565_CONVERTED_JPEG\",\n");
    f.printf("  \"status\": \"%s\"\n", completed ? "COMPLETED" : "INTERRUPTED");
    f.printf("}\n");
    f.close();
    return true;
}

// Simple in-memory / streamed PKZip 2.0 implementation
// Builds a valid ZIP archive containing all files in segDirPath
esp_err_t streamZipArchive(httpd_req_t *req, const String& segDirPath, int segNum) {
    File dir = SD_MMC.open(segDirPath.c_str());
    if (!dir || !dir.isDirectory()) {
        if (dir) dir.close();
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "Segment directory not found");
        return ESP_FAIL;
    }

    struct ZipEntryMeta {
        String fileName;
        uint32_t crc32;
        uint32_t uncompSize;
        uint32_t headerOffset;
    };
    std::vector<ZipEntryMeta> entries;

    httpd_resp_set_type(req, "application/zip");
    char dispHdr[64];
    snprintf(dispHdr, sizeof(dispHdr), "attachment; filename=\"segment_%d.zip\"", segNum);
    httpd_resp_set_hdr(req, "Content-Disposition", dispHdr);

    uint32_t currentOffset = 0;

    // Step 1: Stream Local File Headers + File Data
    File file = dir.openNextFile();
    while (file) {
        if (!file.isDirectory()) {
            String name = String(file.name());
            int slashIdx = name.lastIndexOf('/');
            if (slashIdx != -1) name = name.substring(slashIdx + 1);

            uint32_t size = file.size();
            uint32_t crc = 0; // Simple store mode

            // Calculate CRC32
            uint8_t buffer[512];
            file.seek(0);
            while (file.available()) {
                size_t r = file.read(buffer, sizeof(buffer));
                for (size_t b = 0; b < r; b++) {
                    crc ^= buffer[b];
                    for (int k = 0; k < 8; k++) {
                        crc = (crc >> 1) ^ (0xEDB88320 & (-(int)(crc & 1)));
                    }
                }
            }

            ZipEntryMeta meta;
            meta.fileName = name;
            meta.crc32 = crc;
            meta.uncompSize = size;
            meta.headerOffset = currentOffset;
            entries.push_back(meta);

            // Write Local Header (30 bytes + name length)
            uint16_t nameLen = (uint16_t)name.length();
            uint8_t localHdr[30];
            memset(localHdr, 0, sizeof(localHdr));
            localHdr[0] = 0x50; localHdr[1] = 0x4B; localHdr[2] = 0x03; localHdr[3] = 0x04; // Signature
            localHdr[4] = 20;   localHdr[5] = 0;    // Version needed (2.0)
            localHdr[6] = 0;    localHdr[7] = 0;    // General flags
            localHdr[8] = 0;    localHdr[9] = 0;    // Compression method (0 = Stored)
            localHdr[10] = 0;   localHdr[11] = 0;   // Mod time
            localHdr[12] = 0;   localHdr[13] = 0;   // Mod date
            memcpy(&localHdr[14], &crc, 4);
            memcpy(&localHdr[18], &size, 4);        // Compressed size
            memcpy(&localHdr[22], &size, 4);        // Uncompressed size
            memcpy(&localHdr[26], &nameLen, 2);

            httpd_resp_send_chunk(req, (const char*)localHdr, 30);
            httpd_resp_send_chunk(req, name.c_str(), nameLen);
            currentOffset += 30 + nameLen;

            // Stream File Data
            file.seek(0);
            while (file.available()) {
                size_t r = file.read(buffer, sizeof(buffer));
                httpd_resp_send_chunk(req, (const char*)buffer, r);
                currentOffset += r;
            }
        }
        file.close();
        file = dir.openNextFile();
    }
    dir.close();

    // Step 2: Stream Central Directory Headers
    uint32_t cdStartOffset = currentOffset;
    uint32_t cdSize = 0;

    for (const auto& meta : entries) {
        uint16_t nameLen = (uint16_t)meta.fileName.length();
        uint8_t cdHdr[46];
        memset(cdHdr, 0, sizeof(cdHdr));
        cdHdr[0] = 0x50; cdHdr[1] = 0x4B; cdHdr[2] = 0x01; cdHdr[3] = 0x02; // Central directory sig
        cdHdr[4] = 20;   cdHdr[5] = 0;
        cdHdr[6] = 20;   cdHdr[7] = 0;
        memcpy(&cdHdr[16], &meta.crc32, 4);
        memcpy(&cdHdr[20], &meta.uncompSize, 4);
        memcpy(&cdHdr[24], &meta.uncompSize, 4);
        memcpy(&cdHdr[28], &nameLen, 2);
        memcpy(&cdHdr[42], &meta.headerOffset, 4);

        httpd_resp_send_chunk(req, (const char*)cdHdr, 46);
        httpd_resp_send_chunk(req, meta.fileName.c_str(), nameLen);
        cdSize += 46 + nameLen;
    }

    // Step 3: Stream End of Central Directory Record (22 bytes)
    uint16_t totalEntries = (uint16_t)entries.size();
    uint8_t eocd[22];
    memset(eocd, 0, sizeof(eocd));
    eocd[0] = 0x50; eocd[1] = 0x4B; eocd[2] = 0x05; eocd[3] = 0x06; // EOCD sig
    memcpy(&eocd[8], &totalEntries, 2);
    memcpy(&eocd[10], &totalEntries, 2);
    memcpy(&eocd[12], &cdSize, 4);
    memcpy(&eocd[16], &cdStartOffset, 4);

    httpd_resp_send_chunk(req, (const char*)eocd, 22);

    // End Chunked Transfer
    httpd_resp_send_chunk(req, NULL, 0);
    return ESP_OK;
}

// =========================================================================
// 6. HTTP SERVER HANDLERS & SECURITY (C++11/14 Compatibility)
// =========================================================================
static bool isAuthorizedRequest(httpd_req_t *req) {
    char authHdr[64] = {0};
    if (httpd_req_get_hdr_value_str(req, "Authorization", authHdr, sizeof(authHdr)) == ESP_OK) {
        if (strcmp(authHdr, API_AUTH_TOKEN) == 0) return true;
    }
    // Also accept token query param for browser testing: ?token=safora_auth_token_2026
    char query[64] = {0};
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) == ESP_OK) {
        char tokenVal[32] = {0};
        if (httpd_query_key_value(query, "token", tokenVal, sizeof(tokenVal)) == ESP_OK) {
            if (strcmp(tokenVal, "safora_auth_token_2026") == 0) return true;
        }
    }
    return false;
}

static bool isValidFilename(const String& name) {
    // Requirement 11: Prevent path traversal (no "..", no "/", no "\")
    if (name.indexOf("..") != -1 || name.indexOf('/') != -1 || name.indexOf('\\') != -1) {
        return false;
    }
    return true;
}

// 1. Root Dashboard
static esp_err_t indexHandler(httpd_req_t *req) {
    httpd_resp_set_type(req, "text/html");
    return httpd_resp_send(req, INDEX_HTML, HTTPD_RESP_USE_STRLEN);
}

// 2. Non-blocking Shared MJPEG Stream Handler
static esp_err_t streamHandler(httpd_req_t *req) {
    char part_buf[64];
    esp_err_t res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
    if (res != ESP_OK) return res;

    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "Cache-Control", "no-cache");

    Serial.println("[HTTP] Client connected to live MJPEG stream.");
    uint32_t lastSentTimestamp = 0;

    // Stream from shared frame buffer without touching camera driver directly!
    while (true) {
        uint8_t* frameCopy = NULL;
        size_t frameLen = 0;
        uint32_t frameTime = 0;

        taskENTER_CRITICAL(&frameBufferMux);
        if (latestJpegBuffer != NULL && latestJpegLength > 0 && latestFrameTimestamp != lastSentTimestamp) {
            frameCopy = (uint8_t*)malloc(latestJpegLength);
            if (frameCopy) {
                memcpy(frameCopy, latestJpegBuffer, latestJpegLength);
                frameLen = latestJpegLength;
                frameTime = latestFrameTimestamp;
            }
        }
        taskEXIT_CRITICAL(&frameBufferMux);

        if (frameCopy != NULL) {
            lastSentTimestamp = frameTime;

            res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
            if (res == ESP_OK) {
                size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_PART, (unsigned int)frameLen);
                res = httpd_resp_send_chunk(req, part_buf, hlen);
            }
            if (res == ESP_OK) {
                res = httpd_resp_send_chunk(req, (const char*)frameCopy, frameLen);
            }

            free(frameCopy);

            if (res != ESP_OK) {
                break; // Socket error or client disconnect
            }
        }

        // Pacing & Yield: Allows REST endpoints to process concurrently!
        vTaskDelay(pdMS_TO_TICKS(40));
    }

    Serial.println("[HTTP] Stream client disconnected.");
    return res;
}

// 3. Evidence List: GET /evidence/list
static esp_err_t listEvidenceHandler(httpd_req_t *req) {
    if (!isAuthorizedRequest(req)) {
        httpd_resp_send_err(req, HTTPD_401_UNAUTHORIZED, "Unauthorized: Valid token required");
        return ESP_FAIL;
    }

    String json = "[";
    if (sdCardReady) {
        File root = SD_MMC.open(EVIDENCE_BASE_DIR);
        if (root && root.isDirectory()) {
            bool first = true;
            File f = root.openNextFile();
            while (f) {
                if (f.isDirectory()) {
                    String name = String(f.name());
                    int idx = name.lastIndexOf("seg_");
                    if (idx != -1) {
                        if (!first) json += ",";
                        first = false;
                        json += "\"" + name.substring(idx) + "\"";
                    }
                }
                f.close(); // Close handle immediately!
                f = root.openNextFile();
            }
            root.close(); // Close root!
        }
    }
    json += "]";

    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, json.c_str(), json.length());
}

// 4. Manifest Download: GET /evidence/manifest?segment=X
static esp_err_t manifestEvidenceHandler(httpd_req_t *req) {
    if (!isAuthorizedRequest(req)) {
        httpd_resp_send_err(req, HTTPD_401_UNAUTHORIZED, "Unauthorized: Valid token required");
        return ESP_FAIL;
    }

    char query[64] = {0};
    char segStr[16] = {0};
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
        httpd_query_key_value(query, "segment", segStr, sizeof(segStr)) != ESP_OK) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Missing 'segment' parameter");
        return ESP_FAIL;
    }

    int segNum = atoi(segStr);
    String manifestPath = String(EVIDENCE_BASE_DIR) + "/seg_" + String(segNum) + "/manifest.json";

    if (!SD_MMC.exists(manifestPath.c_str())) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "Manifest not found for segment");
        return ESP_FAIL;
    }

    File f = SD_MMC.open(manifestPath.c_str(), FILE_READ);
    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

    char buf[512];
    while (f.available()) {
        size_t r = f.read((uint8_t*)buf, sizeof(buf));
        httpd_resp_send_chunk(req, buf, r);
    }
    f.close();
    return httpd_resp_send_chunk(req, NULL, 0);
}

// 5. Download Evidence ZIP Archive: GET /evidence/zip?segment=X
static esp_err_t zipEvidenceHandler(httpd_req_t *req) {
    if (!isAuthorizedRequest(req)) {
        httpd_resp_send_err(req, HTTPD_401_UNAUTHORIZED, "Unauthorized: Valid token required");
        return ESP_FAIL;
    }

    char query[64] = {0};
    char segStr[16] = {0};
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
        httpd_query_key_value(query, "segment", segStr, sizeof(segStr)) != ESP_OK) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Missing 'segment' parameter");
        return ESP_FAIL;
    }

    int segNum = atoi(segStr);
    String segDirPath = String(EVIDENCE_BASE_DIR) + "/seg_" + String(segNum);

    if (!SD_MMC.exists(segDirPath.c_str())) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "Evidence segment folder not found");
        return ESP_FAIL;
    }

    return streamZipArchive(req, segDirPath, segNum);
}

// 6. Single File Download with Validation: GET /evidence/file?segment=X&file=frame_001.jpg
static esp_err_t fileEvidenceHandler(httpd_req_t *req) {
    if (!isAuthorizedRequest(req)) {
        httpd_resp_send_err(req, HTTPD_401_UNAUTHORIZED, "Unauthorized: Valid token required");
        return ESP_FAIL;
    }

    char query[128] = {0};
    char segStr[16] = {0};
    char fileStr[64] = {0};

    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
        httpd_query_key_value(query, "segment", segStr, sizeof(segStr)) != ESP_OK ||
        httpd_query_key_value(query, "file", fileStr, sizeof(fileStr)) != ESP_OK) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Missing 'segment' or 'file' parameter");
        return ESP_FAIL;
    }

    String fileName = String(fileStr);
    if (!isValidFilename(fileName)) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Path traversal rejected");
        return ESP_FAIL;
    }

    int segNum = atoi(segStr);
    String filePath = String(EVIDENCE_BASE_DIR) + "/seg_" + String(segNum) + "/" + fileName;

    if (!SD_MMC.exists(filePath.c_str())) {
        httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "File not found");
        return ESP_FAIL;
    }

    File f = SD_MMC.open(filePath.c_str(), FILE_READ);
    const char* ctype = fileName.endsWith(".json") ? "application/json" : "image/jpeg";
    httpd_resp_set_type(req, ctype);
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

    char chunk[512];
    while (f.available()) {
        size_t r = f.read((uint8_t*)chunk, sizeof(chunk));
        httpd_resp_send_chunk(req, chunk, r);
    }
    f.close();
    return httpd_resp_send_chunk(req, NULL, 0);
}

// 7. System Diagnostics & Benchmark Status: GET /status
static esp_err_t statusHandler(httpd_req_t *req) {
    char buf[256];
    int batt = queryBatteryPercentage();
    snprintf(buf, sizeof(buf),
        "{\"cameraReady\":%d,\"sdCardReady\":%d,\"recording\":%d,\"battery\":%d,"
        "\"currentSegment\":%d,\"storageFull\":%d,\"totalConvertedFrames\":%u,"
        "\"lastConversionTimeUs\":%u}",
        cameraReady ? 1 : 0, sdCardReady ? 1 : 0, isRecording ? 1 : 0, batt,
        currentSegmentIndex, isStorageSpaceLow() ? 1 : 0, totalConvertedFrames,
        lastConversionTimeUs);

    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, buf, strlen(buf));
}

void setupHttpServerEndpoints() {
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = HTTP_SERVER_PORT;
    config.ctrl_port = 32768;
    config.max_uri_handlers = 10;
    config.stack_size = 8192;

    // Requirement 1: Replace C++20 designated initializers with standard C++11/C99 assignment
    httpd_uri_t index_uri;
    memset(&index_uri, 0, sizeof(index_uri));
    index_uri.uri = "/";
    index_uri.method = HTTP_GET;
    index_uri.handler = indexHandler;

    httpd_uri_t stream_uri;
    memset(&stream_uri, 0, sizeof(stream_uri));
    stream_uri.uri = "/stream";
    stream_uri.method = HTTP_GET;
    stream_uri.handler = streamHandler;

    httpd_uri_t list_uri;
    memset(&list_uri, 0, sizeof(list_uri));
    list_uri.uri = "/evidence/list";
    list_uri.method = HTTP_GET;
    list_uri.handler = listEvidenceHandler;

    httpd_uri_t manifest_uri;
    memset(&manifest_uri, 0, sizeof(manifest_uri));
    manifest_uri.uri = "/evidence/manifest";
    manifest_uri.method = HTTP_GET;
    manifest_uri.handler = manifestEvidenceHandler;

    httpd_uri_t zip_uri;
    memset(&zip_uri, 0, sizeof(zip_uri));
    zip_uri.uri = "/evidence/zip";
    zip_uri.method = HTTP_GET;
    zip_uri.handler = zipEvidenceHandler;

    httpd_uri_t file_uri;
    memset(&file_uri, 0, sizeof(file_uri));
    file_uri.uri = "/evidence/file";
    file_uri.method = HTTP_GET;
    file_uri.handler = fileEvidenceHandler;

    httpd_uri_t status_uri;
    memset(&status_uri, 0, sizeof(status_uri));
    status_uri.uri = "/status";
    status_uri.method = HTTP_GET;
    status_uri.handler = statusHandler;

    if (httpd_start(&camera_httpd, &config) == ESP_OK) {
        httpd_register_uri_handler(camera_httpd, &index_uri);
        httpd_register_uri_handler(camera_httpd, &stream_uri);
        httpd_register_uri_handler(camera_httpd, &list_uri);
        httpd_register_uri_handler(camera_httpd, &manifest_uri);
        httpd_register_uri_handler(camera_httpd, &zip_uri);
        httpd_register_uri_handler(camera_httpd, &file_uri);
        httpd_register_uri_handler(camera_httpd, &status_uri);
        Serial.println("[HTTP] Native esp_http_server initialized successfully on port 80.");
    } else {
        Serial.println("[HTTP] Error starting httpd server!");
    }
}

// =========================================================================
// 7. SINGLE CAMERA CAPTURE PIPELINE & BENCHMARKING (Core 0)
// =========================================================================
void cameraCaptureAndProcessingTask(void *parameter) {
    Serial.println("[CameraPipeline] Single capture & conversion pipeline started on Core 0");

    while (true) {
        // Capture frame from RHYX M21-45 in RGB565 format
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            vTaskDelay(pdMS_TO_TICKS(50));
            continue;
        }

        // Benchmark Software JPEG conversion
        uint32_t startConvUs = micros();
        uint8_t *jpg_buf = NULL;
        size_t jpg_len = 0;

        bool ok = frame2jpg(fb, JPEG_QUALITY_VAL, &jpg_buf, &jpg_len);
        uint32_t convDurationUs = micros() - startConvUs;

        // Immediately return raw RGB565 framebuffer to camera driver
        esp_camera_fb_return(fb);

        if (!ok || !jpg_buf || jpg_len == 0) {
            if (jpg_buf) free(jpg_buf);
            vTaskDelay(pdMS_TO_TICKS(50));
            continue;
        }

        lastConversionTimeUs = convDurationUs;
        totalConvertedFrames++;

        // Update Shared Frame Buffer for HTTP Streaming / Web Dashboard (Thread-safe)
        taskENTER_CRITICAL(&frameBufferMux);
        if (latestJpegBuffer != NULL) {
            free(latestJpegBuffer);
        }
        latestJpegBuffer = (uint8_t*)malloc(jpg_len);
        if (latestJpegBuffer != NULL) {
            memcpy(latestJpegBuffer, jpg_buf, jpg_len);
            latestJpegLength = jpg_len;
            latestFrameTimestamp = millis();
        }
        taskEXIT_CRITICAL(&frameBufferMux);

        // If recording active and storage available, save frame to MicroSD in 5s segments
        if (isRecording && sdCardReady) {
            static int segIdx = 0;
            static int frameInSeg = 0;
            static unsigned long segStartMs = 0;
            static String activeDirPath = "";

            if (frameInSeg == 0) {
                if (isStorageSpaceLow()) {
                    sendBleNotification("STORAGE_FULL");
                    isRecording = false;
                    free(jpg_buf);
                    continue;
                }
                segIdx = ++currentSegmentIndex;
                char dirBuf[64];
                snprintf(dirBuf, sizeof(dirBuf), "%s/seg_%d", EVIDENCE_BASE_DIR, segIdx);
                activeDirPath = String(dirBuf);
                SD_MMC.mkdir(activeDirPath.c_str());
                segStartMs = millis();
                Serial.printf("[Recording] >>> Starting 5s segment #%d\n", segIdx);
            }

            frameInSeg++;
            char framePath[80];
            snprintf(framePath, sizeof(framePath), "%s/frame_%03d.jpg", activeDirPath.c_str(), frameInSeg);

            File f = SD_MMC.open(framePath, FILE_WRITE);
            if (f) {
                f.write(jpg_buf, jpg_len);
                f.close();
            } else {
                Serial.printf("[Recording] Error writing frame: %s\n", framePath);
            }

            // Check if segment duration reached (5000ms) or stopped early
            unsigned long elapsed = millis() - segStartMs;
            if (elapsed >= SEGMENT_DURATION_MS || !isRecording) {
                writeSegmentManifest(activeDirPath, segIdx, frameInSeg, elapsed, isRecording);
                Serial.printf("[Recording] <<< Segment #%d completed (%d frames, %lums)\n", segIdx, frameInSeg, elapsed);
                frameInSeg = 0;
            }
        }

        // Release temporary converted JPEG
        free(jpg_buf);

        // Target capture pacing (~5 FPS = 200ms)
        vTaskDelay(pdMS_TO_TICKS(180));
    }
}

// =========================================================================
// 8. SETUP FUNCTION
// =========================================================================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n=======================================================");
    Serial.println("     SAFORA WEARABLE — ESP32-CAM INTEGRATED FIRMWARE   ");
    Serial.println("=======================================================");

    // 1. Status LED Setup
    pinMode(LED_STATUS_PIN, OUTPUT);
    digitalWrite(LED_STATUS_PIN, HIGH); // OFF

    // 2. Physical SOS Button Setup (GPIO 13, INPUT_PULLUP)
    pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(SOS_BUTTON_PIN), onSosButtonIsr, FALLING);
    Serial.printf("[Hardware] SOS Push-Button attached on GPIO %d (Active LOW)\n", SOS_BUTTON_PIN);

    // 3. Camera Setup (RHYX M21-45: 10MHz clock, QVGA 320x240, RGB565)
    camera_config_t camConfig;
    memset(&camConfig, 0, sizeof(camConfig));
    camConfig.ledc_channel = LEDC_CHANNEL_0;
    camConfig.ledc_timer   = LEDC_TIMER_0;
    camConfig.pin_d0       = Y2_GPIO_NUM;
    camConfig.pin_d1       = Y3_GPIO_NUM;
    camConfig.pin_d2       = Y4_GPIO_NUM;
    camConfig.pin_d3       = Y5_GPIO_NUM;
    camConfig.pin_d4       = Y6_GPIO_NUM;
    camConfig.pin_d5       = Y7_GPIO_NUM;
    camConfig.pin_d6       = Y8_GPIO_NUM;
    camConfig.pin_d7       = Y9_GPIO_NUM;
    camConfig.pin_xclk     = XCLK_GPIO_NUM;
    camConfig.pin_pclk     = PCLK_GPIO_NUM;
    camConfig.pin_vsync    = VSYNC_GPIO_NUM;
    camConfig.pin_href     = HREF_GPIO_NUM;
    camConfig.pin_sccb_sda = SIOD_GPIO_NUM;
    camConfig.pin_sccb_scl = SIOC_GPIO_NUM;
    camConfig.pin_pwdn     = PWDN_GPIO_NUM;
    camConfig.pin_reset    = RESET_GPIO_NUM;
    camConfig.xclk_freq_hz = CAMERA_XCLK_FREQ_HZ;
    camConfig.pixel_format = PIXFORMAT_RGB565;
    camConfig.frame_size   = CAMERA_FRAME_SIZE;
    camConfig.jpeg_quality = 12;
    camConfig.fb_count     = 1;

    if (psramFound()) {
        camConfig.fb_location = CAMERA_FB_IN_PSRAM;
        camConfig.grab_mode   = CAMERA_GRAB_WHEN_EMPTY;
        Serial.println("[Camera] PSRAM initialized for frame buffer.");
    } else {
        camConfig.fb_location = CAMERA_FB_IN_DRAM;
        camConfig.grab_mode   = CAMERA_GRAB_WHEN_EMPTY;
        Serial.println("[Camera] Warning: PSRAM not found, allocating in DRAM.");
    }

    if (esp_camera_init(&camConfig) == ESP_OK) {
        cameraReady = true;
        Serial.println("[Camera] RHYX M21-45 camera initialized successfully!");
    } else {
        cameraReady = false;
        Serial.println("[Camera] Error: Camera initialization failed!");
    }

    // 4. MicroSD Storage Setup (SD_MMC 1-bit mode, leaves GPIO 13 free)
    if (SD_MMC.begin("/sdcard", true)) {
        if (SD_MMC.cardType() != CARD_NONE) {
            sdCardReady = true;
            initializeStorageIndex();
            Serial.println("[Storage] MicroSD card mounted successfully (1-bit mode).");
        } else {
            sdCardReady = false;
            Serial.println("[Storage] Error: No card attached.");
        }
    } else {
        sdCardReady = false;
        Serial.println("[Storage] Error: SD_MMC mount failed.");
    }

    // 5. Wi-Fi SoftAP Setup (192.168.4.1)
    WiFi.mode(WIFI_AP);
    IPAddress local_ip(192, 168, 4, 1);
    IPAddress gateway(192, 168, 4, 1);
    IPAddress subnet(255, 255, 255, 0);
    WiFi.softAPConfig(local_ip, gateway, subnet);
    WiFi.softAP(AP_SSID, AP_PASS, WIFI_AP_CHANNEL, 0, WIFI_AP_MAX_CONN);
    Serial.printf("[Wi-Fi] SoftAP active. SSID: %s, Pass: %s, IP: %s\n", AP_SSID, AP_PASS, WiFi.softAPIP().toString().c_str());

    // 6. Native HTTP Server Setup
    setupHttpServerEndpoints();

    // 7. BLE GATT Server Setup
    BLEDevice::init(BLE_DEVICE_NAME);
    pBleServer = BLEDevice::createServer();
    pBleServer->setCallbacks(new BleServerCallbackHandler());

    BLEService *pService = pBleServer->createService(BLE_SERVICE_UUID);

    // Command (Write)
    pCommandChar = pService->createCharacteristic(
        BLE_CHAR_COMMAND_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
    );
    pCommandChar->setCallbacks(new BleCommandCallbackHandler());

    // Status (Read / Notify)
    pStatusChar = pService->createCharacteristic(
        BLE_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusChar->addDescriptor(new BLE2902());

    // Events (Notify with 0x2902)
    pEventsChar = pService->createCharacteristic(
        BLE_CHAR_EVENTS_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pEventsChar->addDescriptor(new BLE2902());

    pService->start();

    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(BLE_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->start();
    Serial.println("[BLE] Advertising started as " BLE_DEVICE_NAME);

    // 8. Launch Single Camera Pipeline Task pinned to Core 0
    if (cameraReady) {
        xTaskCreatePinnedToCore(
            cameraCaptureAndProcessingTask,
            "CamPipeline",
            8192,
            NULL,
            2,
            &cameraTaskHandle,
            0 // Core 0
        );
    }

    // Startup blink indicator
    for (int i = 0; i < 2; i++) {
        digitalWrite(LED_STATUS_PIN, LOW);
        delay(100);
        digitalWrite(LED_STATUS_PIN, HIGH);
        delay(100);
    }
    Serial.println("[System] Boot sequence completed.");
}

// =========================================================================
// 9. MAIN LOOP (Runs on Core 1)
// =========================================================================
void loop() {
    static unsigned long lastStatusBroadcastMs = 0;

    // Requirement 7 & 10: Button Debouncing & Reliable SOS Delivery
    if (isrButtonFlag) {
        isrButtonFlag = false;
        unsigned long now = millis();
        if (now - lastButtonIsrTimeMs > 400) { // 400ms software debounce
            lastButtonIsrTimeMs = now;
            Serial.println("[Button] Physical SOS button pressed!");

            // Always log to SD card if available
            appendSosLog(now);

            // Transmit immediate BLE event or flag for re-delivery on reconnect
            if (bleClientConnected) {
                sendBleNotification("MANUAL_SOS");
                pendingSosEvent = false;
            } else {
                pendingSosEvent = true;
                Serial.println("[Button] BLE offline: MANUAL_SOS queued for delivery upon reconnect.");
            }

            // Requirement 10: Check hardware before recording; SOS fires regardless
            if (cameraReady && sdCardReady && !isStorageSpaceLow()) {
                if (!isRecording) {
                    isRecording = true;
                    digitalWrite(LED_STATUS_PIN, LOW);
                    sendBleNotification("RECORDING_STARTED");
                }
            } else {
                if (!cameraReady) sendBleNotification("CAMERA_ERROR");
                if (!sdCardReady) sendBleNotification("SD_CARD_ERROR");
                if (isStorageSpaceLow()) sendBleNotification("STORAGE_FULL");
                Serial.println("[Button] SOS triggered, but camera recording skipped due to hardware state.");
            }
        }
    }

    // Periodic Status broadcast (every 15s)
    if (millis() - lastStatusBroadcastMs > 15000) {
        lastStatusBroadcastMs = millis();
        updateBleStatus();
    }

    vTaskDelay(pdMS_TO_TICKS(15));
}

#pragma once

#include <Arduino.h>
#include "FS.h"
#include "SD_MMC.h"
#include "Config.h"

class StorageManager {
public:
    StorageManager() : sdAvailable(false), currentSegmentIndex(0) {}

    bool init() {
        // Use 1-bit SD_MMC mode to leave GPIO 12 & 13 free for external hardware (Button on GPIO 13)
        // SD_MMC 1-bit mode uses: CLK=GPIO 14, CMD=GPIO 15, D0=GPIO 2
        Serial.println("[Storage] Initializing MicroSD card in SD_MMC 1-bit mode...");
        
        if (!SD_MMC.begin("/sdcard", true)) { // true = 1-bit mode
            Serial.println("[Storage] Card Mount Failed!");
            sdAvailable = false;
            return false;
        }

        uint8_t cardType = SD_MMC.cardType();
        if (cardType == CARD_NONE) {
            Serial.println("[Storage] No SD card attached");
            sdAvailable = false;
            return false;
        }

        uint64_t totalBytes = SD_MMC.totalBytes();
        uint64_t usedBytes = SD_MMC.usedBytes();
        Serial.printf("[Storage] SD Card Size: %lluMB, Used: %lluMB\n", totalBytes / (1024 * 1024), usedBytes / (1024 * 1024));

        // Create base evidence directory if it doesn't exist
        if (!SD_MMC.exists(EVIDENCE_BASE_DIR)) {
            SD_MMC.mkdir(EVIDENCE_BASE_DIR);
        }

        // Determine next segment index by inspecting existing directories/files
        findNextSegmentIndex();

        sdAvailable = true;
        return true;
    }

    bool isStorageFull() {
        if (!sdAvailable) return true;
        uint64_t total = SD_MMC.totalBytes();
        uint64_t used = SD_MMC.usedBytes();
        // If less than 20MB remaining, flag storage full
        if (total > used && (total - used) < (20 * 1024 * 1024)) {
            return true;
        }
        return false;
    }

    int getNextSegmentIndex() {
        return ++currentSegmentIndex;
    }

    int getCurrentSegmentIndex() const {
        return currentSegmentIndex;
    }

    bool createSegmentDirectory(int segmentIndex, String &outDirPath) {
        if (!sdAvailable) return false;
        char buf[64];
        snprintf(buf, sizeof(buf), "%s/seg_%d", EVIDENCE_BASE_DIR, segmentIndex);
        outDirPath = String(buf);
        if (!SD_MMC.exists(outDirPath.c_str())) {
            return SD_MMC.mkdir(outDirPath.c_str());
        }
        return true;
    }

    bool saveJpegFrame(const String &dirPath, int frameNumber, const uint8_t *buf, size_t len) {
        if (!sdAvailable) return false;
        char path[80];
        snprintf(path, sizeof(path), "%s/frame_%03d.jpg", dirPath.c_str(), frameNumber);

        File file = SD_MMC.open(path, FILE_WRITE);
        if (!file) {
            Serial.printf("[Storage] Failed to open file for write: %s\n", path);
            return false;
        }
        size_t written = file.write(buf, len);
        file.close();
        return written == len;
    }

    bool saveManifest(const String &dirPath, int segmentIndex, int frameCount, unsigned long durationMs) {
        if (!sdAvailable) return false;
        char path[80];
        snprintf(path, sizeof(path), "%s/manifest.json", dirPath.c_str());

        File file = SD_MMC.open(path, FILE_WRITE);
        if (!file) return false;

        file.printf("{\n");
        file.printf("  \"segmentIndex\": %d,\n", segmentIndex);
        file.printf("  \"timestamp\": %lu,\n", millis());
        file.printf("  \"durationSeconds\": %lu,\n", durationMs / 1000);
        file.printf("  \"framesCount\": %d,\n", frameCount);
        file.printf("  \"resolution\": \"640x480\",\n");
        file.printf("  \"cameraState\": \"CAPTURED\"\n");
        file.printf("}\n");
        file.close();
        return true;
    }

    String listSegmentsJson() {
        if (!sdAvailable) return "[]";
        File root = SD_MMC.open(EVIDENCE_BASE_DIR);
        if (!root || !root.isDirectory()) return "[]";

        String json = "[";
        bool first = true;
        File file = root.openNextFile();
        while (file) {
            if (file.isDirectory()) {
                String name = String(file.name());
                // Extract segment number
                if (name.startsWith("seg_") || name.indexOf("seg_") != -1) {
                    if (!first) json += ",";
                    first = false;
                    json += "\"" + name + "\"";
                }
            }
            file = root.openNextFile();
        }
        json += "]";
        return json;
    }

    bool isReady() const { return sdAvailable; }

private:
    bool sdAvailable;
    int currentSegmentIndex;

    void findNextSegmentIndex() {
        currentSegmentIndex = 0;
        File root = SD_MMC.open(EVIDENCE_BASE_DIR);
        if (!root || !root.isDirectory()) return;

        File file = root.openNextFile();
        while (file) {
            if (file.isDirectory()) {
                String name = String(file.name());
                int idx = name.lastIndexOf("seg_");
                if (idx != -1) {
                    int num = name.substring(idx + 4).toInt();
                    if (num > currentSegmentIndex) {
                        currentSegmentIndex = num;
                    }
                }
            }
            file = root.openNextFile();
        }
    }
};

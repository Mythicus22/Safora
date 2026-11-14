#pragma once

#include <Arduino.h>
#include "esp_http_server.h"
#include "FS.h"
#include "SD_MMC.h"
#include "Config.h"
#include "CameraManager.h"
#include "StorageManager.h"

// Forward declaration of CameraManager instance
extern CameraManager camera;

// =========================================================================
// Web Dashboard HTML (Responsive with Live Stream & Evidence Downloader)
// =========================================================================
static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SAFORA ESP32-CAM Wearable</title>
    <style>
        body { background: #0f141c; color: #f8fafc; font-family: -apple-system, Arial, sans-serif; text-align: center; margin: 0; padding: 16px; }
        h2 { color: #e53935; margin-bottom: 8px; letter-spacing: 2px; }
        .subtitle { color: #94a3b8; font-size: 13px; margin-bottom: 18px; }
        .card { background: #181f2c; border: 1px solid #2d374d; border-radius: 12px; padding: 14px; margin-bottom: 16px; max-width: 600px; margin-left: auto; margin-right: auto; }
        img { width: 100%; max-width: 480px; height: auto; border: 2px solid #2563eb; border-radius: 8px; background: #000; }
        .status { color: #10b981; font-weight: bold; margin-top: 10px; font-size: 13px; }
        .btn { background: #2563eb; color: #fff; border: none; padding: 10px 16px; border-radius: 6px; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; margin: 4px; }
        .btn:hover { background: #1d4ed8; }
        .btn-danger { background: #e53935; }
        ul { list-style: none; padding: 0; text-align: left; }
        li { background: #222b3d; margin: 6px 0; padding: 8px 12px; border-radius: 6px; display: flex; justify-content: space-between; align-items: center; }
    </style>
</head>
<body>
    <h2>SAFORA WEARABLE</h2>
    <div class="subtitle">AI-Thinker ESP32-CAM &bull; RHYX M21-45 &bull; SoftAP 192.168.4.1</div>

    <div class="card">
        <h3>Live Camera Stream</h3>
        <img src="/stream" id="camera">
        <div class="status">&bull; MJPEG Live Stream Active</div>
    </div>

    <div class="card">
        <h3>Recorded 5s Evidence Segments</h3>
        <div id="segmentList">Loading evidence list...</div>
        <button class="btn" onclick="loadSegments()">Refresh Evidence</button>
    </div>

    <script>
        function loadSegments() {
            fetch('/evidence/list')
                .then(r => r.json())
                .then(data => {
                    let html = '<ul>';
                    if (data.length === 0) {
                        html += '<li>No evidence segments recorded yet.</li>';
                    } else {
                        data.forEach(seg => {
                            html += `<li><span>Segment: <b>${seg}</b></span> <a class="btn" href="/evidence?segment=${seg.replace('seg_','')}" target="_blank">Download Manifest</a></li>`;
                        });
                    }
                    html += '</ul>';
                    document.getElementById('segmentList').innerHTML = html;
                })
                .catch(e => {
                    document.getElementById('segmentList').innerHTML = 'Error loading segments: ' + e;
                });
        }
        loadSegments();
    </script>
</body>
</html>
)rawliteral";

// Stream Header constants
static const char* STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=frame";
static const char* STREAM_BOUNDARY = "\r\n--frame\r\n";
static const char* STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

class WebServerManager {
public:
    WebServerManager(StorageManager &storage) : httpdServer(NULL), storageRef(storage) {}

    void init() {
        Serial.println("[Wi-Fi] Setting up SoftAP network: " WIFI_AP_SSID);

        IPAddress local_ip(192, 168, 4, 1);
        IPAddress gateway(192, 168, 4, 1);
        IPAddress subnet(255, 255, 255, 0);

        WiFi.mode(WIFI_AP);
        WiFi.softAPConfig(local_ip, gateway, subnet);
        WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASS, WIFI_AP_CHANNEL, 0, WIFI_AP_MAX_CONN);

        Serial.printf("[Wi-Fi] SoftAP active. Network: %s, Pass: %s, IP: %s\n",
                      WIFI_AP_SSID, WIFI_AP_PASS, WiFi.softAPIP().toString().c_str());

        startHttpServer();
    }

private:
    httpd_handle_t httpdServer;
    StorageManager &storageRef;

    void startHttpServer() {
        httpd_config_t config = HTTPD_DEFAULT_CONFIG();
        config.server_port = HTTP_SERVER_PORT;
        config.ctrl_port = 32768;
        config.max_uri_handlers = 8;
        config.stack_size = 8192;

        // 1. Root Webpage / Dashboard Handler
        httpd_uri_t index_uri = {
            .uri = "/",
            .method = HTTP_GET,
            .handler = indexHandler,
            .user_ctx = this
        };

        // 2. Live MJPEG Stream Handler (Working implementation from old_esp_code.ino)
        httpd_uri_t stream_uri = {
            .uri = "/stream",
            .method = HTTP_GET,
            .handler = streamHandler,
            .user_ctx = this
        };

        // 3. Evidence list endpoint: GET /evidence/list
        httpd_uri_t list_uri = {
            .uri = "/evidence/list",
            .method = HTTP_GET,
            .handler = listEvidenceHandler,
            .user_ctx = this
        };

        // 4. Evidence download endpoint: GET /evidence?segment=X
        httpd_uri_t evidence_uri = {
            .uri = "/evidence",
            .method = HTTP_GET,
            .handler = downloadEvidenceHandler,
            .user_ctx = this
        };

        // 5. System status endpoint: GET /status
        httpd_uri_t status_uri = {
            .uri = "/status",
            .method = HTTP_GET,
            .handler = statusHandler,
            .user_ctx = this
        };

        if (httpd_start(&httpdServer, &config) == ESP_OK) {
            httpd_register_uri_handler(httpdServer, &index_uri);
            httpd_register_uri_handler(httpdServer, &stream_uri);
            httpd_register_uri_handler(httpdServer, &list_uri);
            httpd_register_uri_handler(httpdServer, &evidence_uri);
            httpd_register_uri_handler(httpdServer, &status_uri);
            Serial.println("[HTTP] Native esp_http_server listening on port 80");
        } else {
            Serial.println("[HTTP] Failed to start httpd server!");
        }
    }

    static esp_err_t indexHandler(httpd_req_t *req) {
        httpd_resp_set_type(req, "text/html");
        return httpd_resp_send(req, INDEX_HTML, HTTPD_RESP_USE_STRLEN);
    }

    static esp_err_t streamHandler(httpd_req_t *req) {
        char part_buf[64];
        esp_err_t res = httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
        if (res != ESP_OK) return res;

        httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
        httpd_resp_set_hdr(req, "Cache-Control", "no-cache");

        Serial.println("[HTTP] Client connected to live MJPEG stream");

        while (true) {
            uint8_t *jpg_buf = NULL;
            size_t jpg_len = 0;

            if (!camera.captureJpegFrame(&jpg_buf, &jpg_len)) {
                vTaskDelay(pdMS_TO_TICKS(100));
                continue;
            }

            res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
            if (res != ESP_OK) {
                camera.releaseJpeg(jpg_buf);
                break;
            }

            size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_PART, (unsigned int)jpg_len);
            res = httpd_resp_send_chunk(req, part_buf, hlen);
            if (res != ESP_OK) {
                camera.releaseJpeg(jpg_buf);
                break;
            }

            res = httpd_resp_send_chunk(req, (const char*)jpg_buf, jpg_len);
            camera.releaseJpeg(jpg_buf);

            if (res != ESP_OK) {
                break;
            }

            // Pacing for stream
            vTaskDelay(pdMS_TO_TICKS(10));
        }

        Serial.println("[HTTP] Stream client disconnected");
        return res;
    }

    static esp_err_t listEvidenceHandler(httpd_req_t *req) {
        WebServerManager *self = (WebServerManager*)req->user_ctx;
        String json = self->storageRef.listSegmentsJson();
        httpd_resp_set_type(req, "application/json");
        httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
        return httpd_resp_send(req, json.c_str(), json.length());
    }

    static esp_err_t downloadEvidenceHandler(httpd_req_t *req) {
        WebServerManager *self = (WebServerManager*)req->user_ctx;
        char query[64] = {0};
        char segmentParam[16] = {0};
        char fileParam[32] = {0};

        if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK) {
            httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Missing query string");
            return ESP_FAIL;
        }

        httpd_query_key_value(query, "segment", segmentParam, sizeof(segmentParam));
        httpd_query_key_value(query, "file", fileParam, sizeof(fileParam));

        int segmentNum = atoi(segmentParam);
        String dirPath = String(EVIDENCE_BASE_DIR) + "/seg_" + String(segmentNum);

        if (!SD_MMC.exists(dirPath.c_str())) {
            httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "Segment not found");
            return ESP_FAIL;
        }

        httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

        // Check if downloading a specific file (e.g. file=frame_001.jpg)
        if (strlen(fileParam) > 0) {
            String filePath = dirPath + "/" + String(fileParam);
            if (!SD_MMC.exists(filePath.c_str())) {
                httpd_resp_send_err(req, HTTPD_404_NOT_FOUND, "File not found in segment");
                return ESP_FAIL;
            }
            File f = SD_MMC.open(filePath.c_str(), FILE_READ);
            const char *type = String(fileParam).endsWith(".json") ? "application/json" : "image/jpeg";
            httpd_resp_set_type(req, type);

            char chunk[1024];
            size_t bytesRead = 0;
            while ((bytesRead = f.read((uint8_t*)chunk, sizeof(chunk))) > 0) {
                if (httpd_resp_send_chunk(req, chunk, bytesRead) != ESP_OK) {
                    f.close();
                    return ESP_FAIL;
                }
            }
            f.close();
            return httpd_resp_send_chunk(req, NULL, 0);
        }

        // Default: Stream segment manifest.json
        String manifestPath = dirPath + "/manifest.json";
        if (SD_MMC.exists(manifestPath.c_str())) {
            File manifest = SD_MMC.open(manifestPath.c_str(), FILE_READ);
            httpd_resp_set_type(req, "application/json");
            char chunk[1024];
            size_t bytesRead = 0;
            while ((bytesRead = manifest.read((uint8_t*)chunk, sizeof(chunk))) > 0) {
                if (httpd_resp_send_chunk(req, chunk, bytesRead) != ESP_OK) {
                    manifest.close();
                    return ESP_FAIL;
                }
            }
            manifest.close();
            return httpd_resp_send_chunk(req, NULL, 0);
        } else {
            String dummyJson = "{\"segment\":" + String(segmentNum) + ",\"status\":\"CAPTURING\"}";
            httpd_resp_set_type(req, "application/json");
            return httpd_resp_send(req, dummyJson.c_str(), dummyJson.length());
        }
    }

    static esp_err_t statusHandler(httpd_req_t *req) {
        WebServerManager *self = (WebServerManager*)req->user_ctx;
        char buf[128];
        snprintf(buf, sizeof(buf), "{\"storageReady\":%d,\"storageFull\":%d,\"currentSegment\":%d}",
            self->storageRef.isReady(), self->storageRef.isStorageFull(), self->storageRef.getCurrentSegmentIndex());
        httpd_resp_set_type(req, "application/json");
        httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
        return httpd_resp_send(req, buf, strlen(buf));
    }
};

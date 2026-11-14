#include <Arduino.h>
#include "esp_camera.h"
#include "img_converters.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include <string.h>
#include <stdlib.h>

// =====================================================
// WIFI ACCESS POINT CONFIGURATION
// =====================================================

const char* AP_SSID = "RHYX_CAM";
const char* AP_PASSWORD = "12345678";

// =====================================================
// AI THINKER ESP32-CAM PIN CONFIGURATION
// =====================================================

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

// =====================================================
// CAMERA AND STREAM CONFIGURATION
// =====================================================

#define FRAME_SIZE       FRAMESIZE_QVGA
#define JPEG_QUALITY     80

static httpd_handle_t camera_httpd = NULL;

static const char* STREAM_CONTENT_TYPE =
    "multipart/x-mixed-replace;boundary=frame";

static const char* STREAM_BOUNDARY =
    "\r\n--frame\r\n";

static const char* STREAM_PART =
    "Content-Type: image/jpeg\r\n"
    "Content-Length: %u\r\n\r\n";

// =====================================================
// WEB INTERFACE
// =====================================================

static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>RHYX ESP32 Camera</title>

    <style>
        body {
            background: #151515;
            color: white;
            font-family: Arial, sans-serif;
            text-align: center;
            margin: 0;
            padding: 20px;
        }

        h2 {
            margin-bottom: 20px;
        }

        img {
            width: 100%;
            max-width: 800px;
            height: auto;
            border: 2px solid #444;
            border-radius: 8px;
        }

        .status {
            color: #aaa;
            margin-top: 15px;
        }
    </style>
</head>

<body>

    <h2>RHYX M21-45 Live Camera</h2>

    <img src="/stream" id="camera">

    <div class="status">
        ESP32-CAM MJPEG Stream
    </div>

</body>
</html>
)rawliteral";

// =====================================================
// MJPEG STREAM HANDLER
// =====================================================

static esp_err_t stream_handler(httpd_req_t *req)
{
    camera_fb_t *fb = NULL;

    uint8_t *jpg_buf = NULL;
    size_t jpg_len = 0;

    char part_buf[64];

    esp_err_t res = httpd_resp_set_type(
        req,
        STREAM_CONTENT_TYPE
    );

    if (res != ESP_OK) {
        return res;
    }

    httpd_resp_set_hdr(
        req,
        "Access-Control-Allow-Origin",
        "*"
    );

    httpd_resp_set_hdr(
        req,
        "Cache-Control",
        "no-cache"
    );

    Serial.println("Client connected to video stream");

    while (true)
    {
        // Capture RGB565 frame
        fb = esp_camera_fb_get();

        if (!fb)
        {
            Serial.println("Camera capture failed");
            res = ESP_FAIL;
            break;
        }

        // Convert RGB565 framebuffer to JPEG
        bool converted = frame2jpg(
            fb,
            JPEG_QUALITY,
            &jpg_buf,
            &jpg_len
        );

        // Release camera framebuffer
        esp_camera_fb_return(fb);
        fb = NULL;

        if (!converted || !jpg_buf || jpg_len == 0)
        {
            Serial.println("JPEG conversion failed");

            if (jpg_buf) {
                free(jpg_buf);
                jpg_buf = NULL;
            }

            res = ESP_FAIL;
            break;
        }

        // Send frame boundary
        res = httpd_resp_send_chunk(
            req,
            STREAM_BOUNDARY,
            strlen(STREAM_BOUNDARY)
        );

        if (res != ESP_OK) {
            free(jpg_buf);
            jpg_buf = NULL;
            break;
        }

        // Prepare JPEG HTTP header
        size_t hlen = snprintf(
            part_buf,
            sizeof(part_buf),
            STREAM_PART,
            (unsigned int)jpg_len
        );

        // Send JPEG header
        res = httpd_resp_send_chunk(
            req,
            part_buf,
            hlen
        );

        if (res != ESP_OK) {
            free(jpg_buf);
            jpg_buf = NULL;
            break;
        }

        // Send JPEG image
        res = httpd_resp_send_chunk(
            req,
            (const char*)jpg_buf,
            jpg_len
        );

        // Release JPEG memory
        free(jpg_buf);
        jpg_buf = NULL;

        if (res != ESP_OK) {
            break;
        }

        // Yield to other ESP32 tasks
        vTaskDelay(1);
    }

    if (fb) {
        esp_camera_fb_return(fb);
    }

    if (jpg_buf) {
        free(jpg_buf);
    }

    Serial.println("Stream client disconnected");

    return res;
}

// =====================================================
// WEBPAGE HANDLER
// =====================================================

static esp_err_t index_handler(httpd_req_t *req)
{
    httpd_resp_set_type(req, "text/html");

    return httpd_resp_send(
        req,
        INDEX_HTML,
        HTTPD_RESP_USE_STRLEN
    );
}

// =====================================================
// HTTP SERVER
// =====================================================

void startCameraServer()
{
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();

    config.server_port = 80;
    config.ctrl_port = 32768;
    config.max_uri_handlers = 8;
    config.stack_size = 8192;

    httpd_uri_t index_uri = {
        .uri = "/",
        .method = HTTP_GET,
        .handler = index_handler,
        .user_ctx = NULL
    };

    httpd_uri_t stream_uri = {
        .uri = "/stream",
        .method = HTTP_GET,
        .handler = stream_handler,
        .user_ctx = NULL
    };

    if (httpd_start(&camera_httpd, &config) == ESP_OK)
    {
        httpd_register_uri_handler(
            camera_httpd,
            &index_uri
        );

        httpd_register_uri_handler(
            camera_httpd,
            &stream_uri
        );

        Serial.println("HTTP camera server started");
    }
    else
    {
        Serial.println("HTTP server failed to start");
    }
}

// =====================================================
// CAMERA INITIALIZATION
// =====================================================

void setupCamera()
{
    camera_config_t config = {};

    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;

    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;

    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;

    config.pin_sccb_sda = SIOD_GPIO_NUM;
    config.pin_sccb_scl = SIOC_GPIO_NUM;

    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;

    config.xclk_freq_hz = 10000000;

    // Capture RGB565, not JPEG
    config.pixel_format = PIXFORMAT_RGB565;
    config.frame_size = FRAME_SIZE;

    config.jpeg_quality = 12;

    config.fb_count = 1;

    if (psramFound())
    {
        Serial.println("PSRAM detected");

        config.fb_location = CAMERA_FB_IN_PSRAM;
        config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    }
    else
    {
        Serial.println("WARNING: PSRAM not detected");

        config.fb_location = CAMERA_FB_IN_DRAM;
        config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    }

    Serial.println("Initializing RHYX M21-45...");

    esp_err_t err = esp_camera_init(&config);

    if (err != ESP_OK)
    {
        Serial.printf(
            "Camera initialization failed: 0x%x\n",
            err
        );

        while (true) {
            delay(1000);
        }
    }

    Serial.println("Camera initialized successfully");
}

// =====================================================
// WIFI ACCESS POINT INITIALIZATION
// =====================================================

void startAccessPoint()
{
    Serial.println();
    Serial.println("Starting ESP32 WiFi hotspot...");

    WiFi.mode(WIFI_AP);

    bool started = WiFi.softAP(
        AP_SSID,
        AP_PASSWORD
    );

    if (!started)
    {
        Serial.println("Hotspot startup failed!");

        while (true) {
            delay(1000);
        }
    }

    Serial.println();
    Serial.println("================================");
    Serial.println("       WIFI HOTSPOT READY");
    Serial.println("================================");

    Serial.print("Network: ");
    Serial.println(AP_SSID);

    Serial.print("Password: ");
    Serial.println(AP_PASSWORD);

    Serial.print("IP Address: ");
    Serial.println(WiFi.softAPIP());

    Serial.println("================================");
}

// =====================================================
// SETUP
// =====================================================

void setup()
{
    Serial.begin(115200);

    delay(1000);

    Serial.println();
    Serial.println("==============================");
    Serial.println(" ESP32 RHYX M21-45 CAMERA");
    Serial.println("==============================");

    // Initialize camera
    setupCamera();

    // Create ESP32 WiFi hotspot
    startAccessPoint();

    // Start HTTP server
    startCameraServer();

    Serial.println();
    Serial.println("CAMERA READY!");

    Serial.println("Web interface:");
    Serial.println("http://192.168.4.1/");

    Serial.println("Direct video stream:");
    Serial.println("http://192.168.4.1/stream");

    Serial.println("==============================");
}

// =====================================================
// LOOP
// =====================================================

void loop()
{
    delay(1000);
}

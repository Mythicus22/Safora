#pragma once

#include <Arduino.h>
#include "esp_camera.h"
#include "img_converters.h"
#include "Config.h"

class CameraManager {
public:
    CameraManager() : initialized(false) {}

    bool init() {
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

        // Verified working clock frequency for RHYX M21-45
        config.xclk_freq_hz = CAMERA_XCLK_FREQ_HZ;

        // Verified working format for RHYX M21-45: RGB565 at QVGA
        config.pixel_format = PIXFORMAT_RGB565;
        config.frame_size = CAMERA_FRAME_SIZE;
        config.jpeg_quality = 12;
        config.fb_count = 1;

        if (psramFound()) {
            Serial.println("[Camera] PSRAM detected. Using PSRAM for frame buffer.");
            config.fb_location = CAMERA_FB_IN_PSRAM;
            config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
        } else {
            Serial.println("[Camera] WARNING: PSRAM not detected. Using DRAM.");
            config.fb_location = CAMERA_FB_IN_DRAM;
            config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
        }

        Serial.println("[Camera] Initializing RHYX M21-45 camera driver...");
        esp_err_t err = esp_camera_init(&config);
        if (err != ESP_OK) {
            Serial.printf("[Camera] esp_camera_init failed: 0x%x\n", err);
            initialized = false;
            return false;
        }

        initialized = true;
        Serial.println("[Camera] RHYX M21-45 camera initialized successfully!");
        return true;
    }

    /**
     * Captures a frame from RHYX M21-45 (RGB565) and converts it to JPEG
     * using the exact working method `frame2jpg()` from your tested code!
     * Caller MUST release the converted jpeg using free(outJpegBuf) when done.
     */
    bool captureJpegFrame(uint8_t **outJpegBuf, size_t *outJpegLen) {
        if (!initialized) return false;

        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            Serial.println("[Camera] Frame capture failed");
            return false;
        }

        // Use tested frame2jpg from img_converters.h
        uint8_t *jpg_buf = NULL;
        size_t jpg_len = 0;

        bool converted = frame2jpg(
            fb,
            JPEG_QUALITY_VAL,
            &jpg_buf,
            &jpg_len
        );

        // Immediately return raw RGB565 framebuffer to camera driver
        esp_camera_fb_return(fb);

        if (!converted || !jpg_buf || jpg_len == 0) {
            Serial.println("[Camera] frame2jpg conversion failed");
            if (jpg_buf) free(jpg_buf);
            return false;
        }

        *outJpegBuf = jpg_buf;
        *outJpegLen = jpg_len;
        return true;
    }

    void releaseJpeg(uint8_t *jpegBuf) {
        if (jpegBuf) {
            free(jpegBuf);
        }
    }

    bool isReady() const { return initialized; }

private:
    bool initialized;
};

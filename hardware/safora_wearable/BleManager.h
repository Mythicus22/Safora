#pragma once

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "Config.h"

// Forward callback declaration for commands received from Android
typedef void (*BleCommandCallback)(const String &cmd);

class BleManager : public BLEServerCallbacks, public BLECharacteristicCallbacks {
public:
    BleManager() :
        server(nullptr),
        commandChar(nullptr),
        statusChar(nullptr),
        eventsChar(nullptr),
        deviceConnected(false),
        cmdCallback(nullptr) {}

    void init(BleCommandCallback callback) {
        cmdCallback = callback;

        Serial.println("[BLE] Initializing BLE GATT Server...");
        BLEDevice::init(BLE_DEVICE_NAME);

        server = BLEDevice::createServer();
        server->setCallbacks(this);

        BLEService *service = server->createService(BLE_SERVICE_UUID);

        // 1. Command Characteristic (Write from Phone)
        commandChar = service->createCharacteristic(
            BLE_CHAR_COMMAND_UUID,
            BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
        );
        commandChar->setCallbacks(this);

        // 2. Status Characteristic (Read / Notify)
        statusChar = service->createCharacteristic(
            BLE_CHAR_STATUS_UUID,
            BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
        );
        statusChar->addDescriptor(new BLE2902());

        // 3. Events Characteristic (Notify to Phone with Descriptor 0x2902)
        eventsChar = service->createCharacteristic(
            BLE_CHAR_EVENTS_UUID,
            BLECharacteristic::PROPERTY_NOTIFY
        );
        eventsChar->addDescriptor(new BLE2902());

        service->start();

        // Advertising
        BLEAdvertising *advertising = BLEDevice::getAdvertising();
        advertising->addServiceUUID(BLE_SERVICE_UUID);
        advertising->setScanResponse(true);
        advertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
        advertising->setMinPreferred(0x12);
        BLEDevice::startAdvertising();

        Serial.println("[BLE] Advertising started as " BLE_DEVICE_NAME);
    }

    void onConnect(BLEServer* pServer) override {
        deviceConnected = true;
        Serial.println("[BLE] Phone client connected!");
    }

    void onDisconnect(BLEServer* pServer) override {
        deviceConnected = false;
        Serial.println("[BLE] Phone client disconnected. Restarting advertising...");
        BLEDevice::startAdvertising();
    }

    void onWrite(BLECharacteristic *pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            String cmd = String(value.c_str());
            cmd.trim();
            Serial.printf("[BLE] Command received: %s\n", cmd.c_str());
            if (cmdCallback) {
                cmdCallback(cmd);
            }
        }
    }

    void notifyEvent(const String &event) {
        if (eventsChar && deviceConnected) {
            eventsChar->setValue(event.c_str());
            eventsChar->notify();
            Serial.printf("[BLE] Notified Event: %s\n", event.c_str());
        }
    }

    void updateStatus(int batteryPct, bool isRecording) {
        if (statusChar && deviceConnected) {
            char buf[64];
            snprintf(buf, sizeof(buf), "DEVICE_STATUS:BATTERY=%d;REC=%d", batteryPct, isRecording ? 1 : 0);
            statusChar->setValue(buf);
            statusChar->notify();
        }
    }

    bool isConnected() const { return deviceConnected; }

private:
    BLEServer *server;
    BLECharacteristic *commandChar;
    BLECharacteristic *statusChar;
    BLECharacteristic *eventsChar;
    bool deviceConnected;
    BleCommandCallback cmdCallback;
};

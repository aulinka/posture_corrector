#include <Arduino.h>
#include "FastIMU.h"
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// Define pins on which I2C devices are connected.
// Devkit had other pins used than final board.
#ifdef DEVKIT
#define I2C_SDA 23
#define I2C_SCL 19
#else
#define I2C_SDA 21
#define I2C_SCL 22
#endif

// Define IMU I2C address.
// Devkit had another address used than final board.
#ifdef DEVKIT
#define IMU_ADDRESS 0x68
#else
#define IMU_ADDRESS 0x69
#endif

// Define external component pins
#define SWITCH_PIN 16
#define MOTOR_PIN 4

// Define to perform IMU calibration
// #define PERFORM_CALIBRATION

// Bluetooth Low Energy variables and UUID macros
BLEServer *pServer;
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

MPU6500 IMU;
BLECharacteristic *pCharacteristic;

calData calib = { 0 };  //Calibration data
AccelData accelData;    //Sensor data
GyroData gyroData;

float alpha = 0.5;  // Change this value to adjust the filter (0 <= alpha <= 1)
float dt = 0.01;    // (seconds) Time interval between reads (in this case, 10ms)

float pitch = 0;    // Pitch angle

bool deviceConnected = false;
bool oldDeviceConnected = false;

// Variables for storing posture states
bool last_posture_state = true; // true -> stretched, false = hunched
uint32_t new_posture_state_timestamp = 0; // timestamp which stores timestamp when posture changed
uint16_t new_posture_minimum_duration = 5000; // minimum time of new posture for trigger event
float stretched_angle_treshold = 66.0; // threshold after where person is in stretch posture

// Server Callbacks class, for detection if host connects / disconnects.
class MyServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
  }
};

void setup() {
  // Disable brownout detector
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  
  // Initialization of I2C with clock frequency 400 kHz
  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setClock(400000);

  // Initialization of serial for debug information
  Serial.begin(115200);
  Serial.println("Posture Corrector starts.");

  // Initialization of IMU
  int err = IMU.init(calib, IMU_ADDRESS);
  if (err != 0) {
    Serial.print("Error initializing IMU: ");
    Serial.println(err);
    while (true) {
      ;
    }
  }
 
  // Initialization of pins
  pinMode(SWITCH_PIN, INPUT_PULLUP);
  pinMode(MOTOR_PIN, OUTPUT);

  // Short vibration sequence on start of the device
  for (int i = 0; i < 4; i++) {
    digitalWrite(MOTOR_PIN, HIGH);
    delay(100);
    digitalWrite(MOTOR_PIN, LOW);
    delay(100);
  }

  // Optional calibration sequence, taken from FastIMU example
#ifdef PERFORM_CALIBRATION
  Serial.println("FastIMU calibration");
  delay(5000);
  Serial.println("Keep IMU level.");
  delay(5000);
  IMU.calibrateAccelGyro(&calib);
  Serial.println("Calibration done!");
  Serial.println("Accel biases X/Y/Z: ");
  Serial.print(calib.accelBias[0]);
  Serial.print(", ");
  Serial.print(calib.accelBias[1]);
  Serial.print(", ");
  Serial.println(calib.accelBias[2]);
  Serial.println("Gyro biases X/Y/Z: ");
  Serial.print(calib.gyroBias[0]);
  Serial.print(", ");
  Serial.print(calib.gyroBias[1]);
  Serial.print(", ");
  Serial.println(calib.gyroBias[2]);
  delay(5000);
  IMU.init(calib, IMU_ADDRESS);

  err = IMU.setGyroRange(500);      //USE THESE TO SET THE RANGE, IF AN INVALID RANGE IS SET IT WILL RETURN -1
  err = IMU.setAccelRange(2);       //THESE TWO SET THE GYRO RANGE TO ±500 DPS AND THE ACCELEROMETER RANGE TO ±2g
#endif

  // Initialization of Bluetooth Low Energy device and GATT
  BLEDevice::init("Posture Corrector");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
                                        CHARACTERISTIC_UUID,
                                        BLECharacteristic::PROPERTY_READ |
                                        BLECharacteristic::PROPERTY_WRITE |
                                        BLECharacteristic::PROPERTY_NOTIFY
                                      );
  pCharacteristic->addDescriptor(new BLE2902());

  pService->start();
  BLEAdvertising *pAdvertising = pServer->getAdvertising(); 
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);

  // Starts advertising
  pServer->startAdvertising();
  Serial.println("Advertising...");
}

void loop() {
  // Handle Bluetooth host connection / disconnection
  if (!deviceConnected && oldDeviceConnected) { // Host disconnected
    delay(500); // Give the bluetooth stack the chance to get things ready
    pServer->startAdvertising(); // Start advertising
    Serial.println("Advertising...");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) { // Host connected
    oldDeviceConnected = deviceConnected;
    // Send current posture to the device
    pCharacteristic->setValue(last_posture_state ? "ok" : "bad");
    pCharacteristic->notify();
  }

  // Update IMU readings and load latest data
  IMU.update();
  IMU.getAccel(&accelData);
  IMU.getGyro(&gyroData);

  // Calculate pitch with complementary filter
  pitch = alpha * (pitch + gyroData.gyroX * dt) + (1 - alpha) * (atan2(accelData.accelY, sqrt(accelData.accelX * accelData.accelX + accelData.accelZ * accelData.accelZ)) * 180.0 / PI);

  // Perform detection of posture
  if (pitch > stretched_angle_treshold) {
    // Person is stretched
    if (last_posture_state == false) { // But before it was not stretched
      if (new_posture_state_timestamp == 0) {
        // This is first reading we detect stretch from hunched state, we save timestamp
        new_posture_state_timestamp = millis();
      } else if (millis() - new_posture_state_timestamp >= new_posture_minimum_duration) {
        // If person was stretched for minimum duration, we send information to phone
        last_posture_state = true;
        new_posture_state_timestamp = 0;
        Serial.println("Stretched!");
        pCharacteristic->setValue("ok");
        pCharacteristic->notify();
      }
    } else if (new_posture_state_timestamp != 0) {
      // we are still stretched, so reset new posture timestamp
      new_posture_state_timestamp = 0;
    }
  } else {
    // Person is hunched
    if (last_posture_state == true) { // But before it was stretched before
      if (new_posture_state_timestamp == 0) {
        // This is first reading we detect hunched from stretch state, we save timestamp
        new_posture_state_timestamp = millis();
      } else if (millis() - new_posture_state_timestamp >= new_posture_minimum_duration) {
        // If person was stretched for minimum duration, we send information to phone and vibrate
        last_posture_state = false;
        new_posture_state_timestamp = 0;
        Serial.println("Hunched!");
        pCharacteristic->setValue("bad");
        pCharacteristic->notify();
        // We will vibrate two times for short period of time
        for (int i = 0; i < 2; i++) {
          digitalWrite(MOTOR_PIN, HIGH);
          delay(100);
          digitalWrite(MOTOR_PIN, LOW);
          delay(100);
        }
      }
    } else if (new_posture_state_timestamp != 0) {
      // we are still hunched, so reset new posture timestamp
      new_posture_state_timestamp = 0;
    }
  }
  // Wait for 10ms
  delay(10);
}
